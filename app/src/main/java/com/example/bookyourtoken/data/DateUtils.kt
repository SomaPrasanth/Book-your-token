package com.example.bookyourtoken.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** All date handling is anchored to Asia/Kolkata regardless of the device's own timezone. */
object DateUtils {
    private val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    private val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    fun today(): LocalDate = LocalDate.now(ZONE)

    fun tomorrow(): LocalDate = today().plusDays(1)

    fun tomorrowString(): String = tomorrow().format(FORMAT)

    /** Parses the portal's dd-MM-yyyy dates; null if the portal sent something else. */
    fun parsePortalDate(value: String?): LocalDate? =
        value?.let { runCatching { LocalDate.parse(it, FORMAT) }.getOrNull() }

    /** e.g. "Tuesday, 29 Sep" */
    fun friendlyLabel(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH))
}
