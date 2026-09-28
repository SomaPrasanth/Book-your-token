package com.example.bookyourtoken.ui.common

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The portal mixes "GOBI CHILLI" and "Chicken Gravy"; show everything in Title Case. */
fun prettyName(raw: String): String =
    raw.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).joinToString(" ") { word ->
        word.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

/** "₹40" -> 40. Null when the portal didn't show a usable price. */
fun priceValue(price: String?): Int? =
    price?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }

fun formatRupees(amount: Int): String = "₹$amount"

fun formatTime(hour: Int, minute: Int): String =
    LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
