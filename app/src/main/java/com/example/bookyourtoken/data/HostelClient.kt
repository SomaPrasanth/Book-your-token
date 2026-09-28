package com.example.bookyourtoken.data

import com.example.bookyourtoken.data.models.ApiResult
import com.example.bookyourtoken.data.models.BookResult
import com.example.bookyourtoken.data.models.BookedToken
import com.example.bookyourtoken.data.models.messageForResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

/** An in-memory cookie jar. One instance == one session; a fresh HostelClient means a fresh session. */
private class SimpleCookieJar : CookieJar {
    private val store = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val list = store.getOrPut(url.host) { mutableListOf() }
        for (cookie in cookies) {
            list.removeAll { it.name == cookie.name }
            list.add(cookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host].orEmpty()
}

/**
 * Thin OkHttp wrapper around the hostel portal. Every instance is a single session: create a new
 * HostelClient for each logical operation rather than reusing one across app launches.
 */
class HostelClient {

    private val cookieJar = SimpleCookieJar()
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Runs the call AND consumes/closes the response entirely on Dispatchers.IO. Closing an
     * unconsumed OkHttp response body drains the socket synchronously, which is still network I/O —
     * doing that after returning to the caller's (Main) dispatcher throws NetworkOnMainThreadException.
     */
    private suspend fun <T> withResponse(request: Request, block: (Response) -> T): T =
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use(block)
        }

    private fun baseHeaders(builder: Request.Builder): Request.Builder =
        builder.header("User-Agent", USER_AGENT)

    private fun xhrHeaders(builder: Request.Builder, referer: String = "$BASE/Hostel"): Request.Builder =
        baseHeaders(builder)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Origin", BASE)
            .header("Referer", referer)

    suspend fun primeSession(): ApiResult<Unit> = try {
        val request = baseHeaders(Request.Builder().url("$BASE/Hostel")).get().build()
        withResponse(request) { }
        ApiResult.Success(Unit)
    } catch (e: SocketTimeoutException) {
        ApiResult.Failure("Connection to the portal timed out.", isTimeout = true)
    } catch (e: IOException) {
        ApiResult.Failure("Couldn't reach the portal: ${e.message}")
    }

    /** Primes the session then authenticates. The roll number is always uppercased before sending. */
    suspend fun login(rollNo: String, password: String): ApiResult<String?> {
        val prime = primeSession()
        if (prime is ApiResult.Failure) return prime

        return try {
            val formBody = FormBody.Builder()
                .add("name", rollNo.uppercase())
                .add("password", password)
                .build()
            val request = xhrHeaders(Request.Builder().url("$BASE/Hostel/Login/Authenticate"))
                .post(formBody)
                .build()
            withResponse(request) { response ->
                if (!response.isSuccessful) {
                    ApiResult.Failure("Login failed (HTTP ${response.code}).")
                } else {
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val token = json.optStringOrNull("Token")
                    if (token.isNullOrEmpty()) {
                        ApiResult.Failure("Login was rejected — check your roll number and password.")
                    } else {
                        ApiResult.Success(token)
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            ApiResult.Failure("Login timed out.", isTimeout = true)
        } catch (e: IOException) {
            ApiResult.Failure("Login failed: ${e.message}")
        } catch (e: JSONException) {
            ApiResult.Failure("Login response was not understood.")
        }
    }

    suspend fun fetchBookingPageHtml(): ApiResult<String> = try {
        val request = baseHeaders(Request.Builder().url("$BASE/Hostel/Student/StudentView")).get().build()
        withResponse(request) { response ->
            if (!response.isSuccessful) {
                ApiResult.Failure("Couldn't load the booking page (HTTP ${response.code}).")
            } else {
                ApiResult.Success(response.body?.string().orEmpty())
            }
        }
    } catch (e: SocketTimeoutException) {
        ApiResult.Failure("Loading the booking page timed out.", isTimeout = true)
    } catch (e: IOException) {
        ApiResult.Failure("Couldn't load the booking page: ${e.message}")
    }

    suspend fun fetchBookedTokens(rollNo: String): ApiResult<List<BookedToken>> = try {
        val formBody = FormBody.Builder().add("rollno", rollNo.uppercase()).build()
        val request = xhrHeaders(Request.Builder().url("$BASE/Hostel/Student/StudentGetToken"))
            .post(formBody)
            .build()
        withResponse(request) { response ->
            if (!response.isSuccessful) {
                ApiResult.Failure("Couldn't load booked tokens (HTTP ${response.code}).")
            } else {
                val body = response.body?.string().orEmpty().trim()
                if (body.isEmpty()) {
                    ApiResult.Success(emptyList())
                } else {
                    val array = JSONArray(body)
                    val result = mutableListOf<BookedToken>()
                    for (i in 0 until array.length()) {
                        val o = array.optJSONObject(i) ?: continue
                        result.add(
                            BookedToken(
                                messId = o.optStringOrNull("MESS_ID"),
                                tokenName = o.optStringOrNull("TOKEN_NAME"),
                                tokenId = o.optStringOrNull("TOKEN_ID")?.toIntOrNull(),
                                issueDate = o.optStringOrNull("IssueDate"),
                                expireDate = o.optStringOrNull("ExpireDate"),
                                tokenNo = o.optStringOrNull("TOKEN_NO"),
                                tokenQty = o.optIntOrNull("TOKEN_QTY"),
                                status = o.optStringOrNull("Status"),
                                viewStatus = o.optStringOrNull("ViewStatus"),
                                mealTime = o.optStringOrNull("MEALTIME"),
                                count = o.optIntOrNull("COUNT")
                            )
                        )
                    }
                    ApiResult.Success(result)
                }
            }
        }
    } catch (e: SocketTimeoutException) {
        ApiResult.Failure("Loading booked tokens timed out.", isTimeout = true)
    } catch (e: IOException) {
        ApiResult.Failure("Couldn't load booked tokens: ${e.message}")
    } catch (e: JSONException) {
        ApiResult.Failure("Booked tokens response was not understood.")
    }

    /** date must be exactly the dd-MM-yyyy value from the item's date dropdown — never reformatted. */
    suspend fun bookToken(ptokenId: Int, qty: Int, date: String, mealTime: String): ApiResult<BookResult> = try {
        val formBody = FormBody.Builder()
            .add("PTOKEN_ID", ptokenId.toString())
            .add("ddtokenqty", qty.toString())
            .add("Tokendatetime", date)
            .add("MEALTIME", mealTime)
            .build()
        val request = xhrHeaders(
            Request.Builder().url("$BASE/Hostel/Student/newStudentTokenApply"),
            referer = BOOKING_PAGE_URL
        )
            .post(formBody)
            .build()
        withResponse(request) { response ->
            if (!response.isSuccessful) {
                ApiResult.Failure("Booking request failed (HTTP ${response.code}).")
            } else {
                val body = response.body?.string().orEmpty().trim()
                val obj: JSONObject = when {
                    body.startsWith("[") -> {
                        val array = JSONArray(body)
                        if (array.length() == 0) JSONObject() else (array.optJSONObject(0) ?: JSONObject())
                    }
                    body.startsWith("{") -> JSONObject(body)
                    else -> JSONObject()
                }
                val oresult = obj.optIntOrNull("oresult")
                val count = obj.optIntOrNull("Count")
                ApiResult.Success(BookResult(oresult == 1, oresult, count, messageForResult(oresult, count)))
            }
        }
    } catch (e: SocketTimeoutException) {
        ApiResult.Failure("Booking request timed out.", isTimeout = true)
    } catch (e: IOException) {
        ApiResult.Failure("Booking request failed: ${e.message}")
    } catch (e: JSONException) {
        ApiResult.Failure("Booking response was not understood.")
    }

    companion object {
        const val BASE = "https://edviewx.psgtech.ac.in"
        const val BOOKING_PAGE_URL = "$BASE/Hostel/Student/StudentView"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36"
    }
}
