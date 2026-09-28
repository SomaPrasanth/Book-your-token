package com.example.bookyourtoken.data

import com.example.bookyourtoken.data.models.TokenItem
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element

/**
 * These ids, PTOKEN_IDs and quantity caps come from the site's own Leave.js — they are not present
 * in the page HTML, so they must stay hardcoded here rather than scraped.
 */
private data class ItemDef(
    val buttonId: String,
    val ptokenId: Int,
    val selectId: String,
    val maxQty: Int
)

private val KNOWN_ITEMS = listOf(
    ItemDef("Gobichilli", 8, "ddlgobi", 2),
    ItemDef("Chickentoken", 1, "ddlchicken", 2),
    ItemDef("PanneerMasala", 75, "ddlmushroommasal", 2),
    ItemDef("MushroomManchuriantoken", 91, "ddlManchurian", 2),
    ItemDef("Omelettetoken", 96, "ddlOmelette", 5),
    ItemDef("BoiledEggtoken", 94, "ddlBoiledEggtoken", 5),
    ItemDef("FullBoilEggtoken", 93, "ddlFullBoilEggtoken", 5),
    ItemDef("EggCurrytoken", 92, "ddlEggCurry", 5),
    ItemDef("EggGraveytoken", 87, "ddlEggGravey", 5),
    ItemDef("EggDosatoken", 95, "ddlEggDosa", 5),
    ItemDef("snackstoken", 84, "ddlSnacks", 1)
)

object TokenPageParser {

    /** Pure function over the StudentView HTML — testable offline with a saved page copy. */
    fun parse(html: String): List<TokenItem> {
        val doc = Ksoup.parse(html)
        val items = mutableListOf<TokenItem>()

        for (def in KNOWN_ITEMS) {
            val button = doc.getElementById(def.buttonId) ?: continue

            // The Buy button sits outside the card div as a sibling, so walk up from the button
            // until we reach an ancestor whose subtree contains the item's <select> — never assume
            // a fixed number of parent hops.
            var region: Element? = button.parent()
            while (region != null && region.select("select").isEmpty()) {
                region = region.parent()
            }
            val select = region?.select("select")?.firstOrNull() ?: continue

            val dates = select.select("option[value]")
                .map { it.attr("value").trim() }
                .filter { it.isNotEmpty() }

            val radios = region.select("input[type=radio]")
            val meals = radios.map { it.attr("value") }.filter { it.isNotBlank() }
            if (meals.isEmpty()) continue

            val defaultMeal = radios.firstOrNull { it.hasAttr("checked") }?.attr("value") ?: meals.first()

            val headings = region.select("h4")
            val name = headings.getOrNull(0)?.text()?.trim()?.takeIf { it.isNotEmpty() } ?: def.buttonId
            val price = cleanPrice(headings.getOrNull(1)?.text())

            items.add(
                TokenItem(
                    name = name,
                    buttonId = def.buttonId,
                    ptokenId = def.ptokenId,
                    maxQty = def.maxQty,
                    price = price,
                    dates = dates,
                    meals = meals,
                    defaultMeal = defaultMeal
                )
            )
        }

        return items
    }

    /** The page renders "<span>Per</span>₹ 40"; keep just "₹40". Falls back to the raw text. */
    internal fun cleanPrice(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val amount = Regex("""\d+(?:\.\d+)?""").find(raw)?.value ?: return raw.trim()
        return "₹$amount"
    }
}
