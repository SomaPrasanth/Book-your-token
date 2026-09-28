package com.example.bookyourtoken.ui.tokens

import com.example.bookyourtoken.data.models.TokenItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenSelectionTest {

    private val eggGravy = TokenItem(
        name = "Egg Gravy", buttonId = "EggGraveytoken", ptokenId = 87, maxQty = 5, price = "₹25",
        dates = listOf("29-09-2026"), meals = listOf("Lunch", "Dinner"), defaultMeal = "Dinner"
    )
    private val gobi = eggGravy.copy(name = "Gobi Chilli", buttonId = "Gobichilli", ptokenId = 8, maxQty = 2)

    @Test
    fun `toggle selects with the default meal and quantity 1, and toggles off again`() {
        val on = emptyMap<Int, Selection>().toggle(eggGravy)
        assertEquals(Selection("Dinner", 1), on[87])
        assertFalse(on.toggle(eggGravy).containsKey(87))
    }

    @Test
    fun `meal choice survives selecting other items and changing quantity`() {
        val s = emptyMap<Int, Selection>()
            .toggle(eggGravy)
            .withMeal(eggGravy, "Lunch")
            .toggle(gobi)
            .withQuantity(eggGravy, 3)
        assertEquals(Selection("Lunch", 3), s[87])
        assertEquals(Selection("Dinner", 1), s[8])
    }

    @Test
    fun `quantity is clamped to 1 to maxQty`() {
        val s = emptyMap<Int, Selection>().toggle(gobi)
        assertEquals(2, s.withQuantity(gobi, 9)[8]!!.quantity)
        assertEquals(1, s.withQuantity(gobi, 0)[8]!!.quantity)
    }

    @Test
    fun `meal and quantity changes are ignored for unselected items or unknown meals`() {
        val none = emptyMap<Int, Selection>()
        assertTrue(none.withMeal(eggGravy, "Lunch").isEmpty())
        assertTrue(none.withQuantity(eggGravy, 2).isEmpty())
        val s = none.toggle(eggGravy)
        assertEquals("Dinner", s.withMeal(eggGravy, "Breakfast")[87]!!.meal)
    }
}
