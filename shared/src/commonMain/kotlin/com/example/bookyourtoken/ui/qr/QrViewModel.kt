package com.example.bookyourtoken.ui.qr

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookyourtoken.AppContainer
import com.example.bookyourtoken.data.DateUtils
import com.example.bookyourtoken.data.HostelClient
import com.example.bookyourtoken.data.QrPageParser
import com.example.bookyourtoken.data.QrSnapshot
import com.example.bookyourtoken.data.QrTokenRow
import com.example.bookyourtoken.data.models.ApiResult
import com.example.bookyourtoken.data.models.BookedToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

sealed interface QrUiState {
    /** [hasOfflineCopy]: a saved QR can be shown straight away instead of waiting on a weak signal. */
    data class Loading(val stage: String, val hasOfflineCopy: Boolean) : QrUiState

    /** No token has ViewStatus "1", so the QR page wasn't requested. */
    data object NoneAvailable : QrUiState

    /** The portal answered but its page had no QR image. */
    data object NotGenerated : QrUiState

    data class Error(val message: String) : QrUiState

    /** [offline]: a saved copy, shown because the fetch failed (or the user asked for it). */
    data class Showing(
        val image: ImageBitmap,
        val rows: List<QrTokenRow>,
        val fetchedAt: Instant,
        val offline: Boolean,
        val isRefreshing: Boolean = false
    ) : QrUiState
}

/**
 * login → StudentView → StudentGetToken → (only if some token has ViewStatus "1") the QR page, all
 * in one session — the same path as opening "View QR" on the website. Fetches only when the screen
 * opens or the user taps refresh; never polls.
 */
class QrViewModel(container: AppContainer) : ViewModel() {

    private val credentialStore = container.credentials
    private val store = container.qr

    private val _uiState = MutableStateFlow<QrUiState>(QrUiState.Loading(SIGNING_IN, hasOfflineCopy = false))
    val uiState: StateFlow<QrUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        val previous = _uiState.value as? QrUiState.Showing

        refreshJob = viewModelScope.launch {
            val saved = withContext(Dispatchers.Default) { store.loadValid() }
            _uiState.value = previous?.copy(isRefreshing = true)
                ?: QrUiState.Loading(SIGNING_IN, hasOfflineCopy = saved != null)

            val rollNo = credentialStore.rollNo()
            val password = credentialStore.password()
            if (rollNo.isNullOrBlank() || password.isNullOrBlank()) {
                _uiState.value = QrUiState.Error("No saved credentials. Sign in again from Settings.")
                return@launch
            }

            HostelClient().use { client ->
                val login = client.login(rollNo, password)
                if (login is ApiResult.Failure) return@launch fallBack(login.message, saved)

                stage("Checking your tokens…")
                val page = client.fetchBookingPageHtml()
                if (page is ApiResult.Failure) return@launch fallBack(page.message, saved)
                val tokens = when (val booked = client.fetchBookedTokens(rollNo)) {
                    is ApiResult.Failure -> return@launch fallBack(booked.message, saved)
                    is ApiResult.Success -> booked.data
                }

                val enabled = tokens.filter { it.qrEnabled }
                if (enabled.isEmpty()) {
                    _uiState.value = QrUiState.NoneAvailable
                    return@launch
                }

                stage("Loading your QR…")
                val html = when (val qr = client.fetchQrPageHtml()) {
                    is ApiResult.Failure -> return@launch fallBack(qr.message, saved)
                    is ApiResult.Success -> qr.data
                }

                val parsed = QrPageParser.parse(html)
                val image = parsed?.let { decodeQrImage(it.png) }
                if (parsed == null || image == null) {
                    // The portal answered — an older saved QR would only mislead here.
                    _uiState.value = QrUiState.NotGenerated
                    return@launch
                }

                val rows = parsed.rows.ifEmpty { enabled.map(::rowFor) }
                val snapshot = QrSnapshot(
                    png = parsed.png,
                    rows = rows,
                    fetchedAt = Clock.System.now(),
                    coversDates = coveredDates(enabled, rows)
                )
                withContext(Dispatchers.Default) { store.save(snapshot) }
                _uiState.value = QrUiState.Showing(image, rows, snapshot.fetchedAt, offline = false)
            }
        }
    }

    /** "Show saved copy" while still loading: stops the fetch and shows the offline QR. */
    fun showOfflineCopy() {
        if (_uiState.value !is QrUiState.Loading) return
        refreshJob?.cancel()
        viewModelScope.launch {
            val saved = withContext(Dispatchers.Default) { store.loadValid() }
            _uiState.value = saved?.let(::showSaved) ?: QrUiState.Error("The saved copy is no longer available.")
        }
    }

    private fun stage(text: String) {
        val current = _uiState.value
        if (current is QrUiState.Loading) _uiState.value = current.copy(stage = text)
    }

    /** A failed fetch shows the saved copy if there is one, otherwise the error. */
    private fun fallBack(message: String, saved: QrSnapshot?) {
        _uiState.value = saved?.let(::showSaved) ?: QrUiState.Error(message)
    }

    private fun showSaved(saved: QrSnapshot): QrUiState {
        val image = decodeQrImage(saved.png) ?: return QrUiState.Error("The saved QR couldn't be read.")
        return QrUiState.Showing(image, saved.rows, saved.fetchedAt, offline = true)
    }

    private companion object {
        const val SIGNING_IN = "Signing in to the portal…"

        /** Fallback table when the page's own token table can't be read. */
        fun rowFor(token: BookedToken) = QrTokenRow(
            tokenName = token.tokenName.orEmpty(),
            date = token.expireDate.orEmpty(),
            mealTime = token.mealTime.orEmpty(),
            quantity = (token.tokenQty ?: 1).toString()
        )

        /**
         * Food dates the QR covers: the ViewStatus "1" tokens' dates, plus any date in the page's
         * table. Taking both keeps the copy for as long as either says it's needed.
         */
        fun coveredDates(enabled: List<BookedToken>, rows: List<QrTokenRow>): List<String> =
            (enabled.map { it.expireDate } + rows.map { it.date })
                .mapNotNull { DateUtils.parseLooseDate(it) }
                .distinct()
                .map(DateUtils::formatPortalDate)
    }
}
