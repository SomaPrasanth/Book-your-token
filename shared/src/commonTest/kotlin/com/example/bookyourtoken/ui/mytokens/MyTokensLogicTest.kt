package com.example.bookyourtoken.ui.mytokens

import com.example.bookyourtoken.data.models.BookedToken
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlinx.datetime.LocalDate

class MyTokensLogicTest {

    private fun token(name: String, date: String?, meal: String, qty: Int = 1) = BookedToken(
        messId = "1", tokenName = name, tokenId = 1, issueDate = null, expireDate = date, tokenNo = null,
        tokenQty = qty, status = "N", viewStatus = "0", mealTime = meal, count = null
    )

    private val today = LocalDate(2026, 9, 28)

    @Test
    fun `groups by date soonest first with today and tomorrow labels`() {
        val groups = groupTokensByDate(
            listOf(
                token("CHICKEN", "30-09-2026", "Dinner"),
                token("BOILED EGG", "29-09-2026", "Dinner"),
                token("OMELETTE", "29-09-2026", "Breakfast"),
                token("SNACKS", "28-09-2026", "Lunch")
            ),
            today
        )
        assertEquals(listOf("28-09-2026", "29-09-2026", "30-09-2026"), groups.map { it.date })
        assertEquals("Today · Monday, 28 Sep", groups[0].label)
        assertEquals("Tomorrow · Tuesday, 29 Sep", groups[1].label)
        assertEquals("Wednesday, 30 Sep", groups[2].label)
        assertEquals(listOf("OMELETTE", "BOILED EGG"), groups[1].tokens.map { it.tokenName })
    }

    @Test
    fun `unparseable dates sort last instead of crashing`() {
        val groups = groupTokensByDate(listOf(token("A", "garbage", "Lunch"), token("B", "29-09-2026", "Lunch")), today)
        assertEquals(listOf("29-09-2026", "garbage"), groups.map { it.date })
    }

    @Test
    fun `quantityOf sums rows for the same name, date and meal only`() {
        val rows = listOf(
            token("EGG GRAVY", "29-09-2026", "Dinner", qty = 1),
            token("EGG GRAVY", "29-09-2026", "Dinner", qty = 2),
            token("EGG GRAVY", "29-09-2026", "Lunch", qty = 5)
        )
        assertEquals(3, quantityOf(rows, token("EGG GRAVY", "29-09-2026", "Dinner")))
        assertEquals(0, quantityOf(emptyList(), token("EGG GRAVY", "29-09-2026", "Dinner")))
    }
}
