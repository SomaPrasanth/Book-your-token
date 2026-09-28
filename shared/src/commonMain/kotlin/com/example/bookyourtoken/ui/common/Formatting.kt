package com.example.bookyourtoken.ui.common

/** The portal mixes "GOBI CHILLI" and "Chicken Gravy"; show everything in Title Case. */
fun prettyName(raw: String): String =
    raw.trim().lowercase().split(Regex("\\s+")).joinToString(" ") { word ->
        word.replaceFirstChar { it.titlecase() }
    }

/** "₹40" -> 40. Null when the portal didn't show a usable price. */
fun priceValue(price: String?): Int? =
    price?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }

fun formatRupees(amount: Int): String = "₹$amount"

/** 12-hour clock, e.g. "4:00 PM". */
fun formatTime(hour: Int, minute: Int): String {
    val displayHour = if (hour % 12 == 0) 12 else hour % 12
    val suffix = if (hour < 12) "AM" else "PM"
    return "$displayHour:${minute.toString().padStart(2, '0')} $suffix"
}
