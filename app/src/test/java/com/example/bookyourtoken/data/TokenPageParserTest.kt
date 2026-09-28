package com.example.bookyourtoken.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenPageParserTest {

    // Trimmed real card markup from StudentView, per the build spec section 4.1 — the Buy button
    // is a sibling of the card div, not a descendant, so the parser must walk up from it.
    private val gobiChilliHtml = """
        <html><body>
        <div class="card card-span h-90 rounded-3">
          <h4 style="font-weight:bold;">GOBI CHILLI</h4>
          <h4 style="font-size: medium;"><span>Per</span>₹ 40</h4>
          <div class="form-control">
            <div class="quantity mt-4 hidden" id="quantity1">
              <input disabled id="quantityInput1" type="number" value="0">
            </div>
            <div>
              <select id="ddlgobi" class="form-select form-select-sm">
                <option value="">DATE</option>
                <option value="29-09-2026">29-09-2026</option>
                <option value="04-10-2026">04-10-2026</option>
              </select>
            </div>
            <div class="radio">
              <label><input type="radio" id="Lunch" name="Gobi_radio" value="Lunch" checked> Lunch</label>
              <label><input type="radio" id="Dinner" name="Gobi_radio" value="Dinner"> Dinner</label>
            </div>
          </div>
        </div>
        <div class="row"><button id="Gobichilli">Buy</button></div>
        </body></html>
    """.trimIndent()

    @Test
    fun `parses a known item from its card`() {
        val items = TokenPageParser.parse(gobiChilliHtml)

        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("GOBI CHILLI", item.name)
        assertEquals("Gobichilli", item.buttonId)
        assertEquals(8, item.ptokenId)
        assertEquals(2, item.maxQty)
        assertEquals("₹40", item.price)
        assertEquals(listOf("29-09-2026", "04-10-2026"), item.dates)
        assertEquals(listOf("Lunch", "Dinner"), item.meals)
        assertEquals("Lunch", item.defaultMeal)
    }

    @Test
    fun `skips known items absent from the page`() {
        val items = TokenPageParser.parse(gobiChilliHtml)

        assertTrue(items.none { it.buttonId == "Chickentoken" })
    }

    @Test
    fun `cleanPrice keeps only the rupee amount`() {
        assertEquals("₹40", TokenPageParser.cleanPrice("Per₹ 40"))
        assertEquals("₹12.5", TokenPageParser.cleanPrice("Per ₹12.5"))
        assertEquals("Free", TokenPageParser.cleanPrice(" Free "))
        assertEquals(null, TokenPageParser.cleanPrice("  "))
    }

    @Test
    fun `zero items on an unrelated page signals a parse failure to the caller`() {
        val items = TokenPageParser.parse("<html><body><p>Portal maintenance</p></body></html>")

        assertTrue(items.isEmpty())
    }
}
