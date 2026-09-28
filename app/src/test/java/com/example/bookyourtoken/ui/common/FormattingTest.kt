package com.example.bookyourtoken.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattingTest {

    @Test
    fun `prettyName title-cases all-caps and mixed names`() {
        assertEquals("Gobi Chilli", prettyName("GOBI CHILLI"))
        assertEquals("Chicken Gravy", prettyName("Chicken Gravy"))
        assertEquals("Boiled Egg", prettyName("  BOILED   EGG "))
    }

    @Test
    fun `priceValue reads the rupee amount`() {
        assertEquals(40, priceValue("₹40"))
        assertNull(priceValue(null))
        assertNull(priceValue("Free"))
    }

    @Test
    fun `formatTime uses a 12-hour clock`() {
        assertEquals("4:00 PM", formatTime(16, 0))
        assertEquals("9:05 AM", formatTime(9, 5))
    }
}
