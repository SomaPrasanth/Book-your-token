package com.example.bookyourtoken.data

import com.example.bookyourtoken.data.models.ApiResult
import com.example.bookyourtoken.data.models.BookResult
import com.example.bookyourtoken.data.models.BookedToken
import com.example.bookyourtoken.data.models.CancelResult
import com.example.bookyourtoken.data.models.cancelMessage
import com.example.bookyourtoken.data.models.messageForResult
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Per platform: OkHttp on Android, NSURLSession on iOS — neither may keep cookies of its own. */
internal expect fun createPlatformHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient

private val json = Json { isLenient = true }

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

private fun JsonObject.intOrNull(key: String): Int? =
    stringOrNull(key)?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }

private fun Throwable.isTimeout(): Boolean =
    this is HttpRequestTimeoutException || this is ConnectTimeoutException || this is SocketTimeoutException

/**
 * Thin Ktor wrapper around the hostel portal. Every instance is a single session with its own
 * in-memory cookie jar: create a new HostelClient for each logical operation (and close it) rather
 * than reusing one — the portal's login expires after 10 minutes.
 */
class HostelClient : AutoCloseable {

    private val client = createPlatformHttpClient {
        expectSuccess = false
        install(HttpCookies) { storage = AcceptAllCookiesStorage() }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 20_000
            requestTimeoutMillis = 45_000
        }
    }

    override fun close() = client.close()

    private fun HttpRequestBuilder.baseHeaders() {
        header(HttpHeaders.UserAgent, USER_AGENT)
    }

    private fun HttpRequestBuilder.xhrHeaders(referer: String = "$BASE/Hostel") {
        baseHeaders()
        header("X-Requested-With", "XMLHttpRequest")
        header(HttpHeaders.Origin, BASE)
        header(HttpHeaders.Referrer, referer)
    }

    /**
     * Maps exceptions to the user-facing messages. [unknownOutcome] marks every dropped request as
     * "may have gone through" — used for booking and cancelling, where that must be verified.
     */
    private suspend fun <T> guarded(
        timeout: String,
        failed: String,
        notUnderstood: String? = null,
        unknownOutcome: Boolean = false,
        block: suspend () -> ApiResult<T>
    ): ApiResult<T> = try {
        block()
    } catch (e: Throwable) {
        when {
            e.isTimeout() -> ApiResult.Failure(timeout, isTimeout = true)
            e is CancellationException -> throw e
            // SerializationException is an IllegalArgumentException, as is "not a JSON object".
            e is IllegalArgumentException && notUnderstood != null -> ApiResult.Failure(notUnderstood)
            e is Exception -> ApiResult.Failure("$failed: ${e.message}", isTimeout = unknownOutcome)
            else -> throw e
        }
    }

    private suspend inline fun <T> HttpResponse.ifSuccessful(
        failure: String,
        block: (String) -> ApiResult<T>
    ): ApiResult<T> =
        if (!status.isSuccess()) ApiResult.Failure("$failure (HTTP ${status.value}).") else block(bodyAsText())

    suspend fun primeSession(): ApiResult<Unit> =
        guarded(timeout = "Connection to the portal timed out.", failed = "Couldn't reach the portal") {
            client.get("$BASE/Hostel") { baseHeaders() }
            ApiResult.Success(Unit)
        }

    /** Primes the session then authenticates. The roll number is always uppercased before sending. */
    suspend fun login(rollNo: String, password: String): ApiResult<String?> {
        val prime = primeSession()
        if (prime is ApiResult.Failure) return prime

        return guarded(
            timeout = "Login timed out.",
            failed = "Login failed",
            notUnderstood = "Login response was not understood."
        ) {
            val response = client.submitForm(
                url = "$BASE/Hostel/Login/Authenticate",
                formParameters = parameters {
                    append("name", rollNo.uppercase())
                    append("password", password)
                }
            ) { xhrHeaders() }
            response.ifSuccessful("Login failed") { body ->
                val token = json.parseToJsonElement(body).jsonObject.stringOrNull("Token")
                if (token.isNullOrEmpty()) {
                    ApiResult.Failure("Login was rejected — check your roll number and password.")
                } else {
                    ApiResult.Success(token)
                }
            }
        }
    }

    suspend fun fetchBookingPageHtml(): ApiResult<String> =
        guarded(timeout = "Loading the booking page timed out.", failed = "Couldn't load the booking page") {
            client.get(BOOKING_PAGE_URL) { baseHeaders() }
                .ifSuccessful("Couldn't load the booking page") { ApiResult.Success(it) }
        }

    suspend fun fetchBookedTokens(rollNo: String): ApiResult<List<BookedToken>> = guarded(
        timeout = "Loading booked tokens timed out.",
        failed = "Couldn't load booked tokens",
        notUnderstood = "Booked tokens response was not understood."
    ) {
        val response = client.submitForm(
            url = "$BASE/Hostel/Student/StudentGetToken",
            formParameters = parameters { append("rollno", rollNo.uppercase()) }
        ) { xhrHeaders() }
        response.ifSuccessful("Couldn't load booked tokens") { raw ->
            val body = raw.trim()
            if (body.isEmpty()) ApiResult.Success(emptyList()) else ApiResult.Success(parseBookedTokens(body))
        }
    }

    /** date must be exactly the dd-MM-yyyy value from the item's date dropdown — never reformatted. */
    suspend fun bookToken(ptokenId: Int, qty: Int, date: String, mealTime: String): ApiResult<BookResult> = guarded(
        timeout = "Booking request timed out.",
        failed = "Booking request failed",
        notUnderstood = "Booking response was not understood.",
        unknownOutcome = true
    ) {
        val response = client.submitForm(
            url = "$BASE/Hostel/Student/newStudentTokenApply",
            formParameters = parameters {
                append("PTOKEN_ID", ptokenId.toString())
                append("ddtokenqty", qty.toString())
                append("Tokendatetime", date)
                append("MEALTIME", mealTime)
            }
        ) { xhrHeaders(referer = BOOKING_PAGE_URL) }
        response.ifSuccessful("Booking request failed") { body ->
            val obj = firstResultObject(body)
            val oresult = obj.intOrNull("oresult")
            val count = obj.intOrNull("Count")
            ApiResult.Success(BookResult(oresult == 1, oresult, count, messageForResult(oresult, count)))
        }
    }

    /**
     * Cancels ONE unit of a token (verified live: quantity 2 → 1). The token MUST be a
     * StudentGetToken row — never build one from the booking page.
     */
    suspend fun cancelToken(rollNo: String, token: BookedToken): ApiResult<CancelResult> =
        cancel("StudentTokenCancel", rollNo, token, bulk = false)

    /**
     * The site's "Cancel All". Same payload as [cancelToken]. Verified to remove a quantity-1 token;
     * whether it clears a larger quantity in one go is still unverified.
     */
    suspend fun cancelAllOfToken(rollNo: String, token: BookedToken): ApiResult<CancelResult> =
        cancel("StudentTokenBulkCancel", rollNo, token, bulk = true)

    private suspend fun cancel(
        endpoint: String,
        rollNo: String,
        token: BookedToken,
        bulk: Boolean
    ): ApiResult<CancelResult> {
        val fields = cancelFormFields(rollNo, token)
            ?: return ApiResult.Failure("This token is missing details needed to cancel it.")
        return guarded(
            timeout = "Cancel request timed out.",
            failed = "Cancel request failed",
            notUnderstood = "Cancel response was not understood.",
            unknownOutcome = true
        ) {
            val response = client.submitForm(
                url = "$BASE/Hostel/Student/$endpoint",
                formParameters = parameters { fields.forEach { (name, value) -> append(name, value) } }
            ) { xhrHeaders(referer = BOOKING_PAGE_URL) }
            response.ifSuccessful("Cancel request failed") { body ->
                val oresult = firstResultObject(body).intOrNull("oresult")
                ApiResult.Success(CancelResult(oresult == 0, oresult, cancelMessage(oresult, bulk)))
            }
        }
    }

    companion object {
        const val BASE = "https://edviewx.psgtech.ac.in"
        const val BOOKING_PAGE_URL = "$BASE/Hostel/Student/StudentView"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36"

        /**
         * The site's JS reads these from its booked-tokens table by column position, so the names
         * don't match the contents: ISSUE_DATE carries the token NAME, TOKEN_ID carries the DATE,
         * and Tokenno is the QR cell's text, always "View". Captured from the live site — don't
         * "fix" them. TOKEN_NAME is passed through verbatim (no case changes).
         */
        internal fun cancelFormFields(rollNo: String, token: BookedToken): List<Pair<String, String>>? {
            val name = token.tokenName ?: return null
            val date = token.expireDate ?: return null
            val meal = token.mealTime ?: return null
            return listOf(
                "rollno" to rollNo.uppercase(),
                "Tokenno" to "View",
                "ISSUE_DATE" to name,
                "TOKEN_ID" to date,
                "MEALTIME" to meal
            )
        }

        /** StudentGetToken rows. Throws IllegalArgumentException if the body isn't a JSON array. */
        internal fun parseBookedTokens(body: String): List<BookedToken> =
            json.parseToJsonElement(body).jsonArray.mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                BookedToken(
                    messId = o.stringOrNull("MESS_ID"),
                    tokenName = o.stringOrNull("TOKEN_NAME"),
                    tokenId = o.stringOrNull("TOKEN_ID")?.toIntOrNull(),
                    issueDate = o.stringOrNull("IssueDate"),
                    expireDate = o.stringOrNull("ExpireDate"),
                    tokenNo = o.stringOrNull("TOKEN_NO"),
                    tokenQty = o.intOrNull("TOKEN_QTY"),
                    status = o.stringOrNull("Status"),
                    viewStatus = o.stringOrNull("ViewStatus"),
                    mealTime = o.stringOrNull("MEALTIME"),
                    count = o.intOrNull("COUNT")
                )
            }

        /** Responses are usually an object but sometimes a one-element array; take the object either way. */
        internal fun firstResultObject(rawBody: String): JsonObject {
            val body = rawBody.trim()
            return when {
                body.startsWith("[") -> json.parseToJsonElement(body).jsonArray.firstOrNull() as? JsonObject
                body.startsWith("{") -> json.parseToJsonElement(body).jsonObject
                else -> null
            } ?: JsonObject(emptyMap())
        }

        /** The oresult of a booking/cancel response, or null if the portal didn't send one. */
        internal fun resultCode(rawBody: String): Int? = firstResultObject(rawBody).intOrNull("oresult")
    }
}
