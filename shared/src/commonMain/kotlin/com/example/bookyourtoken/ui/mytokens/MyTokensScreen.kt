package com.example.bookyourtoken.ui.mytokens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bookyourtoken.data.models.BookedToken
import com.example.bookyourtoken.ui.common.AppIcons
import com.example.bookyourtoken.ui.common.IconBadge
import com.example.bookyourtoken.ui.common.prettyName
import com.example.bookyourtoken.ui.theme.successColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTokensScreen(
    onTokensChanged: () -> Unit,
    onOpenQr: () -> Unit,
    onUpcomingCount: (Int) -> Unit,
    viewModel: MyTokensViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cancelState by viewModel.cancelState.collectAsStateWithLifecycle()
    val tokensChanged by viewModel.tokensChanged.collectAsStateWithLifecycle()

    val upcoming = (uiState as? MyTokensUiState.Loaded)?.upcomingCount
    LaunchedEffect(upcoming) { upcoming?.let(onUpcomingCount) }

    LaunchedEffect(tokensChanged) {
        if (tokensChanged) {
            onTokensChanged()
            viewModel.consumeTokensChanged()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Booked tokens") },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh booked tokens")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                MyTokensUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is MyTokensUiState.Error -> ErrorContent(state.message, onRetry = viewModel::refresh)
                is MyTokensUiState.Loaded -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (state.groups.isEmpty()) {
                        EmptyContent()
                    } else {
                        TokenList(
                            groups = state.groups,
                            onCancel = { viewModel.requestCancel(it, bulk = false) },
                            onCancelAll = { viewModel.requestCancel(it, bulk = true) },
                            onShowQr = onOpenQr
                        )
                    }
                }
            }
        }
    }

    when (val cancel = cancelState) {
        is CancelUiState.Confirm -> ConfirmCancelDialog(cancel, onConfirm = viewModel::confirmCancel, onDismiss = viewModel::dismissCancel)
        is CancelUiState.Running -> CancellingDialog(cancel)
        is CancelUiState.Done -> CancelResultDialog(cancel, onDismiss = viewModel::dismissCancel)
        null -> Unit
    }
}

@Composable
private fun TokenList(
    groups: List<TokenGroup>,
    onCancel: (BookedToken) -> Unit,
    onCancelAll: (BookedToken) -> Unit,
    onShowQr: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        groups.forEach { group ->
            item(key = "header-${group.date}") {
                Text(
                    group.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 4.dp, top = 8.dp)
                        .semantics { heading() }
                )
            }
            // The portal can return several rows with the same name/date/meal, so key by position.
            itemsIndexed(group.tokens, key = { index, _ -> "${group.date}#$index" }) { _, token ->
                BookedTokenCard(
                    token,
                    onCancel = { onCancel(token) },
                    onCancelAll = { onCancelAll(token) },
                    onShowQr = onShowQr
                )
            }
        }
    }
}

@Composable
private fun BookedTokenCard(token: BookedToken, onCancel: () -> Unit, onCancelAll: () -> Unit, onShowQr: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    // Spoken with every button, so it's clear which token a Cancel or Show QR belongs to.
    val name = prettyName(token.tokenName ?: "Unknown item")
    val which = "$name, ${token.mealTime.orEmpty()}"

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                painter = rememberVectorPainter(AppIcons.Restaurant),
                containerColor = if (token.isUsed) scheme.surfaceVariant else scheme.primaryContainer,
                contentColor = if (token.isUsed) scheme.onSurfaceVariant else scheme.onPrimaryContainer
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${token.mealTime.orEmpty()} · Qty ${token.tokenQty ?: 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                when {
                    token.isUsed -> StatusLabel("Already used")
                    !token.canCancel -> StatusLabel("Can't be cancelled")
                }
                // There's one QR page per student, so every row's button opens the same screen.
                if (token.qrEnabled) {
                    FilledTonalButton(
                        onClick = onShowQr,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .semantics { contentDescription = "Show QR for $which" }
                    ) {
                        Icon(AppIcons.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Show QR", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            OutlinedButton(
                onClick = onCancel,
                enabled = token.canCancel,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error),
                border = BorderStroke(1.dp, if (token.canCancel) scheme.error else scheme.outlineVariant),
                modifier = Modifier.semantics { contentDescription = "Cancel one $which" }
            ) {
                Text("Cancel")
            }
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = token.canCancel) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options for $which")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Cancel all of this item") },
                        onClick = {
                            menuOpen = false
                            onCancelAll()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun EmptyContent() {
    // Scrollable so pull-to-refresh still works on the empty state.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            IconBadge(
                painter = rememberVectorPainter(AppIcons.Restaurant),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                size = 72.dp,
                iconSize = 34.dp
            )
            Spacer(Modifier.height(16.dp))
            Text("No tokens booked.", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
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
        Text("Couldn't load your tokens", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}

/** "BOILED EGG — Lunch, 29-09-2026, qty 1": names exactly what the portal will cancel. */
private fun describe(token: BookedToken) =
    "— ${token.mealTime.orEmpty()}, ${token.expireDate.orEmpty()}, qty ${token.tokenQty ?: 1}"

@Composable
private fun ConfirmCancelDialog(state: CancelUiState.Confirm, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val token = state.token
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(if (state.bulk) "Cancel all of this item?" else "Cancel this token?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    buildAnnotatedString {
                        append(if (state.bulk) "Cancel ALL of " else "Cancel ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(token.tokenName.orEmpty()) }
                        append(" ${describe(token)}?")
                    }
                )
                if (state.bulk) {
                    Text(
                        "This uses the portal's \"Cancel All\" action. It likely cancels the whole quantity at once, but that hasn't been verified for quantities above 1. The regular Cancel button removes one at a time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("This can't be undone.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(if (state.bulk) "Cancel all" else "Cancel token", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep it") } }
    )
}

@Composable
private fun CancellingDialog(state: CancelUiState.Running) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp) },
        title = { Text("Cancelling…") },
        text = { Text("${state.token.tokenName.orEmpty()} ${describe(state.token)}") },
        confirmButton = {}
    )
}

@Composable
private fun CancelResultDialog(state: CancelUiState.Done, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            if (state.success) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = successColor)
            else Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        },
        title = { Text(if (state.success) "Cancelled" else "Couldn't cancel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.message)
                // The single-vs-bulk difference is unverified; showing the real before/after makes it visible.
                Text(
                    quantityChangeText(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } }
    )
}

private fun quantityChangeText(state: CancelUiState.Done): String {
    val name = prettyName(state.token.tokenName ?: "Item")
    val meal = state.token.mealTime.orEmpty()
    return when (val after = state.quantityAfter) {
        null -> "$name · $meal: couldn't re-read your tokens. Pull down to refresh."
        0 -> "$name · $meal: quantity ${state.quantityBefore} → 0 (removed)"
        else -> "$name · $meal: quantity ${state.quantityBefore} → $after"
    }
}
