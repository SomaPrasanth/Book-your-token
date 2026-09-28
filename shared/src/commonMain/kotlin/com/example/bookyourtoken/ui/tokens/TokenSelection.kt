package com.example.bookyourtoken.ui.tokens

import com.example.bookyourtoken.data.models.TokenItem

/** What the user has picked for one item. Presence in the map == the item is selected. */
data class Selection(val meal: String, val quantity: Int)

typealias Selections = Map<Int, Selection>

fun Selections.toggle(item: TokenItem): Selections =
    if (containsKey(item.ptokenId)) this - item.ptokenId
    else this + (item.ptokenId to Selection(meal = item.defaultMeal, quantity = 1))

fun Selections.withMeal(item: TokenItem, meal: String): Selections {
    val current = this[item.ptokenId] ?: return this
    if (meal !in item.meals) return this
    return this + (item.ptokenId to current.copy(meal = meal))
}

fun Selections.withQuantity(item: TokenItem, quantity: Int): Selections {
    val current = this[item.ptokenId] ?: return this
    return this + (item.ptokenId to current.copy(quantity = quantity.coerceIn(1, item.maxQty)))
}
