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
 * Every date after [today] that at least one item is offered on, in calendar order. Comes from the
 * one StudentView fetch — each item's date dropdown already lists all its dates. Strings that don't
 * parse as dd-MM-yyyy are skipped. Items keep the page's order within a date.
 */
fun upcomingDates(items: List<TokenItem>, today: LocalDate): List<UpcomingDate> =
    items.flatMap { item -> item.dates.distinct().map { raw -> raw to item } }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (raw, offered) -> DateUtils.parsePortalDate(raw)?.let { UpcomingDate(it, raw, offered) } }
        .filter { it.date > today }
        // dd-MM-yyyy strings don't sort ("04-10-2026" < "29-09-2026"): always by the parsed date.
        .sortedBy { it.date }

/**
 * The portal's JS closes booking for a food date at 17:30 the day before. Inferred from the site's
 * code, so it only ever labels a date — the server decides (oresult 3 when too late).
 */
fun bookingCutoff(date: LocalDate): LocalDateTime =
    LocalDateTime(date.minus(1, DateTimeUnit.DAY), LocalTime(17, 30))

fun isLikelyClosed(date: LocalDate, now: LocalDateTime): Boolean = now > bookingCutoff(date)

/** "Book by 5:30 PM on Sat 03 Oct" (or "… today"). */
fun cutoffHint(date: LocalDate, today: LocalDate): String {
    val day = bookingCutoff(date).date
    return "Book by 5:30 PM " + if (day == today) "today" else "on ${DateUtils.shortLabel(day)}"
}

/** "Tomorrow · Sat 03 Oct" or "Sun 04 Oct": dialog headings. Falls back to the raw string. */
fun dateHeading(raw: String, today: LocalDate): String {
    val date = DateUtils.parsePortalDate(raw) ?: return raw
    val label = DateUtils.shortLabel(date)
    return if (date == today.plus(1, DateTimeUnit.DAY)) "Tomorrow · $label" else label
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
