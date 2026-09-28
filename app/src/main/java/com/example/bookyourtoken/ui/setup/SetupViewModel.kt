package com.example.bookyourtoken.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookyourtoken.data.AppPreferences
import com.example.bookyourtoken.data.CredentialStore
import com.example.bookyourtoken.data.HostelClient
import com.example.bookyourtoken.data.models.ApiResult
import com.example.bookyourtoken.work.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SetupUiState(
    val rollNo: String = "",
    val password: String = "",
    val reminderHour: Int,
    val reminderMinute: Int,
    val isSigningIn: Boolean = false,
    val error: String? = null
) {
    val canSubmit: Boolean get() = rollNo.isNotBlank() && password.isNotBlank() && !isSigningIn
}

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialStore = CredentialStore(application)
    private val appPreferences = AppPreferences(application)

    private val _uiState = MutableStateFlow(
        SetupUiState(reminderHour = appPreferences.reminderHour, reminderMinute = appPreferences.reminderMinute)
    )
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun onRollNoChange(value: String) {
        // The portal only binds the session to the student when the roll number is uppercase.
        _uiState.value = _uiState.value.copy(rollNo = value.trim().uppercase(), error = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun onReminderTimeChange(hour: Int, minute: Int) {
        _uiState.value = _uiState.value.copy(reminderHour = hour, reminderMinute = minute)
    }

    fun signIn(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.value = state.copy(isSigningIn = true, error = null)
        viewModelScope.launch {
            when (val result = HostelClient().login(state.rollNo, state.password)) {
                is ApiResult.Success -> {
                    credentialStore.save(state.rollNo, state.password)
                    appPreferences.reminderHour = state.reminderHour
                    appPreferences.reminderMinute = state.reminderMinute
                    ReminderScheduler.schedule(getApplication())
                    _uiState.value = _uiState.value.copy(isSigningIn = false)
                    onSuccess()
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isSigningIn = false, error = result.message)
                }
            }
        }
    }
}
