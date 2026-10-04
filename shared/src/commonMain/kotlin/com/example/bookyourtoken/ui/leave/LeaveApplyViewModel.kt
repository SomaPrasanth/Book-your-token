package com.example.bookyourtoken.ui.leave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookyourtoken.AppContainer
import com.example.bookyourtoken.data.DateUtils
import com.example.bookyourtoken.data.HostelClient
import com.example.bookyourtoken.data.LeaveFormat
import com.example.bookyourtoken.data.models.ApiResult
import com.example.bookyourtoken.data.models.Approver
import com.example.bookyourtoken.data.models.LeaveType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

sealed interface LeaveOptionsState {
    data object Loading : LeaveOptionsState
    data class Error(val message: String) : LeaveOptionsState
    data class Ready(val types: List<LeaveType>, val approvers: List<Approver>) : LeaveOptionsState
}

/** Everything the confirm dialog shows — exactly what will be sent. */
data class LeaveRequest(
    val from: LocalDateTime,
    val to: LocalDateTime,
    val type: LeaveType,
    val approver: Approver,
    val reason: String
)

sealed interface ApplyState {
    val request: LeaveRequest

    data class Confirm(override val request: LeaveRequest) : ApplyState
    data class Running(override val request: LeaveRequest) : ApplyState

    /** [sent] is true when a request reached (or may have reached) the portal, so the history should reload. */
    data class Done(
        override val request: LeaveRequest,
        val success: Boolean,
        val message: String,
        val sent: Boolean
    ) : ApplyState
}

/** The apply form. Types and staff come from the portal each time it opens — never hardcoded. */
class LeaveApplyViewModel(container: AppContainer) : ViewModel() {

    private val credentialStore = container.credentials
    private val preferences = container.preferences

    private val _options = MutableStateFlow<LeaveOptionsState>(LeaveOptionsState.Loading)
    val options: StateFlow<LeaveOptionsState> = _options.asStateFlow()

    private val _form = MutableStateFlow(LeaveForm())
    val form: StateFlow<LeaveForm> = _form.asStateFlow()

    private val _applyState = MutableStateFlow<ApplyState?>(null)
    val applyState: StateFlow<ApplyState?> = _applyState.asStateFlow()

    init {
        loadOptions()
    }

    fun loadOptions() {
        if (_options.value is LeaveOptionsState.Ready) return
        _options.value = LeaveOptionsState.Loading
        viewModelScope.launch {
            val (rollNo, password) = credentials() ?: run {
                _options.value = LeaveOptionsState.Error("No saved credentials. Sign in again from Settings.")
                return@launch
            }
            val loaded = HostelClient().use { client ->
                val login = client.login(rollNo, password)
                if (login is ApiResult.Failure) return@use login
                val types = when (val r = client.leaveTypes(rollNo)) {
                    is ApiResult.Failure -> return@use r
                    is ApiResult.Success -> r.data
                }
                when (val r = client.approvers(rollNo)) {
                    is ApiResult.Failure -> r
                    is ApiResult.Success -> ApiResult.Success(LeaveOptionsState.Ready(types, r.data))
                }
            }
            when (loaded) {
                is ApiResult.Failure -> _options.value = LeaveOptionsState.Error(loaded.message)
                is ApiResult.Success -> {
                    val ready = loaded.data
                    _options.value = when {
                        ready.types.isEmpty() -> LeaveOptionsState.Error("The portal didn't list any leave types.")
                        ready.approvers.isEmpty() -> LeaveOptionsState.Error("The portal didn't list any approving staff.")
                        else -> ready
                    }
                    _form.update { form ->
                        form.copy(
                            typeId = form.typeId ?: ready.types.singleOrNull()?.id,
                            staffId = form.staffId
                                ?: preferences.lastLeaveApprover?.takeIf { id -> ready.approvers.any { it.staffId == id } }
                        )
                    }
                }
            }
        }
    }

    fun setType(id: String) = _form.update { it.copy(typeId = id) }

    fun setApprover(id: String) = _form.update { it.copy(staffId = id) }

    /** Moving From past To pushes To's date along, so the pair never starts out backwards. */
    fun setFromDate(date: LocalDate) = _form.update {
        it.copy(fromDate = date, toDate = it.toDate?.takeIf { to -> to >= date })
    }

    fun setFromTime(time: LocalTime) = _form.update { it.copy(fromTime = LeaveFormat.roundDownTo5Minutes(time)) }

    fun setToDate(date: LocalDate) = _form.update { it.copy(toDate = date) }

    fun setToTime(time: LocalTime) = _form.update { it.copy(toTime = LeaveFormat.roundDownTo5Minutes(time)) }

    fun setReason(text: String) = _form.update { it.copy(reason = LeaveFormat.sanitizeReason(text)) }

    /** Opens the confirm dialog. Nothing is sent until [confirmApply]. */
    fun requestApply() {
        if (_applyState.value != null) return
        _applyState.value = ApplyState.Confirm(currentRequest() ?: return)
    }

    /** Closes the confirm or result dialog. A running request can't be dismissed. */
    fun dismissApply() {
        if (_applyState.value !is ApplyState.Running) _applyState.value = null
    }

    fun confirmApply() {
        val confirm = _applyState.value as? ApplyState.Confirm ?: return
        // Time may have passed while the dialog was open; check again against what's on screen now.
        val request = currentRequest()?.takeIf { it == confirm.request } ?: run {
            _applyState.value = ApplyState.Done(confirm.request, false, "From is now in the past. Pick a new time.", sent = false)
            return
        }
        _applyState.value = ApplyState.Running(request)

        viewModelScope.launch {
            fun done(success: Boolean, message: String, sent: Boolean) {
                _applyState.value = ApplyState.Done(request, success, message, sent)
            }

            val (rollNo, password) = credentials() ?: return@launch done(false, "No saved credentials.", false)

            // A fresh session: the form may have been open for longer than the portal's 10-minute login.
            HostelClient().use { client ->
                val login = client.login(rollNo, password)
                if (login is ApiResult.Failure) return@launch done(false, login.message, false)

                val before = when (val history = client.leaveHistory(rollNo)) {
                    is ApiResult.Failure -> return@launch done(false, history.message, false)
                    is ApiResult.Success -> countMatching(history.data, request.from, request.to)
                }

                preferences.lastLeaveApprover = request.approver.staffId
                val result = client.applyLeave(
                    rollNo, request.from, request.to, request.type.id, request.reason, request.approver.staffId
                )
                when (result) {
                    is ApiResult.Success -> done(result.data.success, result.data.message, true)
                    is ApiResult.Failure -> if (!result.isTimeout) {
                        done(false, result.message, false)
                    } else {
                        // Never resend: look for the leave in the history instead.
                        val after = (client.leaveHistory(rollNo) as? ApiResult.Success)
                            ?.data?.let { countMatching(it, request.from, request.to) }
                        if (after != null && after > before) {
                            done(true, "Leave applied (confirmed by re-checking)", true)
                        } else {
                            done(
                                false,
                                "No response — couldn't confirm the leave was applied. Check your leave history before trying again.",
                                true
                            )
                        }
                    }
                }
            }
        }
    }

    /** The request the form describes, or null if it isn't complete and valid. */
    private fun currentRequest(): LeaveRequest? {
        val ready = _options.value as? LeaveOptionsState.Ready ?: return null
        val form = _form.value
        if (!isComplete(form, DateUtils.now())) return null
        return LeaveRequest(
            from = form.from ?: return null,
            to = form.to ?: return null,
            type = ready.types.firstOrNull { it.id == form.typeId } ?: return null,
            approver = ready.approvers.firstOrNull { it.staffId == form.staffId } ?: return null,
            reason = form.trimmedReason
        )
    }

    private fun credentials(): Pair<String, String>? {
        val rollNo = credentialStore.rollNo()
        val password = credentialStore.password()
        return if (rollNo.isNullOrBlank() || password.isNullOrBlank()) null else rollNo to password
    }
}
