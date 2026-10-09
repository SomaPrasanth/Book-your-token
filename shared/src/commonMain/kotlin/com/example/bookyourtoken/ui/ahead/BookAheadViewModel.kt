package com.example.bookyourtoken.ui.ahead

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookyourtoken.AppContainer
import com.example.bookyourtoken.data.DateUtils
import com.example.bookyourtoken.data.HostelClient
import com.example.bookyourtoken.data.TokenPageParser
import com.example.bookyourtoken.data.models.ApiResult
import com.example.bookyourtoken.data.models.BookedToken
import com.example.bookyourtoken.data.models.TokenItem
import com.example.bookyourtoken.ui.booking.BookingUiState
import com.example.bookyourtoken.ui.booking.runBookings
import com.example.bookyourtoken.ui.mytokens.countUpcoming
import com.example.bookyourtoken.ui.tokens.Selections
import com.example.bookyourtoken.ui.tokens.toggle
import com.example.bookyourtoken.ui.tokens.withMeal
import com.example.bookyourtoken.ui.tokens.withQuantity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BookAheadUiState {
    data class Loading(val stage: String) : BookAheadUiState
    data class Error(val message: String) : BookAheadUiState
    data class Loaded(
        val dates: List<UpcomingDate>,
        val booked: List<BookedToken>,
        /** Raw dropdown string of the date whose items are shown. */
        val selectedDate: String?,
        val selections: DateSelections = emptyMap(),
        val upcomingCount: Int = 0,
        val isRefreshing: Boolean = false
    ) : BookAheadUiState {
        val current: UpcomingDate? get() = dates.firstOrNull { it.raw == selectedDate }

        fun bookedOn(raw: String): List<BookedToken> = booked.filter { it.expireDate == raw }

        fun bookedFor(item: TokenItem, raw: String): List<BookedToken> =
            bookedOn(raw).filter { it.tokenId == item.ptokenId }
    }
}

class BookAheadViewModel(private val container: AppContainer) : ViewModel() {

    private val credentialStore = container.credentials

    private val _uiState = MutableStateFlow<BookAheadUiState>(BookAheadUiState.Loading("Signing in to the portal…"))
    val uiState: StateFlow<BookAheadUiState> = _uiState.asStateFlow()

    private val _bookingState = MutableStateFlow<BookingUiState?>(null)
    val bookingState: StateFlow<BookingUiState?> = _bookingState.asStateFlow()

    private val _showConfirmDialog = MutableStateFlow(false)
    val showConfirmDialog: StateFlow<Boolean> = _showConfirmDialog.asStateFlow()

    /** A short message for a snackbar, e.g. picks dropped by a refresh. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** Goes up each time a run booked at least one token, so other screens know to reload. */
    private val _bookedRuns = MutableStateFlow(0)
    val bookedRuns: StateFlow<Int> = _bookedRuns.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    private val loaded: BookAheadUiState.Loaded? get() = _uiState.value as? BookAheadUiState.Loaded

    /**
     * One StudentView fetch gives every date — never one request per date. Keeps the current
     * screen and picks while refreshing.
     */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        val previous = loaded
        _uiState.value = previous?.copy(isRefreshing = true) ?: BookAheadUiState.Loading("Signing in to the portal…")

        refreshJob = viewModelScope.launch {
            val rollNo = credentialStore.rollNo()
            val password = credentialStore.password()
            if (rollNo.isNullOrBlank() || password.isNullOrBlank()) {
                _uiState.value = BookAheadUiState.Error("No saved credentials. Sign in again from Settings.")
                return@launch
            }

            HostelClient().use { client ->
                val login = client.login(rollNo, password)
                if (login is ApiResult.Failure) {
                    _uiState.value = BookAheadUiState.Error(login.message)
                    return@launch
                }

                showStage("Reading the menu for upcoming days…")
                val html = when (val page = client.fetchBookingPageHtml()) {
                    is ApiResult.Failure -> {
                        _uiState.value = BookAheadUiState.Error(page.message)
                        return@launch
                    }
                    is ApiResult.Success -> page.data
                }

                val items = TokenPageParser.parse(html)
                if (items.isEmpty()) {
                    _uiState.value = BookAheadUiState.Error("Couldn't read the booking page — the portal may have changed.")
                    return@launch
                }

                showStage("Checking your bookings…")
                val booked = (client.fetchBookedTokens(rollNo) as? ApiResult.Success)?.data.orEmpty()

                // Fresh each refresh, so a screen left open past midnight moves on to the new day.
                val today = DateUtils.today()
                val dates = upcomingDates(items, today)
                // Latest picks, not the ones from when the refresh started: the user may have kept tapping.
                val picks = loaded?.selections ?: emptyMap()
                val passed = picks.passed(today)
                val (kept, dropped) = (picks - passed.keys).prunedTo(dates)
                refreshNotice(passed, dropped)?.let { _notice.value = it }
                val keepDate = loaded?.selectedDate?.takeIf { raw -> dates.any { it.raw == raw } }

                _uiState.value = BookAheadUiState.Loaded(
                    dates = dates,
                    booked = booked,
                    selectedDate = keepDate ?: dates.firstOrNull()?.raw,
                    selections = kept,
                    upcomingCount = countUpcoming(booked, today)
                )
                onBookedListChanged(booked)
            }
        }
    }

    private fun showStage(stage: String) {
        if (_uiState.value is BookAheadUiState.Loading) _uiState.value = BookAheadUiState.Loading(stage)
    }

    fun selectDate(raw: String) {
        val current = loaded ?: return
        if (current.dates.any { it.raw == raw }) _uiState.value = current.copy(selectedDate = raw)
    }

    fun toggle(item: TokenItem) = updatePicks { it.toggle(item) }

    fun setMeal(item: TokenItem, meal: String) = updatePicks { it.withMeal(item, meal) }

    // withQuantity caps at item.maxQty, per date, exactly as on Tomorrow.
    fun setQuantity(item: TokenItem, quantity: Int) = updatePicks { it.withQuantity(item, quantity) }

    private fun updatePicks(transform: (Selections) -> Selections) {
        val current = loaded ?: return
        val date = current.selectedDate ?: return
        _uiState.value = current.copy(selections = current.selections.update(date, transform))
    }

    fun clearAll() {
        val current = loaded ?: return
        _uiState.value = current.copy(selections = emptyMap())
    }

    fun noticeShown() {
        _notice.value = null
    }

    fun requestConfirm() {
        val current = loaded ?: return
        if (current.selections.itemCount > 0) _showConfirmDialog.value = true
    }

    fun dismissConfirm() {
        _showConfirmDialog.value = false
    }

    fun dismissBooking() {
        _bookingState.value = null
    }

    /** Only from the grouped confirmation dialog. */
    fun confirmBooking() {
        val current = loaded ?: return
        val lines = bookingLines(current.dates, current.selections)
        if (lines.isEmpty()) return
        _showConfirmDialog.value = false

        viewModelScope.launch {
            val bookedAfter = runBookings(
                credentialStore,
                lines,
                groupByDate = true,
                fetchBookedAfter = true
            ) { _bookingState.value = it }

            val results = _bookingState.value?.lines.orEmpty()
            val anyBooked = results.any { it.succeeded }
            val latest = loaded ?: return@launch
            _uiState.value = latest.copy(
                selections = latest.selections.withoutBooked(results),
                booked = bookedAfter ?: latest.booked,
                upcomingCount = bookedAfter?.let { countUpcoming(it, DateUtils.today()) } ?: latest.upcomingCount
            )
            if (anyBooked) _bookedRuns.value += 1
            when {
                bookedAfter != null -> onBookedListChanged(bookedAfter)
                // Booked something but couldn't re-read the list: reload so dots and strips are right.
                anyBooked -> refresh()
            }
        }
    }

    /** Same as the Tomorrow screen: a booking for tomorrow quiets today's reminder. */
    private fun onBookedListChanged(booked: List<BookedToken>) {
        val tomorrow = DateUtils.tomorrowString()
        if (booked.any { it.expireDate == tomorrow }) container.reminders.onTomorrowBooked()
    }
}
