package com.example.bookyourtoken.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bookyourtoken.data.HostelClient
import com.example.bookyourtoken.ui.common.AppIcons
import com.example.bookyourtoken.ui.common.LocalPlatformActions
import com.example.bookyourtoken.ui.common.SectionHeader
import com.example.bookyourtoken.ui.common.TimePickerDialog
import com.example.bookyourtoken.ui.common.formatTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val platform = LocalPlatformActions.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var timePicker by remember { mutableStateOf<TimePickerTarget?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }

    // Re-read on every resume: the user may have just toggled notifications in system settings.
    var notificationsEnabled by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { notificationsEnabled = platform.notificationsEnabled() }
    }

    val versionName = remember { platform.appVersion }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SectionHeader("Reminder")
            SettingsCard {
                SettingsRow(
                    title = "Daily reminder",
                    subtitle = "Tap to change",
                    onClickLabel = "change the reminder time",
                    leading = { Icon(AppIcons.Schedule, contentDescription = null) },
                    trailing = {
                        Text(
                            formatTime(state.reminderHour, state.reminderMinute),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = { timePicker = TimePickerTarget.Reminder }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsSwitchRow(
                    title = "Skip if already booked",
                    subtitle = if (state.remindersCheckInBackground) {
                        "No reminder on days you've already booked for tomorrow"
                    } else {
                        "No reminder once the app has seen tomorrow is booked"
                    },
                    leading = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                    checked = state.skipIfAlreadyBooked,
                    onCheckedChange = viewModel::setSkipIfAlreadyBooked
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRow(
                    title = "Check now",
                    subtitle = "Run the reminder check right away",
                    leading = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    onClick = {
                        viewModel.checkNow()
                        scope.launch { snackbarHostState.showSnackbar("Checking the portal — a notification will follow.") }
                    }
                )
            }

            // The QR-ready check has to ask the portal at a set time, which only Android can do.
            if (state.remindersCheckInBackground) {
                SectionHeader("Food QR")
                SettingsCard {
                    SettingsSwitchRow(
                        title = "QR ready notification",
                        subtitle = "One morning check — tells you when the portal has enabled your QR",
                        leading = { Icon(AppIcons.QrCode, contentDescription = null) },
                        checked = state.qrReadyEnabled,
                        onCheckedChange = viewModel::setQrReadyEnabled
                    )
                    if (state.qrReadyEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(
                            title = "Check at",
                            subtitle = "Tap to change",
                            onClickLabel = "change the QR check time",
                            leading = { Icon(AppIcons.Schedule, contentDescription = null) },
                            trailing = {
                                Text(
                                    formatTime(state.qrReadyHour, state.qrReadyMinute),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = { timePicker = TimePickerTarget.QrReady }
                        )
                    }
                }
            }

            SectionHeader("Notifications")
            SettingsCard {
                if (!notificationsEnabled) {
                    SettingsRow(
                        title = "Notifications are off",
                        subtitle = "Reminders can't be shown until you allow them",
                        leading = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { platform.openNotificationSettings() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
                SettingsRow(
                    title = "Notification settings",
                    subtitle = "Sound, vibration and pop-up style",
                    leading = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    trailing = { Icon(AppIcons.OpenInNew, contentDescription = null) },
                    onClick = { platform.openNotificationSettings() }
                )
            }

            SectionHeader("Account")
            SettingsCard {
                SettingsRow(
                    title = "Signed in as",
                    subtitle = state.rollNo ?: "—",
                    leading = { Icon(Icons.Filled.Person, contentDescription = null) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRow(
                    title = "Sign out",
                    subtitle = "Remove saved credentials from this device",
                    leading = {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { confirmSignOut = true }
                )
            }

            SectionHeader("About")
            SettingsCard {
                SettingsRow(
                    title = "Open hostel portal",
                    subtitle = "edviewx.psgtech.ac.in",
                    leading = { Icon(AppIcons.OpenInNew, contentDescription = null) },
                    onClick = { platform.openUrl(HostelClient.BOOKING_PAGE_URL) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRow(
                    title = "Version",
                    subtitle = versionName ?: "—",
                    leading = { Icon(Icons.Filled.Info, contentDescription = null) }
                )
            }

            Text(
                "Unofficial app. Not affiliated with PSG College of Technology.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            )
        }
    }

    when (timePicker) {
        TimePickerTarget.Reminder -> TimePickerDialog(
            initialHour = state.reminderHour,
            initialMinute = state.reminderMinute,
            onDismiss = { timePicker = null },
            onConfirm = { hour, minute ->
                viewModel.setReminderTime(hour, minute)
                timePicker = null
                scope.launch { snackbarHostState.showSnackbar("Reminder set for ${formatTime(hour, minute)} daily") }
            }
        )
        TimePickerTarget.QrReady -> TimePickerDialog(
            initialHour = state.qrReadyHour,
            initialMinute = state.qrReadyMinute,
            onDismiss = { timePicker = null },
            onConfirm = { hour, minute ->
                viewModel.setQrReadyTime(hour, minute)
                timePicker = null
                scope.launch { snackbarHostState.showSnackbar("QR check set for ${formatTime(hour, minute)} daily") }
            }
        )
        null -> Unit
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
            title = { Text("Sign out?") },
            text = { Text("Your saved roll number and password will be removed and daily reminders will stop until you sign in again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                    onSignedOut()
                }) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } }
        )
    }
}

private enum class TimePickerTarget { Reminder, QrReady }

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
        content = content
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    titleColor: Color = Color.Unspecified,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(title, color = titleColor) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = leading,
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) {
            Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
        } else {
            // Read-only rows ("Signed in as", "Version") are read out as one item.
            Modifier.semantics(mergeDescendants = true) {}
        }
    )
}

/** A row that is one switch: tapping anywhere toggles it, and it's announced as a single control. */
@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = leading,
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    )
}
