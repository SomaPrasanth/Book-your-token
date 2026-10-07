package com.example.bookyourtoken.ui.settings

import androidx.lifecycle.ViewModel
import com.example.bookyourtoken.AppContainer
import com.example.bookyourtoken.AppUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val rollNo: String?,
    val reminderHour: Int,
    val reminderMinute: Int,
    val skipIfAlreadyBooked: Boolean,
    val remindersCheckInBackground: Boolean,
    val qrReadyEnabled: Boolean,
    val qrReadyHour: Int,
    val qrReadyMinute: Int
)

class SettingsViewModel(container: AppContainer) : ViewModel() {

    private val appPreferences = container.preferences
    private val credentialStore = container.credentials
    private val reminders = container.reminders
    private val qrStore = container.qr

    /** Null on iOS, where the Settings update rows are hidden. */
    val updater: AppUpdater? = container.updater

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            rollNo = credentialStore.rollNo(),
            reminderHour = appPreferences.reminderHour,
            reminderMinute = appPreferences.reminderMinute,
            skipIfAlreadyBooked = appPreferences.skipIfAlreadyBooked,
            remindersCheckInBackground = reminders.checksInBackground,
            qrReadyEnabled = appPreferences.qrReadyEnabled,
            qrReadyHour = appPreferences.qrReadyHour,
            qrReadyMinute = appPreferences.qrReadyMinute
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setReminderTime(hour: Int, minute: Int) {
        appPreferences.reminderHour = hour
        appPreferences.reminderMinute = minute
        reminders.schedule()
        _uiState.value = _uiState.value.copy(reminderHour = hour, reminderMinute = minute)
    }

    fun setSkipIfAlreadyBooked(value: Boolean) {
        appPreferences.skipIfAlreadyBooked = value
        // iOS bakes skipped days into its pending notifications, so re-plan them.
        reminders.schedule()
        _uiState.value = _uiState.value.copy(skipIfAlreadyBooked = value)
    }

    fun setQrReadyEnabled(value: Boolean) {
        appPreferences.qrReadyEnabled = value
        reminders.scheduleQrReadyCheck()
        _uiState.value = _uiState.value.copy(qrReadyEnabled = value)
    }

    fun setQrReadyTime(hour: Int, minute: Int) {
        appPreferences.qrReadyHour = hour
        appPreferences.qrReadyMinute = minute
        reminders.scheduleQrReadyCheck()
        _uiState.value = _uiState.value.copy(qrReadyHour = hour, qrReadyMinute = minute)
    }

    fun checkNow() {
        reminders.checkNow()
    }

    fun signOut() {
        credentialStore.clear()
        reminders.cancel()
        qrStore.clear()
    }
}
