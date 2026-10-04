package com.example.bookyourtoken.ui.leave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookyourtoken.AppContainer
import com.example.bookyourtoken.data.HostelClient
import com.example.bookyourtoken.data.models.ApiResult
import com.example.bookyourtoken.data.models.LeaveRecord
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LeaveUiState {
    data object Loading : LeaveUiState
    data class Error(val message: String) : LeaveUiState
    data class Loaded(val records: List<LeaveRecord>, val isRefreshing: Boolean = false) : LeaveUiState
}

sealed interface LeaveCancelState {
    val record: LeaveRecord

    /** [sameDates] are other Applied leaves the portal might cancel instead (it matches by date only). */
    data class Confirm(override val record: LeaveRecord, val sameDates: List<LeaveRecord>) : LeaveCancelState
    data class Running(override val record: LeaveRecord) : LeaveCancelState

    /** [check] is what the refreshed history showed; null when it couldn't be re-read. */
    data class Done(
        override val record: LeaveRecord,
        val success: Boolean,
        val message: String,
        val check: CancelCheck?
    ) : LeaveCancelState
}

/** The Leave tab: history and cancelling. Nothing here runs except from a user's tap or opening the tab. */
class LeaveViewModel(container: AppContainer) : ViewModel() {

    private val credentialStore = container.credentials

    private val _uiState = MutableStateFlow<LeaveUiState>(LeaveUiState.Loading)
    val uiState: StateFlow<LeaveUiState> = _uiState.asStateFlow()

    private val _cancelState = MutableStateFlow<LeaveCancelState?>(null)
    val cancelState: StateFlow<LeaveCancelState?> = _cancelState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true || _cancelState.value is LeaveCancelState.Running) return
        val previous = _uiState.value as? LeaveUiState.Loaded
        _uiState.value = previous?.copy(isRefreshing = true) ?: LeaveUiState.Loading

        refreshJob = viewModelScope.launch {
            val (rollNo, password) = credentials() ?: run {
                _uiState.value = LeaveUiState.Error("No saved credentials. Sign in again from Settings.")
                return@launch
            }
            val history = HostelClient().use { client ->
                when (val login = client.login(rollNo, password)) {
                    is ApiResult.Failure -> login
                    is ApiResult.Success -> client.leaveHistory(rollNo)
                }
            }
            when (history) {
                is ApiResult.Failure -> _uiState.value = LeaveUiState.Error(history.message)
                is ApiResult.Success -> show(history.data)
            }
        }
    }

    fun requestCancel(record: LeaveRecord) {
        val loaded = _uiState.value as? LeaveUiState.Loaded ?: return
        if (!record.canCancel || _cancelState.value != null) return
        _cancelState.value = LeaveCancelState.Confirm(record, ambiguousCancels(record, loaded.records))
    }

    /** Closes the confirm or result dialog. A running cancel can't be dismissed. */
    fun dismissCancel() {
        if (_cancelState.value !is LeaveCancelState.Running) _cancelState.value = null
    }

    fun confirmCancel() {
        val record = (_cancelState.value as? LeaveCancelState.Confirm)?.record ?: return
        _cancelState.value = LeaveCancelState.Running(record)

        viewModelScope.launch {
            fun done(success: Boolean, message: String, check: CancelCheck?) {
                _cancelState.value = LeaveCancelState.Done(record, success, message, check)
            }

            val (rollNo, password) = credentials() ?: return@launch done(false, "No saved credentials.", null)

            HostelClient().use { client ->
                val login = client.login(rollNo, password)
                if (login is ApiResult.Failure) return@launch done(false, login.message, null)

                val before = when (val history = client.leaveHistory(rollNo)) {
                    is ApiResult.Failure -> return@launch done(false, history.message, null)
                    is ApiResult.Success -> history.data
                }
                // Re-check against the fresh history: the leave may have been decided or cancelled meanwhile.
                val current = before.firstOrNull { it.isSameLeaveAs(record) }
                if (current == null || current.status == "Cancelled") {
                    show(before)
                    return@launch done(false, "This leave is no longer in your history.", CancelCheck.Gone)
                }
                if (!current.canCancel) {
                    show(before)
                    return@launch done(false, "This leave is now ${current.status} and can't be cancelled.", CancelCheck.StillThere)
                }

                val result = client.cancelLeave(rollNo, current)

                // Always re-read the real state instead of assuming what changed.
                val after = (client.leaveHistory(rollNo) as? ApiResult.Success)?.data
                val check = after?.let { checkCancel(current, before, it) }

                when (result) {
                    is ApiResult.Success -> done(result.data.success, result.data.message, check)
                    is ApiResult.Failure -> when {
                        !result.isTimeout -> done(false, result.message, check)
                        check == CancelCheck.Gone -> done(true, "Leave cancelled (confirmed by re-checking)", check)
                        else -> done(
                            false,
                            "No response — couldn't confirm the cancel went through. Check your leave history before trying again.",
                            check
                        )
                    }
                }
                if (after != null) show(after)
            }
        }
    }

    private fun credentials(): Pair<String, String>? {
        val rollNo = credentialStore.rollNo()
        val password = credentialStore.password()
        return if (rollNo.isNullOrBlank() || password.isNullOrBlank()) null else rollNo to password
    }

    private fun show(records: List<LeaveRecord>) {
        _uiState.value = LeaveUiState.Loaded(sortLeaveHistory(records))
    }
}
