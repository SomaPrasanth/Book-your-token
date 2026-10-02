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
            BrandHeader(
                eyebrow = greeting(),
                title = "Tomorrow's tokens",
                subtitle = (uiState as? TokensUiState.Loaded)?.tomorrowLabel,
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh tomorrow's tokens")
                    }
                }
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
            item(key = "qr") { QrReadyCard(onClick = onOpenQr) }
        }

        if (state.bookedTomorrow.isNotEmpty()) {
            item(key = "booked") { BookedSummaryCard("Booked for tomorrow", state.bookedTomorrow, onManage = onManageBooked) }
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
                TokenItemRow(
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
private fun QrReadyCard(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                painter = rememberVectorPainter(AppIcons.QrCode),
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary,
                size = 52.dp,
                iconSize = 28.dp
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Show today's QR", style = MaterialTheme.typography.titleMedium)
                Text("Open it at the mess counter", style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** "Good morning" etc. by the hostel's clock, for the Book tab's header. */
private fun greeting(): String = when (DateUtils.localDateTime(Clock.System.now()).hour) {
    in 4..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
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
            FoodBadge("", size = 64.dp)
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

@Composable
private fun BookingBar(state: TokensUiState.Loaded, onBook: () -> Unit) {
    val selected = state.selected
    val total = estimatedTotal(selected)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 12.dp
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
            Button(
                onClick = onBook,
                enabled = selected.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
            ) {
                Text(
                    if (selected.isEmpty()) "Book" else "Book (${selected.size})",
                    style = MaterialTheme.typography.titleMedium
                )
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
