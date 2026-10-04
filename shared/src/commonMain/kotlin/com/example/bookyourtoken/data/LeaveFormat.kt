package com.example.bookyourtoken.data

import com.example.bookyourtoken.data.models.LeaveRecord
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number

/**
 * The leave endpoints' formats, captured from the live site. They differ between calls on purpose:
 * history returns "dd-MM-yyyy hh:mm a", apply takes "dd/MM/yyyy" + "h:mm" + AM/PM, and cancel takes
 * the history's dashed date part as-is. Don't convert one into another.
 */
object LeaveFormat {

    private val HISTORY_DATE_TIME = Regex("""(\d{2})-(\d{2})-(\d{4})\s+(\d{1,2}):(\d{2})\s*([AaPp][Mm])""")

    /** Parses a history fromdate/todate ("05-10-2026 06:00 PM"); null if it's in any other shape. */
    fun parseHistoryDateTime(raw: String): LocalDateTime? {
        val match = HISTORY_DATE_TIME.matchEntire(raw.trim()) ?: return null
        val (day, month, year, hour, minute, half) = match.destructured
        val h = hour.toInt()
        if (h !in 1..12) return null
        val pm = half.equals("PM", ignoreCase = true)
        val hour24 = (h % 12) + if (pm) 12 else 0
        return runCatching {
            LocalDateTime(year.toInt(), month.toInt(), day.toInt(), hour24, minute.toInt())
        }.getOrNull()
    }

    /** Apply's date: dd/MM/yyyy, with slashes. */
    fun applyDate(date: LocalDate): String =
        "${date.day.pad2()}/${date.month.number.pad2()}/${date.year}"

    /** Apply's time: 12-hour "h:mm" with no leading zero, and AM/PM separately. Midnight is 12:00 AM. */
    fun applyTime(time: LocalTime): Pair<String, String> {
        val h12 = if (time.hour % 12 == 0) 12 else time.hour % 12
        return "$h12:${time.minute.pad2()}" to if (time.hour < 12) "AM" else "PM"
    }

    /** The site's time picker moves in 5-minute steps. */
    fun roundDownTo5Minutes(time: LocalTime): LocalTime = LocalTime(time.hour, time.minute - time.minute % 5)

    /** Reason may only hold letters, digits, spaces, '.' and ','; the site rejects anything else. */
    fun sanitizeReason(input: String): String =
        input.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == ' ' || it == '.' || it == ',' }

    /** StudentLeavApply's ten fields, in the site's order. */
    fun applyFormFields(
        rollNo: String,
        from: LocalDateTime,
        to: LocalDateTime,
        typeId: String,
        reason: String,
        staffId: String
    ): List<Pair<String, String>> {
        val (fromTime, fromSession) = applyTime(from.time)
        val (toTime, toSession) = applyTime(to.time)
        return listOf(
            "rollno" to rollNo.uppercase(),
            "fromDate" to applyDate(from.date),
            "fromTime" to fromTime,
            "fromSession" to fromSession,
            "toDate" to applyDate(to.date),
            "toTime" to toTime,
            "toSession" to toSession,
            "leaveType" to typeId,
            "reason" to reason,
            "manager" to staffId
        )
    }

    /** StudentLeavCancel: the history row's dates up to the first space, exactly as returned. */
    fun cancelFormFields(rollNo: String, record: LeaveRecord): List<Pair<String, String>> = listOf(
        "rollno" to rollNo.uppercase(),
        "fromDate" to record.fromDatePart,
        "toDate" to record.toDatePart
    )

    private fun Int.pad2(): String = toString().padStart(2, '0')
}
