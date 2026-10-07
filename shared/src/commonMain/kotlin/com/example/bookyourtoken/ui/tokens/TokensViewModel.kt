package com.example.bookyourtoken.ui.tokens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookyourtoken.AppContainer
import com.example.bookyourtoken.data.DateUtils
import com.example.bookyourtoken.data.HostelClient
import com.example.bookyourtoken.data.StudentNames
import com.example.bookyourtoken.data.TokenPageParser
import com.example.bookyourtoken.data.models.ApiResult
import com.example.bookyourtoken.data.models.BookedToken
import com.example.bookyourtoken.data.models.TokenItem
import com.example.bookyourtoken.ui.booking.BookingLine
import com.example.bookyourtoken.ui.booking.BookingUiState
import com.example.bookyourtoken.ui.booking.runBookings
import com.example.bookyourtoken.ui.mytokens.countUpcoming
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TokensUiState {
    data class Loading(val stage: String) : TokensUiState
    data class Error(val message: String) : TokensUiState
    data class Loaded(
        val tomorrowDate: String,
        val tomorrowLabel: String,
        val items: List<TokenItem>,
        val bookedTomorrow: List<BookedToken>,
        /** Some booked token has ViewStatus "1" — the portal has enabled its QR. */
        val qrAvailable: Boolean = false,
        /** Booked tokens from today on, for the Booked tab's badge. */
        val upcomingCount: Int = 0,
        val selections: Selections = emptyMap(),
        val isRefreshing: Boolean = false
    ) : TokensUiState {
        val selected: List<Pair<TokenItem, Selection>>
            get() = items.mapNotNull { item -> selections[item.ptokenId]?.let { item to it } }

        fun bookedFor(item: TokenItem): List<BookedToken> = bookedTomorrow.filter { it.tokenId == item.ptokenId }
    }
}

class TokensViewModel(private val container: AppContainer) : ViewModel() {

    private val credentialStore = container.credentials

    private val _uiState = MutableStateFlow<TokensUiState>(TokensUiState.Loading("Signing in to the portal…"))
    val uiState: StateFlow<TokensUiState> = _uiState.asStateFlow()

    private val _bookingState = MutableStateFlow<BookingUiState?>(null)
    val bookingState: StateFlow<BookingUiState?> = _bookingState.asStateFlow()

    private val _showConfirmDialog = MutableStateFlow(false)
    val showConfirmDialog: StateFlow<Boolean> = _showConfirmDialog.asStateFlow()

    private val greeting = container.greeting

    /** The greeting's name: the user's own, else the portal's short name, else none. */
    val greetingName: StateFlow<String?> = greeting.displayName

    private var refreshJob: Job? = null
    private var nameJob: Job? = null

    init {
        refresh()
    }

    /** Keeps the current list on screen while refreshing; a single request chain at a time. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        val previous = _uiState.value as? TokensUiState.Loaded
        _uiState.value = previous?.copy(isRefreshing = true) ?: TokensUiState.Loading("Signing in to the portal…")

        refreshJob = viewModelScope.launch {
            val rollNo = credentialStore.rollNo()
            val password = credentialStore.password()
            if (rollNo.isNullOrBlank() || password.isNullOrBlank()) {
                _uiState.value = TokensUiState.Error("No saved credentials. Sign in again from Settings.")
                return@launch
            }

            val client = HostelClient()
            var nameFetch: Job? = null
            try {
                val login = client.login(rollNo, password)
                if (login is ApiResult.Failure) {
                    _uiState.value = TokensUiState.Error(login.message)
                    return@launch
                }
                val jwt = (login as? ApiResult.Success)?.data

                showStage("Reading tomorrow's menu…")
                val html = when (val page = client.fetchBookingPageHtml()) {
                    is ApiResult.Failure -> {
                        _uiState.value = TokensUiState.Error(page.message)
                        return@launch
                    }
                    is ApiResult.Success -> page.data
                }

                val items = TokenPageParser.parse(html)
                if (items.isEmpty()) {
                    _uiState.value = TokensUiState.Error("Couldn't read the booking page — the portal may have changed.")
                    return@launch
                }

                showStage("Checking your bookings…")
                val tomorrow = DateUtils.tomorrowString()
                val booked = (client.fetchBookedTokens(rollNo) as? ApiResult.Success)?.data.orEmpty()
                val bookedTomorrow = booked.filter { it.expireDate == tomorrow }

                _uiState.value = TokensUiState.Loaded(
                    tomorrowDate = tomorrow,
                    tomorrowLabel = DateUtils.friendlyLabel(DateUtils.tomorrow()),
                    items = items.filter { tomorrow in it.dates },
                    bookedTomorrow = bookedTomorrow,
                    qrAvailable = booked.any { it.qrEnabled },
                    upcomingCount = countUpcoming(booked, DateUtils.today())
                )
                if (bookedTomorrow.isNotEmpty()) container.reminders.onTomorrowBooked()
                // Only now: the portal may handle one request per session at a time, so starting
                // earlier could hold up the token list.
                nameFetch = fetchNameInBackground(client, rollNo, jwt)
            } finally {
                // A name fetch still running shares this session; it closes it when it's done.
                val pending = nameFetch
                if (pending == null) client.close() else pending.invokeOnCompletion { client.close() }
            }
        }
    }

    /**
     * The greeting's name, at most once a day, on this load's own session (no second login). It
     * runs on its own, and any failure just leaves the name as it was.
     */
    private fun fetchNameInBackground(client: HostelClient, rollNo: String, jwt: String?): Job? {
        if (nameJob?.isActive == true || !greeting.isFetchDue(DateUtils.today())) return null
        return viewModelScope.launch {
            val body = client.studentDetailsJson(rollNo, jwt) ?: return@launch
            container.debugLog?.invoke("studDetails keys: ${StudentNames.keyNames(body)}")
            greeting.onPortalFetched(StudentNames.findFullName(body), DateUtils.today())
        }.also { nameJob = it }
    }

    private fun showStage(stage: String) {
        if (_uiState.value is TokensUiState.Loading) _uiState.value = TokensUiState.Loading(stage)
    }

    fun toggle(item: TokenItem) = updateSelections { it.toggle(item) }

    fun setMeal(item: TokenItem, meal: String) = updateSelections { it.withMeal(item, meal) }

    fun setQuantity(item: TokenItem, quantity: Int) = updateSelections { it.withQuantity(item, quantity) }

    private inline fun updateSelections(transform: (Selections) -> Selections) {
        val current = _uiState.value as? TokensUiState.Loaded ?: return
        _uiState.value = current.copy(selections = transform(current.selections))
    }

    fun requestConfirm() {
        val current = _uiState.value
        if (current is TokensUiState.Loaded && current.selections.isNotEmpty()) {
            _showConfirmDialog.value = true
        }
    }

    fun dismissConfirm() {
        _showConfirmDialog.value = false
    }

    fun dismissBooking() {
        _bookingState.value = null
    }

    fun confirmBooking() {
        val current = _uiState.value as? TokensUiState.Loaded ?: return
        val selected = current.selected
        if (selected.isEmpty()) return

        _showConfirmDialog.value = false
        // The date the confirm dialog showed, sent exactly as the portal's dropdown spells it.
        val lines = selected.map { (item, sel) -> BookingLine(item, sel.meal, sel.quantity, current.tomorrowDate) }

        viewModelScope.launch {
            runBookings(credentialStore, lines) { _bookingState.value = it }
            refresh()
        }
    }
}
