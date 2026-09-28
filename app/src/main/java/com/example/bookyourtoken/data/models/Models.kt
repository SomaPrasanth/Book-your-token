package com.example.bookyourtoken.data.models

/** A food item as parsed from the booking page, restricted to what is offered tomorrow. */
data class TokenItem(
    val name: String,
    val buttonId: String,
    val ptokenId: Int,
    val maxQty: Int,
    val price: String?,
    val dates: List<String>,
    val meals: List<String>,
    val defaultMeal: String
)

/** A single row returned by StudentGetToken — a token the student already holds. */
data class BookedToken(
    val messId: String?,
    val tokenName: String?,
    val tokenId: Int?,
    val issueDate: String?,
    val expireDate: String?,
    val tokenNo: String?,
    val tokenQty: Int?,
    val status: String?,
    val viewStatus: String?,
    val mealTime: String?,
    val count: Int?
)

/** Outcome of a newStudentTokenApply call. Only oresult == 1 means the token was actually booked. */
data class BookResult(
    val success: Boolean,
    val oresult: Int?,
    val count: Int?,
    val message: String
)

/** Generic wrapper for network calls against the hostel portal. */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val message: String, val isTimeout: Boolean = false) : ApiResult<Nothing>()
}

/** Maps the site's own oresult codes to the exact messages used by its JS. */
fun messageForResult(oresult: Int?, count: Int?): String = when (oresult) {
    1 -> "Token Booked"
    2 -> "Non-vegetarian food will not be provided by tomorrow."
    3 -> "Token apply time has expired"
    4 -> "Invalid input"
    5 -> "Please contact hostel office."
    6 -> "The remaining balance is required to be paid."
    7 -> "Food not available for the selected time"
    8 -> "Please make sure to only apply for " + (count?.toString() ?: "")
    9 -> "The token limit has been reached."
    else -> "Error occurred"
}
