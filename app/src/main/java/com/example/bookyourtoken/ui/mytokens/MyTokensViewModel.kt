package com.example.bookyourtoken.ui.mytokens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookyourtoken.data.CredentialStore
import com.example.bookyourtoken.data.DateUtils
import com.example.bookyourtoken.data.HostelClient
import com.example.bookyourtoken.data.models.ApiResult
import com.example.bookyourtoken.data.models.BookedToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MyTokensUiState {
    data object Loading : MyTokensUiState
    data class Error(val message: String) : MyTokensUiState
    data class Loaded(val groups: List<TokenGroup>, val isRefreshing: Boolean = false) : MyTokensUiState
}

sealed interface CancelUiState {
    val token: BookedToken
    val bulk: Boolean

    data class Confirm(override val token: BookedToken, override val bulk: Boolean) : CancelUiState
    data class Running(override val token: BookedToken, override val bulk: Boolean) : CancelUiState

    /** [quantityAfter] is null when the list couldn't be re-read after the request. */
    data class Done(
        override val token: BookedToken,
        override val bulk: Boolean,
        val success: Boolean,
        val message: String,
        val quantityBefore: Int,
        val quantityAfter: Int?
    ) : CancelUiState
}

class MyTokensViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialStore = CredentialStore(application)

    private val _uiState = MutableStateFlow<MyTokensUiState>(MyTokensUiState.Loading)
    val uiState: StateFlow<MyTokensUiState> = _uiState.asStateFlow()

    private val _cancelState = MutableStateFlow<CancelUiState?>(null)
    val cancelState: StateFlow<CancelUiState?> = _cancelState.asStateFlow()

    /** Set when the booked list really changed, so the Tokens screen knows to reload. */
    private val _tokensChanged = MutableStateFlow(false)
    val tokensChanged: StateFlow<Boolean> = _tokensChanged.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true || _cancelState.value is CancelUiState.Running) return
        val previous = _uiState.value as? MyTokensUiState.Loaded
        _uiState.value = previous?.copy(isRefreshing = true) ?: MyTokensUiState.Loading

        refreshJob = viewModelScope.launch {
            val (rollNo, password) = credentials() ?: run {
                _uiState.value = MyTokensUiState.Error("No saved credentials. Sign in again from Settings.")
                return@launch
            }
            when (val session = openSession(HostelClient(), rollNo, password)) {
                is ApiResult.Failure -> _uiState.value = MyTokensUiState.Error(session.message)
                is ApiResult.Success -> show(session.data)
            }
        }
    }

    fun requestCancel(token: BookedToken, bulk: Boolean) {
        if (token.canCancel && _cancelState.value == null) _cancelState.value = CancelUiState.Confirm(token, bulk)
    }

    /** Closes the confirm or result dialog. A running cancel can't be dismissed. */
    fun dismissCancel() {
        if (_cancelState.value !is CancelUiState.Running) _cancelState.value = null
    }

    fun consumeTokensChanged() {
        _tokensChanged.value = false
    }

    fun confirmCancel() {
        val confirm = _cancelState.value as? CancelUiState.Confirm ?: return
        val token = confirm.token
        val bulk = confirm.bulk
        _cancelState.value = CancelUiState.Running(token, bulk)

        viewModelScope.launch {
            fun done(success: Boolean, message: String, before: Int, after: Int?) {
                _cancelState.value = CancelUiState.Done(token, bulk, success, message, before, after)
            }

            val (rollNo, password) = credentials()
                ?: return@launch done(false, "No saved credentials.", token.tokenQty ?: 1, null)

            val client = HostelClient()
            val beforeList = when (val session = openSession(client, rollNo, password)) {
                is ApiResult.Failure -> return@launch done(false, session.message, token.tokenQty ?: 1, null)
                is ApiResult.Success -> session.data
            }

            // Re-check against the fresh list: the token may have been cancelled or used meanwhile.
            val current = beforeList.firstOrNull { it.isSameTokenAs(token) }
            val before = quantityOf(beforeList, token)
            if (current == null) {
                show(beforeList)
                _tokensChanged.value = true
                return@launch done(false, "This token is no longer in your bookings.", 0, 0)
            }
            if (!current.canCancel) {
                show(beforeList)
                return@launch done(false, "This token has already been used and can't be cancelled.", before, before)
            }

            val result = if (bulk) client.cancelAllOfToken(rollNo, current) else client.cancelToken(rollNo, current)

            // Always re-read the real state instead of assuming what changed.
            val afterList = (client.fetchBookedTokens(rollNo) as? ApiResult.Success)?.data
            val after = afterList?.let { quantityOf(it, token) }
            val decreased = after != null && after < before

            when (result) {
                is ApiResult.Success -> done(result.data.success, result.data.message, before, after)
                is ApiResult.Failure -> when {
                    !result.isTimeout -> done(false, result.message, before, after)
                    decreased -> done(true, "Token cancelled (confirmed after timeout)", before, after)
                    else -> done(
                        false,
                        "Timed out — couldn't confirm the cancel went through. Check your tokens before trying again.",
                        before,
                        after
                    )
                }
            }

            if (afterList != null) show(afterList)
            if (decreased) _tokensChanged.value = true
        }
    }

    private fun credentials(): Pair<String, String>? {
        val rollNo = credentialStore.rollNo()
        val password = credentialStore.password()
        return if (rollNo.isNullOrBlank() || password.isNullOrBlank()) null else rollNo to password
    }

    /**
     * login → StudentView → StudentGetToken in one session. Loading StudentView first matches the
     * website and sets up the server-side session state that booking (and likely cancelling) needs.
     */
    private suspend fun openSession(client: HostelClient, rollNo: String, password: String): ApiResult<List<BookedToken>> {
        val login = client.login(rollNo, password)
        if (login is ApiResult.Failure) return login
        val page = client.fetchBookingPageHtml()
        if (page is ApiResult.Failure) return page
        return client.fetchBookedTokens(rollNo)
    }

    private fun show(tokens: List<BookedToken>) {
        _uiState.value = MyTokensUiState.Loaded(groupTokensByDate(tokens, DateUtils.today()))
    }
}
