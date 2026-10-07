package com.example.bookyourtoken

import com.example.bookyourtoken.data.AppPreferences
import com.example.bookyourtoken.data.CredentialStore
import com.example.bookyourtoken.data.GreetingStore
import com.example.bookyourtoken.data.QrStore
import com.example.bookyourtoken.data.UpdateCheckResult
import com.example.bookyourtoken.data.UpdateDialogState
import kotlinx.coroutines.flow.StateFlow

/** Daily reminder scheduling — WorkManager on Android, local notifications on iOS. */
interface Reminders {
    /** (Re)schedules the daily reminder at the time saved in [AppPreferences]. */
    fun schedule()

    fun cancel()

    /** Runs the reminder check right away; a notification follows unless it decides to skip. */
    fun checkNow()

    /**
     * The app just saw that tomorrow is already booked. Android's background check finds this out
     * by itself; iOS can't check in the background, so it drops today's reminder here instead.
     */
    fun onTomorrowBooked() {}

    /** True when the reminder logs in and checks the portal in the background before notifying. */
    val checksInBackground: Boolean

    /**
     * (Re)schedules — or cancels, when switched off — the optional morning "QR ready" check. Only
     * where [checksInBackground]: it has to ask the portal, which iOS can't do at a set time.
     */
    fun scheduleQrReadyCheck() {}
}

/** The few things the shared UI needs from the operating system. */
interface PlatformActions {
    fun openUrl(url: String)

    fun openNotificationSettings()

    suspend fun notificationsEnabled(): Boolean

    val appVersion: String?
}

/**
 * In-app updates from the public GitHub releases repo — Android only (sideloaded APKs). The user
 * always confirms on Android's own install screen; nothing is installed silently.
 */
interface AppUpdater {
    /** The update dialog to show over the whole app, or null for none. */
    val dialog: StateFlow<UpdateDialogState?>

    /** github.com/{owner}/{repo}/releases/latest, for downloading by hand. */
    val releasesPageUrl: String

    /** Settings → "Check for updates": ignores the 6-hour limit and any "Later", and reports errors. */
    suspend fun checkNow(): UpdateCheckResult

    /** Closes the dialog and holds that version back for 24 hours. Ignored for required updates. */
    fun later()

    /** Downloads (or re-downloads) the dialog's release, verifies it, then moves on to installing. */
    fun startDownload()

    /** Stops the download, deletes the partial file and goes back to the release notes. */
    fun cancelDownload()

    /** Opens the system "Install unknown apps" page for this app. */
    fun requestInstallPermission()

    /** Opens Android's install screen for the verified download again. */
    fun install()

    /** "Updated to 1.3.0" once, on the first launch after an update; null otherwise. */
    fun takeUpdatedMessage(): String?
}

/** Everything the shared code needs, built once per process by each platform. */
class AppContainer(
    val credentials: CredentialStore,
    val preferences: AppPreferences,
    val greeting: GreetingStore,
    val reminders: Reminders,
    val platform: PlatformActions,
    val qr: QrStore,
    /** Null where the app can't update itself (iOS). */
    val updater: AppUpdater? = null,
    /** Debug builds only, and only for developer hints that hold no personal data. */
    val debugLog: ((String) -> Unit)? = null
)
