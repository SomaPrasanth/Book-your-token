package com.example.bookyourtoken.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bookyourtoken.AppUpdater
import com.example.bookyourtoken.data.UpdateDialogState
import com.example.bookyourtoken.ui.common.AppIcons
import com.example.bookyourtoken.ui.common.LocalPlatformActions

/** Shown over every screen while [AppUpdater.dialog] has something to say. */
@Composable
fun UpdateDialogHost(updater: AppUpdater) {
    val state by updater.dialog.collectAsStateWithLifecycle()
    state?.let { UpdateDialog(it, updater) }
}

@Composable
private fun UpdateDialog(state: UpdateDialogState, updater: AppUpdater) {
    val release = state.release
    val platform = LocalPlatformActions.current
    // A required update can't be put off, and a running download only stops through Cancel.
    val dismissible = !release.required && state !is UpdateDialogState.Downloading
    val later: @Composable (() -> Unit)? = if (dismissible) {
        { TextButton(onClick = updater::later) { Text("Later") } }
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = { if (dismissible) updater.later() },
        properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible),
        icon = {
            if (state is UpdateDialogState.InvalidFile) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            } else {
                Icon(AppIcons.SystemUpdate, contentDescription = null)
            }
        },
        title = {
            Text(
                when (state) {
                    is UpdateDialogState.Available -> "Update available — ${release.versionName}"
                    is UpdateDialogState.Downloading -> "Downloading ${release.versionName}"
                    is UpdateDialogState.DownloadFailed -> "Download failed"
                    is UpdateDialogState.InvalidFile -> "Can't install this update"
                    is UpdateDialogState.NeedsPermission -> "Allow installing updates"
                    is UpdateDialogState.ReadyToInstall -> "Install ${release.versionName}"
                }
            )
        },
        text = {
            when (state) {
                is UpdateDialogState.Available -> ReleaseNotes(state)
                is UpdateDialogState.Downloading -> DownloadProgress(state.progress)
                is UpdateDialogState.DownloadFailed -> Text("Download failed. Check your connection and try again.")
                is UpdateDialogState.InvalidFile -> Text("The downloaded file isn't a valid update.")
                is UpdateDialogState.NeedsPermission -> Text(
                    if (state.denied) {
                        "Updates need this permission."
                    } else {
                        "To install updates, allow this app to install apps. You only need to do this once."
                    }
                )
                is UpdateDialogState.ReadyToInstall ->
                    Text("Finish on Android's install screen. If you closed it, tap Install to open it again.")
            }
        },
        confirmButton = {
            when (state) {
                is UpdateDialogState.Available ->
                    TextButton(onClick = updater::startDownload) { Text("Update") }
                is UpdateDialogState.Downloading ->
                    TextButton(onClick = updater::cancelDownload) { Text("Cancel") }
                is UpdateDialogState.DownloadFailed ->
                    TextButton(onClick = updater::startDownload) { Text("Retry") }
                is UpdateDialogState.InvalidFile ->
                    TextButton(onClick = { platform.openUrl(updater.releasesPageUrl) }) { Text("Open releases page") }
                is UpdateDialogState.NeedsPermission ->
                    TextButton(onClick = updater::requestInstallPermission) {
                        Text(if (state.denied) "Try again" else "Continue")
                    }
                is UpdateDialogState.ReadyToInstall ->
                    TextButton(onClick = updater::install) { Text("Install") }
            }
        },
        dismissButton = later
    )
}

@Composable
private fun ReleaseNotes(state: UpdateDialogState.Available) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (state.release.required) {
            Text(
                "This update is required to keep using the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(state.release.notes.ifEmpty { "A new version is ready to install." })
    }
}

@Composable
private fun DownloadProgress(progress: Float?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (progress == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        }
    }
}
