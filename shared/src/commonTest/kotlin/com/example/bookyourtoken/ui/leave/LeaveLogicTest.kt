package com.example.bookyourtoken.ui.leave

import com.example.bookyourtoken.data.LeaveFormat
import com.example.bookyourtoken.data.models.LeaveRecord
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeaveLogicTest {

    private fun record(from: String, to: String = from, status: String = "Applied") = LeaveRecord(
        fromRaw = from,
        toRaw = to,
        from = LeaveFormat.parseHistoryDateTime(from),
        to = LeaveFormat.parseHistoryDateTime(to),
        type = "Leave",
        reason = "Weekend",
        status = status
    )

    @Test
    fun `history is re-sorted newest first, ignoring the portal's text order`() {
        // The order the portal really returned.
        val portal = listOf("01-10-2026", "03-09-2026", "05-10-2026", "10-09-2026", "26-08-2026", "26-09-2026")
            .map { record("$it 12:00 AM") }
        val sorted = sortLeaveHistory(portal + record("garbled")).map { it.fromRaw.substringBefore(' ') }
        assertEquals(
            listOf("05-10-2026", "01-10-2026", "26-09-2026", "10-09-2026", "03-09-2026", "26-08-2026", "garbled"),
            sorted
        )
    }

    @Test
    fun `labels show weekday, date and time, and the year only when it differs`() {
        assertEquals("Mon 05 Oct, 6:00 PM", leaveDateTimeLabel(LocalDateTime(2026, 10, 5, 18, 0), "x", currentYear = 2026))
        assertEquals("Tue 05 Oct 2027, 12:00 AM", leaveDateTimeLabel(LocalDateTime(2027, 10, 5, 0, 0), "x", currentYear = 2026))
        assertEquals("raw text", leaveDateTimeLabel(null, "raw text", currentYear = 2026))
    }

    @Test
    fun `only Applied leaves can be cancelled`() {
        assertTrue(record("05-10-2026 06:00 PM").canCancel)
        listOf("Approved", "Rejected", "Cancelled", "").forEach { assertFalse(record("05-10-2026 06:00 PM", status = it).canCancel) }
    }

    @Test
    fun `a cancel is ambiguous when another Applied leave has the same dates`() {
        val target = record("05-10-2026 06:00 AM", "06-10-2026 06:00 PM")
        val sameDates = record("05-10-2026 09:00 AM", "06-10-2026 08:00 PM")
        val approvedSameDates = record("05-10-2026 10:00 AM", "06-10-2026 09:00 PM", status = "Approved")
        val otherDates = record("07-10-2026 06:00 AM", "08-10-2026 06:00 PM")
        val history = listOf(target, sameDates, approvedSameDates, otherDates)
        assertEquals(listOf(sameDates), ambiguousCancels(target, history))
        assertEquals(emptyList(), ambiguousCancels(otherDates, history))
    }

    @Test
    fun `cancel check tells gone, still there, and a different leave gone`() {
        val target = record("05-10-2026 06:00 AM", "06-10-2026 06:00 PM")
        val twin = record("05-10-2026 09:00 AM", "06-10-2026 08:00 PM")
        val before = listOf(target, twin)
        assertEquals(CancelCheck.Gone, checkCancel(target, before, listOf(twin)))
        assertEquals(CancelCheck.Gone, checkCancel(target, before, listOf(target.copy(status = "Cancelled"), twin)))
        assertEquals(CancelCheck.StillThere, checkCancel(target, before, before))
        assertEquals(CancelCheck.OtherGone(twin), checkCancel(target, before, listOf(target)))
    }

    @Test
    fun `apply after a timeout is confirmed by a matching from and to`() {
        val from = LocalDateTime(2026, 10, 5, 18, 0)
        val to = LocalDateTime(2026, 10, 6, 18, 0)
        val history = listOf(
            record("05-10-2026 06:00 PM", "06-10-2026 06:00 PM"),
            record("05-10-2026 06:00 PM", "06-10-2026 06:00 PM", status = "Cancelled"),
            record("05-10-2026 06:00 PM", "07-10-2026 06:00 PM")
        )
        assertEquals(1, countMatching(history, from, to))
    }

    @Test
    fun `the form needs everything filled, From not in the past, and To after From`() {
        val now = LocalDateTime(2026, 10, 4, 10, 2, 30)
        val good = LeaveForm(
            typeId = "L",
            staffId = "PS0679",
            fromDate = LocalDate(2026, 10, 4),
            fromTime = LocalTime(18, 0),
            toDate = LocalDate(2026, 10, 5),
            toTime = LocalTime(18, 0),
            reason = "  weekend "
        )
        assertNull(dateProblem(good, now))
        assertTrue(isComplete(good, now))
        assertEquals("weekend", good.trimmedReason)

        assertFalse(isComplete(good.copy(reason = "   "), now))
        assertFalse(isComplete(good.copy(staffId = null), now))
        assertFalse(isComplete(good.copy(toTime = null), now))

        val past = good.copy(fromTime = LocalTime(10, 0))
        assertEquals("From can't be in the past.", dateProblem(past, now))
        assertNull(dateProblem(good.copy(fromTime = LocalTime(10, 2)), now)) // this very minute is fine

        val backwards = good.copy(toDate = LocalDate(2026, 10, 4), toTime = LocalTime(18, 0))
        assertEquals("To must be after From.", dateProblem(backwards, now))
        assertFalse(isComplete(backwards, now))
    }
}
