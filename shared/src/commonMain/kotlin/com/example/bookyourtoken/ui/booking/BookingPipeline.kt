package com.example.bookyourtoken.ui.booking

import com.example.bookyourtoken.data.CredentialStore
import com.example.bookyourtoken.data.HostelClient
import com.example.bookyourtoken.data.models.ApiResult
import com.example.bookyourtoken.data.models.BookedToken
import com.example.bookyourtoken.data.models.TokenItem

sealed interface LineStatus {
    data object Pending : LineStatus
    data object Sending : LineStatus

    /** [code] is the portal's oresult when it answered (9 = token limit, 3 = too late). */
    data class Done(val success: Boolean, val message: String, val code: Int? = null) : LineStatus
}

/** One booking request: [date] is the exact dd-MM-yyyy string from the item's date dropdown. */
data class BookingLine(
    val item: TokenItem,
    val meal: String,
    val quantity: Int,
    val date: String,
    val status: LineStatus = LineStatus.Pending
) {
    val succeeded: Boolean get() = (status as? LineStatus.Done)?.success == true
}

data class BookingUiState(
    val lines: List<BookingLine>,
    val finished: Boolean = false,
    /** Book ahead spans several dates: the progress list is grouped under date headings. */
    val groupByDate: Boolean = false
) {
    val bookedCount: Int get() = lines.count { it.succeeded }
    val failedCount: Int get() = lines.count { (it.status as? LineStatus.Done)?.success == false }

    fun anyFailedWith(code: Int): Boolean = lines.any { (it.status as? LineStatus.Done)?.code == code }
}

/**
 * The one booking pipeline behind both booking screens. Only ever called after the user confirmed
 * the exact [lines] in a dialog.
 *
 * Signs in, loads StudentView and StudentGetToken (the portal sets up session state there; skipping
 * them gets oresult 6), then sends one request per line, strictly one after another, in the given
 * order. A failed line never stops the rest. [publish] receives every status change.
 *
 * With [fetchBookedAfter], returns StudentGetToken as it stands after the last request (same session),
 * or null if that couldn't be loaded.
 */
suspend fun runBookings(
    credentials: CredentialStore,
    lines: List<BookingLine>,
    groupByDate: Boolean = false,
    fetchBookedAfter: Boolean = false,
    publish: (BookingUiState) -> Unit
): List<BookedToken>? {
    val current = lines.toMutableList()
    fun emit(finished: Boolean = false) = publish(BookingUiState(current.toList(), finished, groupByDate))

    fun failAll(message: String): List<BookedToken>? {
        for (i in current.indices) current[i] = current[i].copy(status = LineStatus.Done(false, message))
        emit(finished = true)
        return null
    }

    emit()
    if (current.isEmpty()) return null

    val rollNo = credentials.rollNo()
    val password = credentials.password()
    if (rollNo.isNullOrBlank() || password.isNullOrBlank()) return failAll("No saved credentials.")

    HostelClient().use { client ->
        val login = client.login(rollNo, password)
        if (login is ApiResult.Failure) return failAll(login.message)

        val page = client.fetchBookingPageHtml()
        if (page is ApiResult.Failure) return failAll(page.message)
        val bookedBefore = (client.fetchBookedTokens(rollNo) as? ApiResult.Success)?.data.orEmpty()

        // Sequential, one line at a time — matches how the site itself submits bookings.
        for (i in current.indices) {
            current[i] = current[i].copy(status = LineStatus.Sending)
            emit()

            val line = current[i]
            val status = when (val result = client.bookToken(line.item.ptokenId, line.quantity, line.date, line.meal)) {
                is ApiResult.Success -> LineStatus.Done(result.data.success, result.data.message, result.data.oresult)
                is ApiResult.Failure ->
                    if (result.isTimeout) verifyAfterTimeout(client, rollNo, line, bookedBefore)
                    else LineStatus.Done(false, result.message)
            }

            current[i] = current[i].copy(status = status)
            emit(finished = i == current.lastIndex)
        }

        return if (fetchBookedAfter) (client.fetchBookedTokens(rollNo) as? ApiResult.Success)?.data else null
    }
}

/**
 * Never blindly retry a booking that got no response. Compare the booked quantity for this
 * item/date/meal (TOKEN_ID, ExpireDate, MEALTIME) against the snapshot taken before sending — an
 * existing booking alone doesn't prove this request went through when topping up.
 */
private suspend fun verifyAfterTimeout(
    client: HostelClient,
    rollNo: String,
    line: BookingLine,
    bookedBefore: List<BookedToken>
): LineStatus {
    fun List<BookedToken>.quantityFor() = filter {
        it.tokenId == line.item.ptokenId && it.expireDate == line.date && it.mealTime == line.meal
    }.sumOf { it.tokenQty ?: 1 }

    val after = client.fetchBookedTokens(rollNo) as? ApiResult.Success
        ?: return LineStatus.Done(false, "No response and couldn't verify. Check the portal before retrying.")

    // A missing increase isn't proof of failure (top-ups may not show in the list), so never
    // tell the user it's safe to resend — that could double-book.
    return if (after.data.quantityFor() > bookedBefore.quantityFor()) {
        LineStatus.Done(true, "Token Booked (confirmed by re-checking)")
    } else {
        LineStatus.Done(false, "No response — couldn't confirm it went through. Check the portal before retrying.")
    }
}
