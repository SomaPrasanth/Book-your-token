package com.example.bookyourtoken.ui.qr

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bookyourtoken.data.DateUtils
import com.example.bookyourtoken.data.HostelClient
import com.example.bookyourtoken.data.QrTokenRow
import com.example.bookyourtoken.ui.common.AppIcons
import com.example.bookyourtoken.ui.common.BrandHeader
import com.example.bookyourtoken.ui.common.IconBadge
import com.example.bookyourtoken.ui.common.LocalPlatformActions
import com.example.bookyourtoken.ui.common.formatTime
import com.example.bookyourtoken.ui.common.prettyName
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScreen(viewModel: QrViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val platform = LocalPlatformActions.current
    val openInBrowser = { platform.openUrl(HostelClient.QR_PAGE_URL) }

    Scaffold(
        topBar = {
            BrandHeader(
                title = "Food token QR",
                subtitle = "Show this at the mess counter",
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = uiState !is QrUiState.Loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh QR")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is QrUiState.Loading -> LoadingContent(state, onShowOffline = viewModel::showOfflineCopy)
                QrUiState.NoneAvailable -> MessageContent(
                    icon = AppIcons.QrCode,
                    title = "No QR available yet",
                    message = "The portal hasn't enabled the QR for any of your tokens.",
                    onRetry = viewModel::refresh,
                    onOpenInBrowser = openInBrowser
                )
                QrUiState.NotGenerated -> MessageContent(
                    icon = Icons.Filled.Warning,
                    title = "The portal hasn't generated a QR yet",
                    message = "Your token is enabled, but the QR page came back without a code. Try again in a little while.",
                    onRetry = viewModel::refresh,
                    onOpenInBrowser = openInBrowser
                )
                is QrUiState.Error -> MessageContent(
                    icon = Icons.Filled.Warning,
                    title = "Couldn't load your QR",
                    message = state.message,
                    onRetry = viewModel::refresh,
                    onOpenInBrowser = openInBrowser,
                    isError = true
                )
                is QrUiState.Showing -> QrContent(state, onOpenInBrowser = openInBrowser)
            }
        }
    }
}

@Composable
private fun LoadingContent(state: QrUiState.Loading, onShowOffline: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(state.stage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.hasOfflineCopy) {
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onShowOffline) { Text("Weak signal? Show the saved copy") }
        }
    }
}

@Composable
private fun MessageContent(
    icon: ImageVector,
    title: String,
    message: String,
    onRetry: () -> Unit,
    onOpenInBrowser: () -> Unit,
    isError: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBadge(
            painter = rememberVectorPainter(icon),
            containerColor = if (isError) scheme.errorContainer else scheme.secondaryContainer,
            contentColor = if (isError) scheme.onErrorContainer else scheme.onSecondaryContainer,
            size = 72.dp,
            iconSize = 36.dp
        )
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Try again")
        }
        Spacer(Modifier.height(8.dp))
        OpenInBrowserButton(onOpenInBrowser)
    }
}

@Composable
private fun QrContent(state: QrUiState.Showing, onOpenInBrowser: () -> Unit) {
    KeepScreenBrightAndOn()
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.offline) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = scheme.tertiaryContainer,
                contentColor = scheme.onTertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Offline copy from ${timeLabel(state.fetchedAt)} — may be outdated",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Always black-on-white with a quiet zone, whatever the theme, so scanners read it first time.
        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Image(
                bitmap = state.image,
                contentDescription = "Food token QR code",
                filterQuality = FilterQuality.None,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(state.image.width.toFloat() / state.image.height.coerceAtLeast(1))
            )
        }

        if (state.isRefreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            "Fetched at ${timeLabel(state.fetchedAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )

        if (state.rows.isNotEmpty()) TokenTable(state.rows)

        OpenInBrowserButton(onOpenInBrowser)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TokenTable(rows: List<QrTokenRow>) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                "This QR covers",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { heading() }
            )
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(prettyName(row.tokenName), style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOf(row.mealTime, row.date).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    if (row.quantity.isNotBlank()) {
                        Text(
                            "× ${row.quantity}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { contentDescription = "quantity ${row.quantity}" }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenInBrowserButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Icon(AppIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Open in browser")
    }
}

/** "7:42 AM" today, otherwise with the day: "8:05 PM, Thursday, 1 Oct". */
private fun timeLabel(instant: Instant): String {
    val local = DateUtils.localDateTime(instant)
    val time = formatTime(local.hour, local.minute)
    return if (local.date == DateUtils.today()) time else "$time, ${DateUtils.friendlyLabel(local.date)}"
}
