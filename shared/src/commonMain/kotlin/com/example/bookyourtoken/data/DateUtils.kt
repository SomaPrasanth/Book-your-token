package com.example.bookyourtoken.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** All date handling is anchored to Asia/Kolkata regardless of the device's own timezone. */
object DateUtils {
    val ZONE: TimeZone = TimeZone.of("Asia/Kolkata")

    private val PORTAL_DATE = Regex("""(\d{2})-(\d{2})-(\d{4})""")

    fun today(): LocalDate = Clock.System.todayIn(ZONE)

    fun tomorrow(): LocalDate = today().plus(1, DateTimeUnit.DAY)

    fun tomorrowString(): String = formatPortalDate(tomorrow())

    /** dd-MM-yyyy, the only format the portal accepts. */
    fun formatPortalDate(date: LocalDate): String =
        "${date.day.pad2()}-${date.month.number.pad2()}-${date.year}"

    /** Parses the portal's dd-MM-yyyy dates; null if the portal sent something else. */
    fun parsePortalDate(value: String?): LocalDate? {
        val match = value?.let { PORTAL_DATE.matchEntire(it.trim()) } ?: return null
        val (day, month, year) = match.destructured
        return runCatching { LocalDate(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
    }

    /** e.g. "Tuesday, 29 Sep" */
    fun friendlyLabel(date: LocalDate): String =
        "${date.dayOfWeek.displayName()}, ${date.day} ${date.month.shortName()}"

    private fun Int.pad2(): String = toString().padStart(2, '0')

    private fun DayOfWeek.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

    private fun Month.shortName(): String = name.take(3).lowercase().replaceFirstChar { it.uppercase() }
}
