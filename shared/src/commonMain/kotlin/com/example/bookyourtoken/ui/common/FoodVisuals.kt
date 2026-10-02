package com.example.bookyourtoken.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.bookyourtoken.ui.theme.FoodBadgeDark
import com.example.bookyourtoken.ui.theme.FoodBadgeLight

/** A picture for each portal item, matched on its name ("BOILED EGG", "Chicken Gravy", …). */
fun foodEmoji(name: String): String {
    val n = name.uppercase()
    return when {
        "OMELET" in n -> "🍳"
        "DOSA" in n -> "🥞"
        "CURRY" in n || "GRAV" in n -> "🍛"
        "EGG" in n -> "🥚"
        "CHICKEN" in n -> "🍗"
        "MUSHROOM" in n -> "🍄"
        "PANEER" in n || "PANNEER" in n -> "🧀"
        "GOBI" in n -> "🌶️"
        "SNACK" in n -> "🥟"
        else -> "🍽️"
    }
}

/**
 * The item's picture in a tinted circle; with [selected], a check mark in the corner. Purely
 * decorative — screen readers skip it, the item's name is right next to it.
 */
@Composable
fun FoodBadge(name: String, size: Dp = 48.dp, selected: Boolean = false, dimmed: Boolean = false) {
    val emoji = foodEmoji(name)
    // One tint for every item: the emoji bring the colour, the circles stay in the app's palette.
    val tint = if (isSystemInDarkTheme()) FoodBadgeDark else FoodBadgeLight
    val emojiSize = with(LocalDensity.current) { (size * 0.5f).toSp() }
    Box(modifier = Modifier.size(size).clearAndSetSemantics {}) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (dimmed) MaterialTheme.colorScheme.surfaceVariant else tint),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = emojiSize, color = Color.Black.copy(alpha = if (dimmed) 0.45f else 1f))
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.42f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(size * 0.26f)
                )
            }
        }
    }
}
