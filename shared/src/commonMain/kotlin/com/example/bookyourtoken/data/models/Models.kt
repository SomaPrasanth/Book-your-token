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
) {
    /**
     * The portal has enabled the QR for this token (ViewStatus "1"); it greys out "View QR" otherwise.
     * Always read from the API — never work out when it should be enabled.
     */
    val qrEnabled: Boolean get() = viewStatus == "1"

    /** "Y" = already issued/used; the site disables Cancel for these. */
    val isUsed: Boolean get() = status == "Y"

    /** Only "N" rows with every field the cancel request needs. Unknown statuses are not cancellable. */
    val canCancel: Boolean
        get() = status == "N" && tokenName != null && expireDate != null && mealTime != null

    /** The portal identifies a token for cancelling by name + date + meal (plus roll number). */
    fun isSameTokenAs(other: BookedToken): Boolean =
        tokenName == other.tokenName && expireDate == other.expireDate && mealTime == other.mealTime
}

/** Outcome of a newStudentTokenApply call. Only oresult == 1 means the token was actually booked. */
data class BookResult(
    val success: Boolean,
    val oresult: Int?,
    val count: Int?,
    val message: String
)

/**
 * Outcome of StudentTokenCancel / StudentTokenBulkCancel. Note the opposite convention to booking:
 * here oresult == 0 is success.
 */
data class CancelResult(
    val success: Boolean,
    val oresult: Int?,
    val message: String
)

/** Generic wrapper for network calls against the hostel portal. */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()

    /**
     * [isTimeout] means no usable response arrived (a timeout, or for booking/cancelling any dropped
     * connection) — so a booking or cancel may still have been applied and must be verified, never resent.
     */
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

/** Cancel responses, from the site's own JS. Success is 0 — never share this with the booking mapping. */
fun cancelMessage(oresult: Int?, bulk: Boolean): String = when (oresult) {
    0 -> if (bulk) "Tokens cancelled" else "Token cancelled"
    1 -> if (bulk) "No data found" else "Token already cancelled"
    2 -> "Cancel time expired"
    else -> "Error occurred"
}
