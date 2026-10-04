package com.example.bookyourtoken.data

import com.example.bookyourtoken.data.models.LeaveRecord
import com.example.bookyourtoken.data.models.leaveApplyResult
import com.example.bookyourtoken.data.models.leaveCancelResult
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeaveFormatTest {

    private fun record(from: String, to: String, status: String = "Applied") = LeaveRecord(
        fromRaw = from,
        toRaw = to,
        from = LeaveFormat.parseHistoryDateTime(from),
        to = LeaveFormat.parseHistoryDateTime(to),
        type = "Leave",
        reason = "Weekend",
        status = status
    )

    @Test
    fun `history dates parse as dd-MM-yyyy hh mm a`() {
        assertEquals(LocalDateTime(2026, 10, 1, 0, 0), LeaveFormat.parseHistoryDateTime("01-10-2026 12:00 AM"))
        assertEquals(LocalDateTime(2026, 10, 5, 18, 0), LeaveFormat.parseHistoryDateTime("05-10-2026 06:00 PM"))
        assertEquals(LocalDateTime(2026, 10, 5, 12, 30), LeaveFormat.parseHistoryDateTime("05-10-2026 12:30 PM"))
        assertEquals(LocalDateTime(2026, 10, 5, 9, 5), LeaveFormat.parseHistoryDateTime("05-10-2026 9:05 am"))
    }

    @Test
    fun `unparseable history dates give null instead of crashing`() {
        assertNull(LeaveFormat.parseHistoryDateTime("2026-10-05 18:00"))
        assertNull(LeaveFormat.parseHistoryDateTime("31-02-2026 06:00 PM"))
        assertNull(LeaveFormat.parseHistoryDateTime("05-10-2026 13:00 PM"))
        assertNull(LeaveFormat.parseHistoryDateTime(""))
    }

    @Test
    fun `apply times are 12-hour with no leading zero and a separate session`() {
        assertEquals("12:00" to "AM", LeaveFormat.applyTime(LocalTime(0, 0)))
        assertEquals("6:00" to "PM", LeaveFormat.applyTime(LocalTime(18, 0)))
        assertEquals("12:05" to "PM", LeaveFormat.applyTime(LocalTime(12, 5)))
        assertEquals("9:45" to "AM", LeaveFormat.applyTime(LocalTime(9, 45)))
        assertEquals("11:55" to "PM", LeaveFormat.applyTime(LocalTime(23, 55)))
    }

    @Test
    fun `times round down to the site's 5-minute steps`() {
        assertEquals(LocalTime(18, 0), LeaveFormat.roundDownTo5Minutes(LocalTime(18, 4)))
        assertEquals(LocalTime(18, 5), LeaveFormat.roundDownTo5Minutes(LocalTime(18, 5)))
        assertEquals(LocalTime(23, 55), LeaveFormat.roundDownTo5Minutes(LocalTime(23, 59)))
    }

    @Test
    fun `apply sends the verified ten fields with slashed dates`() {
        val fields = LeaveFormat.applyFormFields(
            rollNo = "23i362",
            from = LocalDateTime(2026, 10, 5, 0, 0),
            to = LocalDateTime(2026, 10, 5, 18, 0),
            typeId = "L",
            reason = "weekend",
            staffId = "PS0679"
        )
        assertEquals(
            listOf(
                "rollno" to "23I362",
                "fromDate" to "05/10/2026",
                "fromTime" to "12:00",
                "fromSession" to "AM",
                "toDate" to "05/10/2026",
                "toTime" to "6:00",
                "toSession" to "PM",
                "leaveType" to "L",
                "reason" to "weekend",
                "manager" to "PS0679"
            ),
            fields
        )
        assertEquals("01/02/2027", LeaveFormat.applyDate(LocalDate(2027, 2, 1)))
    }

    @Test
    fun `cancel sends the history's dashed dates as-is, without times`() {
        val fields = LeaveFormat.cancelFormFields("23i362", record("05-10-2026 12:00 AM", "05-10-2026 06:00 PM"))
        assertEquals(
            listOf("rollno" to "23I362", "fromDate" to "05-10-2026", "toDate" to "05-10-2026"),
            fields
        )
    }

    @Test
    fun `reason keeps only letters, digits, spaces, full stops and commas`() {
        assertEquals("Going home, back Sunday.", LeaveFormat.sanitizeReason("Going home, back Sunday."))
        assertEquals("Home  visit 2", LeaveFormat.sanitizeReason("Home & visit #2!"))
        assertEquals("Fest", LeaveFormat.sanitizeReason("Fest\n'\"/é"))
    }

    @Test
    fun `leave rows are read from the portal's arrays`() {
        val types = HostelClient.parseLeaveTypes("""[{"leave_type":"L","leave":"Leave"},{"leave_type":null}]""")
        assertEquals(listOf(com.example.bookyourtoken.data.models.LeaveType("L", "Leave")), types)

        val staff = HostelClient.parseApprovers("""[{"staff_id":"PS0679","staff_name":" Dr. A "},{"staff_id":"PS1","staff_name":null}]""")
        assertEquals(listOf("PS0679" to "Dr. A", "PS1" to "PS1"), staff.map { it.staffId to it.name })

        val history = HostelClient.parseLeaveHistory(
            """[{"fromdate":"01-10-2026 12:00 AM","todate":"04-10-2026 12:00 AM","leave_type":"Leave","reason":"Weekend","status":"Approved"},
                {"fromdate":"odd","todate":"also odd","leave_type":"Leave","reason":"x","status":"Applied"}]"""
        )
        assertEquals(2, history.size)
        assertEquals(LocalDateTime(2026, 10, 1, 0, 0), history[0].from)
        assertEquals("Approved", history[0].status)
        assertFalse(history[0].canCancel)
        assertNull(history[1].from)
        assertEquals("odd", history[1].fromRaw)
        assertTrue(history[1].canCancel)

        assertFailsWith<IllegalArgumentException> { HostelClient.parseLeaveHistory("""{"oresult":1}""") }
    }

    @Test
    fun `leave apply and cancel both succeed only on oresult 1`() {
        assertTrue(leaveApplyResult(1).success)
        assertEquals("Leave applied", leaveApplyResult(1).message)
        assertEquals("Leave already applied", leaveApplyResult(0).message)
        assertFalse(leaveApplyResult(0).success)
        assertEquals("Error occurred", leaveApplyResult(null).message)

        assertTrue(leaveCancelResult(1).success)
        assertEquals("Leave cancelled", leaveCancelResult(1).message)
        assertFalse(leaveCancelResult(0).success)
        assertEquals("Leave already cancelled", leaveCancelResult(0).message)
        assertEquals("Error occurred", leaveCancelResult(7).message)

        // Responses are one-element arrays.
        assertEquals(1, HostelClient.resultCode("""[{"oresult":1}]"""))
    }
}
