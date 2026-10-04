package com.example.bookyourtoken.ui.leave

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bookyourtoken.data.models.LeaveRecord
import com.example.bookyourtoken.ui.common.AppIcons
import com.example.bookyourtoken.ui.common.BrandHeader
import com.example.bookyourtoken.ui.common.IconBadge
import com.example.bookyourtoken.ui.theme.infoContainerColor
import com.example.bookyourtoken.ui.theme.onInfoContainerColor
import com.example.bookyourtoken.ui.theme.onSuccessContainerColor
import com.example.bookyourtoken.ui.theme.successColor
import com.example.bookyourtoken.ui.theme.successContainerColor

/**
 * Leave history, cancelling, and the way into the apply form. [historyChanged] is raised when the
 * apply form sent something, so the history reloads on the way back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveScreen(
    onApply: () -> Unit,
    historyChanged: Boolean,
    onHistoryChangedHandled: () -> Unit,
    viewModel: LeaveViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cancelState by viewModel.cancelState.collectAsStateWithLifecycle()

    LaunchedEffect(historyChanged) {
        if (historyChanged) {
            viewModel.refresh()
            onHistoryChangedHandled()
        }
    }

    Scaffold(
        topBar = {
            BrandHeader(
                title = "Hostel leave",
                subtitle = "Apply, track and cancel",
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh leave history")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onApply,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Apply for leave") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                LeaveUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is LeaveUiState.Error -> ErrorContent(state.message, onRetry = viewModel::refresh)
                is LeaveUiState.Loaded -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (state.records.isEmpty()) {
                        EmptyContent()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            // Bottom room so the last row's Cancel isn't hidden under the button.
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(state.records, key = { index, r -> "${r.fromRaw}|${r.toRaw}#$index" }) { _, record ->
                                LeaveCard(record, onCancel = { viewModel.requestCancel(record) })
                            }
                        }
                    }
                }
            }
        }
    }

    when (val cancel = cancelState) {
        is LeaveCancelState.Confirm -> ConfirmCancelDialog(cancel, onConfirm = viewModel::confirmCancel, onDismiss = viewModel::dismissCancel)
        is LeaveCancelState.Running -> CancellingDialog(cancel.record)
        is LeaveCancelState.Done -> CancelResultDialog(cancel, onDismiss = viewModel::dismissCancel)
        null -> Unit
    }
}

private fun LeaveRecord.fromLabel() = leaveDateTimeLabel(from, fromRaw)

private fun LeaveRecord.toLabel() = leaveDateTimeLabel(to, toRaw)

@Composable
private fun LeaveCard(record: LeaveRecord, onCancel: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    record.type.ifBlank { "Leave" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(record.status)
            }
            Spacer(Modifier.height(8.dp))
            DateLine("From", record.fromLabel())
            DateLine("To", record.toLabel())
            if (record.reason.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    record.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
            if (record.canCancel) {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error),
                    border = BorderStroke(1.dp, scheme.error),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                        .semantics { contentDescription = "Cancel leave from ${record.fromLabel()} to ${record.toLabel()}" }
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun DateLine(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 8.dp)
                .widthIn(min = 44.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusChip(status: String) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        "Applied" -> infoContainerColor to onInfoContainerColor
        "Approved" -> successContainerColor to onSuccessContainerColor
        "Rejected" -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    StatusPill(status.ifBlank { "Unknown" }, container, content)
}

@Composable
private fun StatusPill(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
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
                painter = rememberVectorPainter(AppIcons.Luggage),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                size = 72.dp,
                iconSize = 36.dp
            )
            Spacer(Modifier.height(16.dp))
            Text("No leave history.", style = MaterialTheme.typography.titleMedium)
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
        Text("Couldn't load your leave history", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun ConfirmCancelDialog(state: LeaveCancelState.Confirm, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val record = state.record
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Cancel this leave?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cancel your leave ${record.fromLabel()} → ${record.toLabel()}?")
                if (state.sameDates.isNotEmpty()) {
                    Text(
                        buildString {
                            append("You have another Applied leave on the same dates (")
                            append(state.sameDates.joinToString("; ") { "${it.fromLabel()} → ${it.toLabel()}" })
                            append("). The portal matches leaves by date only, so it may cancel either one.")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Cancel leave", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep it") } }
    )
}

@Composable
private fun CancellingDialog(record: LeaveRecord) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp) },
        title = { Text("Cancelling…") },
        text = { Text("${record.fromLabel()} → ${record.toLabel()}") },
        confirmButton = {}
    )
}

@Composable
private fun CancelResultDialog(state: LeaveCancelState.Done, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            if (state.success) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = successColor)
            else Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        },
        title = { Text(if (state.success) "Leave cancelled" else "Couldn't cancel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.message)
                Text(
                    when (val check = state.check) {
                        null -> "Couldn't re-read your leave history. Pull down to refresh."
                        CancelCheck.Gone -> "The leave is no longer in your history."
                        CancelCheck.StillThere -> "The leave is still in your history."
                        is CancelCheck.OtherGone ->
                            "The portal removed a different leave on the same dates: ${check.other.fromLabel()} → ${check.other.toLabel()}. This one is still in your history."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } }
    )
}
