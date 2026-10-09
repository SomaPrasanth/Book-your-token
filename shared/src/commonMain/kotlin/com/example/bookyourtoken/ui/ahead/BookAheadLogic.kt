package com.example.bookyourtoken.ui.ahead

import com.example.bookyourtoken.data.DateUtils
import com.example.bookyourtoken.data.models.TokenItem
import com.example.bookyourtoken.ui.booking.BookingLine
import com.example.bookyourtoken.ui.tokens.Selections
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * One upcoming food date. [raw] is the portal's own dropdown string — the only thing ever sent as
 * Tokendatetime. [date] is for sorting and labels only, never formatted back into a request.
 */
data class UpcomingDate(val date: LocalDate, val raw: String, val items: List<TokenItem>)

/**
 * Every date from [today] on that at least one item is offered on, in calendar order. Comes from the
 * one StudentView fetch — each item's date dropdown already lists all its dates, and today is there
 * only when the portal takes same-day bookings. Strings that don't parse as dd-MM-yyyy are skipped.
 * Items keep the page's order within a date.
 */
fun upcomingDates(items: List<TokenItem>, today: LocalDate): List<UpcomingDate> =
    items.flatMap { item -> item.dates.distinct().map { raw -> raw to item } }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (raw, offered) -> DateUtils.parsePortalDate(raw)?.let { UpcomingDate(it, raw, offered) } }
        .filter { it.date >= today }
        // dd-MM-yyyy strings don't sort ("04-10-2026" < "29-09-2026"): always by the parsed date.
        .sortedBy { it.date }

/**
 * The portal's JS closes booking for a food date at 17:30 the day before. Inferred from the site's
 * code, so it only ever labels a date — the server decides (oresult 3 when too late).
 */
fun bookingCutoff(date: LocalDate): LocalDateTime =
    LocalDateTime(date.minus(1, DateTimeUnit.DAY), LocalTime(17, 30))

/** Never for today: if the portal still offers today, the day-before rule clearly doesn't apply. */
fun isLikelyClosed(date: LocalDate, now: LocalDateTime): Boolean = date != now.date && now > bookingCutoff(date)

/** Same-day deadlines from the portal's notice; none is stated for breakfast. */
fun sameDayDeadline(meal: String): LocalTime? = when (meal) {
    "Lunch" -> LocalTime(9, 0)
    "Dinner" -> LocalTime(14, 0)
    else -> null
}

/** Only a hint on today's meal choices: booking stays allowed and the server decides. */
fun isMealLikelyClosed(date: LocalDate, meal: String, now: LocalDateTime): Boolean {
    val deadline = sameDayDeadline(meal) ?: return false
    return date == now.date && now.time > deadline
}

const val SAME_DAY_HINT = "Same day: Lunch by 9 AM, Dinner by 2 PM"

/** "Book by 5:30 PM on Sat 03 Oct" (or "… today"); for today itself, the same-day deadlines. */
fun cutoffHint(date: LocalDate, today: LocalDate): String {
    if (date == today) return SAME_DAY_HINT
    val day = bookingCutoff(date).date
    return "Book by 5:30 PM " + if (day == today) "today" else "on ${DateUtils.shortLabel(day)}"
}

/** "Today · Fri 02 Oct", "Tomorrow · Sat 03 Oct" or "Sun 04 Oct": dialog headings. Falls back to the raw string. */
fun dateHeading(raw: String, today: LocalDate): String {
    val date = DateUtils.parsePortalDate(raw) ?: return raw
    val label = DateUtils.shortLabel(date)
    return when (date) {
        today -> "Today · $label"
        today.plus(1, DateTimeUnit.DAY) -> "Tomorrow · $label"
        else -> label
    }
}

/** Picks per date (keyed by the raw date string), each the same [Selections] Tomorrow uses. */
typealias DateSelections = Map<String, Selections>

/** Applies [transform] to one date's picks; a date with nothing picked is dropped from the map. */
fun DateSelections.update(date: String, transform: (Selections) -> Selections): DateSelections {
    val next = transform(this[date].orEmpty())
    return if (next.isEmpty()) this - date else this + (date to next)
}

val DateSelections.itemCount: Int get() = values.sumOf { it.size }

val DateSelections.dateCount: Int get() = count { it.value.isNotEmpty() }

/**
 * After a refresh: keeps only picks whose date is still upcoming and whose item is still offered
 * that day with the picked meal. Returns the kept picks and how many were dropped.
 */
fun DateSelections.prunedTo(dates: List<UpcomingDate>): Pair<DateSelections, Int> {
    var dropped = 0
    val kept = mapNotNull { (raw, picks) ->
        val offered = dates.firstOrNull { it.raw == raw }?.items?.associateBy { it.ptokenId }
        val stillValid = picks.filter { (id, sel) ->
            val item = offered?.get(id)
            item != null && sel.meal in item.meals && sel.quantity in 1..item.maxQty
        }
        dropped += picks.size - stillValid.size
        stillValid.takeIf { it.isNotEmpty() }?.let { raw to it }
    }.toMap()
    return kept to dropped
}

/** Picks on dates now in the past: the app was left open over midnight. */
fun DateSelections.passed(today: LocalDate): DateSelections =
    filterKeys { raw -> DateUtils.parsePortalDate(raw)?.let { it < today } == true }

/** The snackbar after a refresh that dropped picks, or null if none were. */
fun refreshNotice(passed: DateSelections, dropped: Int): String? {
    val parts = buildList {
        val days = passed.keys.mapNotNull { DateUtils.parsePortalDate(it) }.sorted()
        if (days.isNotEmpty()) {
            val label = days.joinToString(" and ") { DateUtils.shortLabel(it) }
            add("Removed selections for $label; " + if (days.size == 1) "that day has passed." else "those days have passed.")
        }
        when {
            dropped == 1 -> add("1 selection was removed — that item or date is no longer offered.")
            dropped > 1 -> add("$dropped selections were removed — those items or dates are no longer offered.")
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

/** One request per pick: dates in calendar order, items in the page's order within a date. */
fun bookingLines(dates: List<UpcomingDate>, selections: DateSelections): List<BookingLine> =
    dates.flatMap { day ->
        val picks = selections[day.raw] ?: return@flatMap emptyList()
        day.items.mapNotNull { item ->
            picks[item.ptokenId]?.let { BookingLine(item, it.meal, it.quantity, date = day.raw) }
        }
    }

/** Booked lines leave the selection; failed ones stay so they can be adjusted and retried. */
fun DateSelections.withoutBooked(lines: List<BookingLine>): DateSelections =
    lines.filter { it.succeeded }.fold(this) { acc, line -> acc.update(line.date) { it - line.item.ptokenId } }
