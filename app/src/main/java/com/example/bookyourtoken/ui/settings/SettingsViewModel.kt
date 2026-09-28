package com.example.bookyourtoken.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.bookyourtoken.data.AppPreferences
import com.example.bookyourtoken.data.CredentialStore
import com.example.bookyourtoken.work.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val rollNo: String?,
    val reminderHour: Int,
    val reminderMinute: Int,
    val skipIfAlreadyBooked: Boolean
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val appPreferences = AppPreferences(application)
    private val credentialStore = CredentialStore(application)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            rollNo = credentialStore.rollNo(),
            reminderHour = appPreferences.reminderHour,
            reminderMinute = appPreferences.reminderMinute,
            skipIfAlreadyBooked = appPreferences.skipIfAlreadyBooked
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setReminderTime(hour: Int, minute: Int) {
        appPreferences.reminderHour = hour
        appPreferences.reminderMinute = minute
        ReminderScheduler.schedule(getApplication())
        _uiState.value = _uiState.value.copy(reminderHour = hour, reminderMinute = minute)
    }

    fun setSkipIfAlreadyBooked(value: Boolean) {
        appPreferences.skipIfAlreadyBooked = value
        _uiState.value = _uiState.value.copy(skipIfAlreadyBooked = value)
    }

    fun checkNow() {
        ReminderScheduler.runNow(getApplication())
    }

    fun signOut() {
        credentialStore.clear()
        ReminderScheduler.cancel(getApplication())
    }
}
