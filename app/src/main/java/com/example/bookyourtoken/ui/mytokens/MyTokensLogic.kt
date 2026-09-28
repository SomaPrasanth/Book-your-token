package com.example.bookyourtoken.ui.mytokens

import com.example.bookyourtoken.data.DateUtils
import com.example.bookyourtoken.data.models.BookedToken
import java.time.LocalDate

data class TokenGroup(val date: String, val label: String, val tokens: List<BookedToken>)

private fun mealOrder(meal: String?): Int = when (meal) {
    "Breakfast" -> 0
    "Lunch" -> 1
    "Dinner" -> 2
    else -> 3
}

/** Groups StudentGetToken rows by date, soonest first, with Today/Tomorrow called out. */
fun groupTokensByDate(tokens: List<BookedToken>, today: LocalDate): List<TokenGroup> {
    val tomorrow = today.plusDays(1)
    return tokens
        .groupBy { it.expireDate.orEmpty() }
        .map { (date, rows) ->
            val parsed = DateUtils.parsePortalDate(date)
            val label = when (parsed) {
                null -> date.ifBlank { "Unknown date" }
                today -> "Today · ${DateUtils.friendlyLabel(parsed)}"
                tomorrow -> "Tomorrow · ${DateUtils.friendlyLabel(parsed)}"
                else -> DateUtils.friendlyLabel(parsed)
            }
            parsed to TokenGroup(date, label, rows.sortedBy { mealOrder(it.mealTime) })
        }
        .sortedWith(compareBy(nullsLast()) { it.first })
        .map { it.second }
}

/** Total quantity held for the same name + date + meal as [token]; 0 once it's gone. */
fun quantityOf(tokens: List<BookedToken>, token: BookedToken): Int =
    tokens.filter { it.isSameTokenAs(token) }.sumOf { it.tokenQty ?: 1 }
