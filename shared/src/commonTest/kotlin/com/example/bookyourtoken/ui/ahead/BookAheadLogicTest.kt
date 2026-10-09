package com.example.bookyourtoken.ui.ahead

import com.example.bookyourtoken.data.models.TokenItem
import com.example.bookyourtoken.ui.booking.BookingLine
import com.example.bookyourtoken.ui.booking.LineStatus
import com.example.bookyourtoken.ui.tokens.Selection
import com.example.bookyourtoken.ui.tokens.toggle
import com.example.bookyourtoken.ui.tokens.withQuantity
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookAheadLogicTest {

    private val today = LocalDate(2026, 10, 2)

    private val gobi = TokenItem(
        name = "Gobi Chilli", buttonId = "Gobichilli", ptokenId = 8, maxQty = 2, price = "₹40",
        // As the live dropdown lists them — not in string order.
        dates = listOf("29-09-2026", "04-10-2026", "03-10-2026", "11-10-2026", "06-10-2026"),
        meals = listOf("Lunch", "Dinner"), defaultMeal = "Lunch"
    )
    private val egg = TokenItem(
        name = "Boiled Egg", buttonId = "BoiledEggtoken", ptokenId = 94, maxQty = 5, price = "₹12",
        dates = listOf("04-10-2026", "02-10-2026", "not a date", "4-10-2026", ""),
        meals = listOf("Dinner"), defaultMeal = "Dinner"
    )

    @Test
    fun `dates are in calendar order, not string order`() {
        val dates = upcomingDates(listOf(gobi, egg), today)
        assertEquals(listOf("02-10-2026", "03-10-2026", "04-10-2026", "06-10-2026", "11-10-2026"), dates.map { it.raw })
    }

    @Test
    fun `today and later, past dropped`() {
        val raws = upcomingDates(listOf(gobi, egg), today).map { it.raw }
        assertEquals("02-10-2026", raws.first()) // today, offered by egg, goes first
        assertTrue("03-10-2026" in raws)
        assertFalse("29-09-2026" in raws) // past
    }

    @Test
    fun `today only when the portal offers it`() {
        val raws = upcomingDates(listOf(gobi), today).map { it.raw }
        assertFalse("02-10-2026" in raws)
    }

    @Test
    fun `unparseable strings are skipped without crashing`() {
        val raws = upcomingDates(listOf(egg), today).map { it.raw }
        assertEquals(listOf("02-10-2026", "04-10-2026"), raws)
    }

    @Test
    fun `each date lists the items offered on it, in page order, and keeps the raw string`() {
        val dates = upcomingDates(listOf(gobi, egg), today)
        val oct4 = dates.single { it.raw == "04-10-2026" }
        assertEquals(LocalDate(2026, 10, 4), oct4.date)
        assertEquals(listOf(8, 94), oct4.items.map { it.ptokenId })
        assertEquals(listOf(8), dates.single { it.raw == "06-10-2026" }.items.map { it.ptokenId })
    }

    @Test
    fun `no dates when nothing is upcoming`() {
        assertTrue(upcomingDates(listOf(gobi.copy(dates = listOf("30-09-2026", "01-10-2026"))), today).isEmpty())
        assertTrue(upcomingDates(emptyList(), today).isEmpty())
    }

    @Test
    fun `an item listing a date twice appears once on that date`() {
        val dup = gobi.copy(dates = listOf("04-10-2026", "04-10-2026"))
        assertEquals(1, upcomingDates(listOf(dup), today).single().items.size)
    }

    @Test
    fun `cutoff is 17 30 the day before`() {
        val oct4 = LocalDate(2026, 10, 4)
        assertEquals(LocalDateTime(2026, 10, 3, 17, 30), bookingCutoff(oct4))
        assertFalse(isLikelyClosed(oct4, LocalDateTime(2026, 10, 3, 17, 30)))
        assertTrue(isLikelyClosed(oct4, LocalDateTime(2026, 10, 3, 17, 31)))
        // Across a month boundary
        assertEquals(LocalDateTime(2026, 9, 30, 17, 30), bookingCutoff(LocalDate(2026, 10, 1)))
    }

    @Test
    fun `the day-before cutoff never marks today`() {
        assertFalse(isLikelyClosed(today, LocalDateTime(2026, 10, 2, 23, 59)))
        // Left open past midnight: yesterday's chip does read as closed.
        assertTrue(isLikelyClosed(today, LocalDateTime(2026, 10, 3, 0, 1)))
    }

    @Test
    fun `same-day meal deadlines are hints for today only`() {
        val morning = LocalDateTime(2026, 10, 2, 9, 0)
        val afternoon = LocalDateTime(2026, 10, 2, 14, 1)
        assertFalse(isMealLikelyClosed(today, "Lunch", morning))
        assertTrue(isMealLikelyClosed(today, "Lunch", LocalDateTime(2026, 10, 2, 9, 1)))
        assertFalse(isMealLikelyClosed(today, "Dinner", LocalDateTime(2026, 10, 2, 14, 0)))
        assertTrue(isMealLikelyClosed(today, "Dinner", afternoon))
        assertFalse(isMealLikelyClosed(today, "Breakfast", afternoon)) // no deadline stated
        assertFalse(isMealLikelyClosed(LocalDate(2026, 10, 3), "Lunch", afternoon)) // not today
    }

    @Test
    fun `cutoff hint and headings`() {
        assertEquals("Same day: Lunch by 9 AM, Dinner by 2 PM", cutoffHint(today, today))
        assertEquals("Book by 5:30 PM today", cutoffHint(LocalDate(2026, 10, 3), today))
        assertEquals("Book by 5:30 PM on Sat 03 Oct", cutoffHint(LocalDate(2026, 10, 4), today))
        assertEquals("Today · Fri 02 Oct", dateHeading("02-10-2026", today))
        assertEquals("Tomorrow · Sat 03 Oct", dateHeading("03-10-2026", today))
        assertEquals("Sun 04 Oct", dateHeading("04-10-2026", today))
        assertEquals("garbage", dateHeading("garbage", today))
    }

    @Test
    fun `picks persist per date and empty dates drop out`() {
        var s: DateSelections = emptyMap()
        s = s.update("04-10-2026") { it.toggle(gobi) }
        s = s.update("06-10-2026") { it.toggle(gobi).withQuantity(gobi, 9) }
        s = s.update("04-10-2026") { it.toggle(egg) }
        assertEquals(3, s.itemCount)
        assertEquals(2, s.dateCount)
        assertEquals(2, s["06-10-2026"]!![8]!!.quantity) // capped at maxQty for that date too

        s = s.update("06-10-2026") { it.toggle(gobi) }
        assertFalse("06-10-2026" in s)
        assertEquals(1, s.dateCount)
    }

    @Test
    fun `booking lines go in date order then page order with the exact raw date`() {
        val dates = upcomingDates(listOf(gobi, egg), today)
        val s: DateSelections = mapOf(
            "11-10-2026" to mapOf(8 to Selection("Dinner", 1)),
            "04-10-2026" to mapOf(94 to Selection("Dinner", 2), 8 to Selection("Lunch", 1)),
            "03-10-2026" to mapOf(8 to Selection("Lunch", 2))
        )
        val lines = bookingLines(dates, s)
        assertEquals(
            listOf("03-10-2026" to 8, "04-10-2026" to 8, "04-10-2026" to 94, "11-10-2026" to 8),
            lines.map { it.date to it.item.ptokenId }
        )
        assertEquals(listOf("Lunch", "Lunch", "Dinner", "Dinner"), lines.map { it.meal })
    }

    @Test
    fun `refresh keeps valid picks and drops vanished dates, items and meals`() {
        val s: DateSelections = mapOf(
            "04-10-2026" to mapOf(8 to Selection("Lunch", 1), 94 to Selection("Dinner", 1)),
            "11-10-2026" to mapOf(8 to Selection("Breakfast", 1)), // meal no longer offered
            "20-10-2026" to mapOf(8 to Selection("Lunch", 1)) // date gone
        )
        val dates = upcomingDates(listOf(gobi), today) // egg no longer listed
        val (kept, dropped) = s.prunedTo(dates)
        assertEquals(mapOf("04-10-2026" to mapOf(8 to Selection("Lunch", 1))), kept)
        assertEquals(3, dropped)
    }

    @Test
    fun `picks on a day that has passed are removed with their own notice`() {
        val s: DateSelections = mapOf(
            "01-10-2026" to mapOf(8 to Selection("Lunch", 1), 94 to Selection("Dinner", 1)),
            "04-10-2026" to mapOf(8 to Selection("Lunch", 1))
        )
        val passed = s.passed(today)
        assertEquals(setOf("01-10-2026"), passed.keys)
        assertEquals("Removed selections for Thu 01 Oct; that day has passed.", refreshNotice(passed, 0))
        assertEquals(
            "Removed selections for Thu 01 Oct; that day has passed. 1 selection was removed — that item or date is no longer offered.",
            refreshNotice(passed, 1)
        )
        assertEquals(null, refreshNotice(emptyMap(), 0))
    }

    @Test
    fun `booked lines leave the selection, failed ones stay`() {
        val s: DateSelections = mapOf(
            "04-10-2026" to mapOf(8 to Selection("Lunch", 1), 94 to Selection("Dinner", 1)),
            "06-10-2026" to mapOf(8 to Selection("Lunch", 1))
        )
        val results = listOf(
            BookingLine(gobi, "Lunch", 1, "04-10-2026", LineStatus.Done(true, "Token Booked", 1)),
            BookingLine(egg, "Dinner", 1, "04-10-2026", LineStatus.Done(false, "The token limit has been reached.", 9)),
            BookingLine(gobi, "Lunch", 1, "06-10-2026", LineStatus.Done(true, "Token Booked", 1))
        )
        assertEquals(mapOf("04-10-2026" to mapOf(94 to Selection("Dinner", 1))), s.withoutBooked(results))
    }
}
