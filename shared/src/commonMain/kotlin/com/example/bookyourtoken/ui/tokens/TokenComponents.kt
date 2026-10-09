package com.example.bookyourtoken.ui.tokens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bookyourtoken.data.DateUtils
import com.example.bookyourtoken.data.HostelClient
import com.example.bookyourtoken.data.models.BookedToken
import com.example.bookyourtoken.data.models.TokenItem
import com.example.bookyourtoken.ui.booking.BookingProgressDialog
import com.example.bookyourtoken.ui.common.AppIcons
import com.example.bookyourtoken.ui.common.BrandHeader
import com.example.bookyourtoken.ui.common.FoodBadge
import com.example.bookyourtoken.ui.common.IconBadge
import com.example.bookyourtoken.ui.common.LocalPlatformActions
import com.example.bookyourtoken.ui.common.formatRupees
import com.example.bookyourtoken.ui.common.prettyName
import com.example.bookyourtoken.ui.common.priceValue
import com.example.bookyourtoken.ui.theme.onSuccessContainerColor
import com.example.bookyourtoken.ui.theme.successColor
import com.example.bookyourtoken.ui.theme.successContainerColor
import kotlin.time.Clock

// Building blocks shared by the Tomorrow and Book ahead screens, so both look and behave the same.

@Composable
internal fun LoadingContent(stage: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(stage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ErrorContent(message: String, onRetry: () -> Unit, onOpenPortal: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBadge(
            painter = rememberVectorPainter(Icons.Filled.Warning),
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            size = 72.dp,
            iconSize = 36.dp
        )
        Spacer(Modifier.height(20.dp))
        Text("Couldn't load tokens", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Try again")
        }
        Spacer(Modifier.height(8.dp))
        OpenPortalButton(onOpenPortal)
    }
}

@Composable
internal fun OpenPortalButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Icon(AppIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Open portal in browser")
    }
}

@Composable
internal fun BookedSummaryCard(title: String, booked: List<BookedToken>, onManage: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = successContainerColor,
            contentColor = onSuccessContainerColor
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                TextButton(
                    onClick = onManage,
                    colors = ButtonDefaults.textButtonColors(contentColor = onSuccessContainerColor)
                ) {
                    Text("View booked")
                }
            }
            booked.forEach {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(prettyName(it.tokenName ?: "Item"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${it.mealTime.orEmpty()} × ${it.tokenQty ?: 1}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TokenItemRow(
    item: TokenItem,
    selection: Selection?,
    booked: List<BookedToken>,
    onToggle: () -> Unit,
    onMeal: (String) -> Unit,
    onQuantity: (Int) -> Unit,
    /** Meals shown dimmed as "Likely closed" — a hint only, they can still be picked and booked. */
    likelyClosedMeals: Set<String> = emptySet()
) {
    val selected = selection != null
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onToggle,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) scheme.surfaceContainerHigh else scheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp),
        border = if (selected) BorderStroke(2.dp, scheme.primary) else BorderStroke(1.dp, scheme.outlineVariant),
        // The card is one control for screen readers: "Boiled Egg, Lunch · Dinner, ₹12, not checked".
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Checkbox
                toggleableState = ToggleableState(selected)
            }
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FoodBadge(item.name, selected = selected)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        prettyName(item.name),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.meals.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    if (booked.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = successColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Booked · " + booked.joinToString { "${it.mealTime.orEmpty()} × ${it.tokenQty ?: 1}" },
                                style = MaterialTheme.typography.labelMedium,
                                color = successColor
                            )
                        }
                    }
                }
                item.price?.let { PriceTag(it) }
            }

            if (selection != null) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = scheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Meal",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    item.meals.forEachIndexed { index, meal ->
                        val mealSelected = selection.meal == meal
                        val closed = meal in likelyClosedMeals
                        SegmentedButton(
                            selected = mealSelected,
                            onClick = { onMeal(meal) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = item.meals.size)
                        ) {
                            if (closed) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.alpha(if (mealSelected) 1f else 0.55f)
                                ) {
                                    Text(meal)
                                    Text("Likely closed", style = MaterialTheme.typography.labelSmall, color = scheme.error)
                                }
                            } else {
                                Text(meal)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Quantity", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                        Text("Up to ${item.maxQty}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    QuantityStepper(
                        itemName = prettyName(item.name),
                        quantity = selection.quantity,
                        max = item.maxQty,
                        onChange = onQuantity
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceTag(price: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(price, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun QuantityStepper(itemName: String, quantity: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onChange(quantity - 1) }, enabled = quantity > 1) {
            Icon(AppIcons.Remove, contentDescription = "Fewer $itemName")
        }
        Text(
            "$quantity",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 40.dp)
                // Announced whenever it changes, so screen-reader users hear the new quantity.
                .semantics {
                    contentDescription = "Quantity $quantity of $max"
                    liveRegion = LiveRegionMode.Polite
                }
        )
        FilledTonalIconButton(onClick = { onChange(quantity + 1) }, enabled = quantity < max) {
            Icon(Icons.Filled.Add, contentDescription = "More $itemName")
        }
    }
}

/** Sum of price × quantity, or null if any selected item has no usable price. */
internal fun estimatedTotal(selected: List<Pair<TokenItem, Selection>>): Int? {
    if (selected.isEmpty()) return null
    val parts = selected.map { (item, sel) -> priceValue(item.price)?.times(sel.quantity) }
    return if (parts.any { it == null }) null else parts.sumOf { it!! }
}
