package com.example.bookyourtoken.ui.settings

import androidx.lifecycle.ViewModel
import com.example.bookyourtoken.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val rollNo: String?,
    val reminderHour: Int,
    val reminderMinute: Int,
    val skipIfAlreadyBooked: Boolean,
    val remindersCheckInBackground: Boolean
)

class SettingsViewModel(container: AppContainer) : ViewModel() {

    private val appPreferences = container.preferences
    private val credentialStore = container.credentials
    private val reminders = container.reminders

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            rollNo = credentialStore.rollNo(),
            reminderHour = appPreferences.reminderHour,
            reminderMinute = appPreferences.reminderMinute,
            skipIfAlreadyBooked = appPreferences.skipIfAlreadyBooked,
            remindersCheckInBackground = reminders.checksInBackground
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

    fun checkNow() {
        reminders.checkNow()
    }

    fun signOut() {
        credentialStore.clear()
        reminders.cancel()
    }
}
