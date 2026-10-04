package com.example.bookyourtoken.ui.leave

import com.example.bookyourtoken.data.DateUtils
import com.example.bookyourtoken.data.models.LeaveRecord
import com.example.bookyourtoken.ui.common.formatTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * Newest first by the parsed from date. The portal sorts as text (01-10 before 03-09), so its order
 * means nothing. Rows whose dates didn't parse go last, in the order they came.
 */
fun sortLeaveHistory(records: List<LeaveRecord>): List<LeaveRecord> =
    records.sortedWith(compareBy<LeaveRecord> { it.from == null }.thenByDescending { it.from })

/** "Sat 05 Oct, 6:00 PM" — with the year when it isn't [currentYear]. Falls back to the raw string. */
fun leaveDateTimeLabel(parsed: LocalDateTime?, raw: String, currentYear: Int = DateUtils.today().year): String {
    parsed ?: return raw
    val year = if (parsed.year != currentYear) " ${parsed.year}" else ""
    return "${DateUtils.shortLabel(parsed.date)}$year, ${formatTime(parsed.hour, parsed.minute)}"
}

/** The other Applied leaves a cancel could hit: the portal matches by from and to date only. */
fun ambiguousCancels(record: LeaveRecord, history: List<LeaveRecord>): List<LeaveRecord> =
    history.filter { it.canCancel && it.hasSameDatesAs(record) && !it.isSameLeaveAs(record) }

/** A leave counts as gone once it's out of the history or shows Cancelled (the portal does either). */
private fun List<LeaveRecord>.stillHolds(record: LeaveRecord): Boolean =
    any { it.isSameLeaveAs(record) && it.status != "Cancelled" }

/** What the refreshed history says about a cancel of [target]. */
sealed interface CancelCheck {
    /** The leave is no longer in the history. */
    data object Gone : CancelCheck

    /** The target is still there but another leave with the same dates went instead. */
    data class OtherGone(val other: LeaveRecord) : CancelCheck

    data object StillThere : CancelCheck
}

fun checkCancel(target: LeaveRecord, before: List<LeaveRecord>, after: List<LeaveRecord>): CancelCheck {
    if (!after.stillHolds(target)) return CancelCheck.Gone
    val other = before.firstOrNull {
        it.hasSameDatesAs(target) && !it.isSameLeaveAs(target) && it.status != "Cancelled" && !after.stillHolds(it)
    }
    return if (other != null) CancelCheck.OtherGone(other) else CancelCheck.StillThere
}

/** How many leaves in [history] run exactly from [from] to [to] — used to confirm an apply after a timeout. */
fun countMatching(history: List<LeaveRecord>, from: LocalDateTime, to: LocalDateTime): Int =
    history.count { it.from == from && it.to == to && it.status != "Cancelled" }

/** The apply form. Times are already on the site's 5-minute steps. */
data class LeaveForm(
    val typeId: String? = null,
    val staffId: String? = null,
    val fromDate: LocalDate? = null,
    val fromTime: LocalTime? = null,
    val toDate: LocalDate? = null,
    val toTime: LocalTime? = null,
    val reason: String = ""
) {
    val from: LocalDateTime? get() = if (fromDate != null && fromTime != null) LocalDateTime(fromDate, fromTime) else null
    val to: LocalDateTime? get() = if (toDate != null && toTime != null) LocalDateTime(toDate, toTime) else null
    val trimmedReason: String get() = reason.trim()
}

/** Why the From/To pair can't be sent, or null when it's fine (or not filled in yet). */
fun dateProblem(form: LeaveForm, now: LocalDateTime): String? {
    val from = form.from
    val to = form.to
    return when {
        from != null && from < now.truncatedToMinute() -> "From can't be in the past."
        from != null && to != null && to <= from -> "To must be after From."
        else -> null
    }
}

fun isComplete(form: LeaveForm, now: LocalDateTime): Boolean =
    form.typeId != null && form.staffId != null && form.from != null && form.to != null &&
        form.trimmedReason.isNotEmpty() && dateProblem(form, now) == null

private fun LocalDateTime.truncatedToMinute(): LocalDateTime = LocalDateTime(date, LocalTime(hour, minute))
