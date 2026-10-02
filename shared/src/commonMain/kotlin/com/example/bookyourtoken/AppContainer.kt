package com.example.bookyourtoken

import com.example.bookyourtoken.data.AppPreferences
import com.example.bookyourtoken.data.CredentialStore
import com.example.bookyourtoken.data.QrStore

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

/** Everything the shared code needs, built once per process by each platform. */
class AppContainer(
    val credentials: CredentialStore,
    val preferences: AppPreferences,
    val reminders: Reminders,
    val platform: PlatformActions,
    val qr: QrStore
)
