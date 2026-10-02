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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.bookyourtoken.data.HostelClient
import com.example.bookyourtoken.data.models.BookedToken
import com.example.bookyourtoken.data.models.TokenItem
import com.example.bookyourtoken.ui.booking.BookingProgressDialog
import com.example.bookyourtoken.ui.common.AppIcons
import com.example.bookyourtoken.ui.common.IconBadge
import com.example.bookyourtoken.ui.common.LocalPlatformActions
import com.example.bookyourtoken.ui.common.formatRupees
import com.example.bookyourtoken.ui.common.prettyName
import com.example.bookyourtoken.ui.common.priceValue
import com.example.bookyourtoken.ui.theme.onSuccessContainerColor
import com.example.bookyourtoken.ui.theme.successColor
import com.example.bookyourtoken.ui.theme.successContainerColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokensScreen(
    onOpenMyTokens: () -> Unit,
    onOpenQr: () -> Unit,
    onUpcomingCount: (Int) -> Unit,
    tokensChanged: Boolean,
    onTokensChangedHandled: () -> Unit,
    viewModel: TokensViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bookingState by viewModel.bookingState.collectAsStateWithLifecycle()
    val showConfirm by viewModel.showConfirmDialog.collectAsStateWithLifecycle()
    val platform = LocalPlatformActions.current
    val openPortal = { platform.openUrl(HostelClient.BOOKING_PAGE_URL) }

    val upcoming = (uiState as? TokensUiState.Loaded)?.upcomingCount
    LaunchedEffect(upcoming) { upcoming?.let(onUpcomingCount) }

    // Something was cancelled on My Tokens: reload so "Already booked" is accurate.
    LaunchedEffect(tokensChanged) {
        if (tokensChanged) {
            viewModel.refresh()
            onTokensChangedHandled()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tomorrow's tokens")
                        (uiState as? TokensUiState.Loaded)?.let {
                            Text(
                                it.tomorrowLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh tomorrow's tokens")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            (uiState as? TokensUiState.Loaded)?.takeIf { it.items.isNotEmpty() }?.let {
                BookingBar(state = it, onBook = viewModel::requestConfirm)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is TokensUiState.Loading -> LoadingContent(state.stage)
                is TokensUiState.Error -> ErrorContent(state.message, onRetry = viewModel::refresh, onOpenPortal = openPortal)
                is TokensUiState.Loaded -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LoadedContent(
                        state = state,
                        onToggle = viewModel::toggle,
                        onMeal = viewModel::setMeal,
                        onQuantity = viewModel::setQuantity,
                        onOpenPortal = openPortal,
                        onManageBooked = onOpenMyTokens,
                        onOpenQr = onOpenQr
                    )
                }
            }
        }
    }

    val loaded = uiState as? TokensUiState.Loaded
    if (showConfirm && loaded != null) {
        ConfirmBookingDialog(state = loaded, onConfirm = viewModel::confirmBooking, onDismiss = viewModel::dismissConfirm)
    }

    bookingState?.let { BookingProgressDialog(state = it, onDismiss = viewModel::dismissBooking) }
}

@Composable
private fun LoadingContent(stage: String) {
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
private fun ErrorContent(message: String, onRetry: () -> Unit, onOpenPortal: () -> Unit) {
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
private fun OpenPortalButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Icon(AppIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Open portal in browser")
    }
}

@Composable
private fun LoadedContent(
    state: TokensUiState.Loaded,
    onToggle: (TokenItem) -> Unit,
    onMeal: (TokenItem, String) -> Unit,
    onQuantity: (TokenItem, Int) -> Unit,
    onOpenPortal: () -> Unit,
    onManageBooked: () -> Unit,
    onOpenQr: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.qrAvailable) {
            item(key = "qr") {
                Button(
                    onClick = onOpenQr,
                    contentPadding = PaddingValues(vertical = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(AppIcons.QrCode, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Show today's QR", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        if (state.bookedTomorrow.isNotEmpty()) {
            item(key = "booked") { BookedSummaryCard(state.bookedTomorrow, onManage = onManageBooked) }
        }

        if (state.items.isEmpty()) {
            item(key = "empty") { EmptyMenuCard(state.tomorrowLabel, onOpenPortal) }
        } else {
            item(key = "header") {
                Text(
                    "Available tomorrow · ${state.items.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 4.dp, top = 8.dp)
                        .semantics { heading() }
                )
            }
            items(state.items, key = { it.ptokenId }) { item ->
                TokenCard(
                    item = item,
                    selection = state.selections[item.ptokenId],
                    booked = state.bookedFor(item),
                    onToggle = { onToggle(item) },
                    onMeal = { onMeal(item, it) },
                    onQuantity = { onQuantity(item, it) }
                )
            }
        }
    }
}

@Composable
private fun BookedSummaryCard(booked: List<BookedToken>, onManage: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
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
                    "Booked for tomorrow",
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

@Composable
private fun EmptyMenuCard(dateLabel: String, onOpenPortal: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconBadge(
                painter = rememberVectorPainter(AppIcons.Restaurant),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                size = 64.dp,
                iconSize = 30.dp
            )
            Spacer(Modifier.height(16.dp))
            Text("Nothing on offer yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "No tokens are listed for $dateLabel. Pull down to check again later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            OpenPortalButton(onOpenPortal)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokenCard(
    item: TokenItem,
    selection: Selection?,
    booked: List<BookedToken>,
    onToggle: () -> Unit,
    onMeal: (String) -> Unit,
    onQuantity: (Int) -> Unit
) {
    val selected = selection != null
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onToggle,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) scheme.surfaceContainerHigh else scheme.surfaceContainerLow
        ),
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
                IconBadge(
                    painter = if (selected) rememberVectorPainter(Icons.Filled.Check) else rememberVectorPainter(AppIcons.Restaurant),
                    containerColor = if (selected) scheme.primary else scheme.primaryContainer,
                    contentColor = if (selected) scheme.onPrimary else scheme.onPrimaryContainer
                )
                Spacer(Modifier.width(12.dp))
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
                        SegmentedButton(
                            selected = selection.meal == meal,
                            onClick = { onMeal(meal) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = item.meals.size)
                        ) {
                            Text(meal)
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
private fun estimatedTotal(selected: List<Pair<TokenItem, Selection>>): Int? {
    if (selected.isEmpty()) return null
    val parts = selected.map { (item, sel) -> priceValue(item.price)?.times(sel.quantity) }
    return if (parts.any { it == null }) null else parts.sumOf { it!! }
}

@Composable
private fun BookingBar(state: TokensUiState.Loaded, onBook: () -> Unit) {
    val selected = state.selected
    val total = estimatedTotal(selected)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            ) {
                if (selected.isEmpty()) {
                    Text(
                        "Tap an item to select it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        if (selected.size == 1) "1 item selected" else "${selected.size} items selected",
                        style = MaterialTheme.typography.titleSmall
                    )
                    total?.let {
                        Text(
                            "Estimated ${formatRupees(it)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Button(onClick = onBook, enabled = selected.isNotEmpty()) {
                Text(if (selected.isEmpty()) "Book" else "Book (${selected.size})")
            }
        }
    }
}

@Composable
private fun ConfirmBookingDialog(state: TokensUiState.Loaded, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val selected = state.selected
    val total = estimatedTotal(selected)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Restaurant, contentDescription = null) },
        title = { Text("Confirm booking") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "For ${state.tomorrowLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                selected.forEach { (item, sel) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(prettyName(item.name), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${sel.meal} × ${sel.quantity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        priceValue(item.price)?.let {
                            Text(formatRupees(it * sel.quantity), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (total != null) {
                    HorizontalDivider()
                    Row {
                        Text("Estimated total", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(formatRupees(total), style = MaterialTheme.typography.titleSmall)
                    }
                }
                Text(
                    "This books real tokens on the hostel portal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Book now") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
