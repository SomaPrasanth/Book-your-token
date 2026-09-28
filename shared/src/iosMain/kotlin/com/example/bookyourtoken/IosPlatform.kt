package com.example.bookyourtoken

import com.example.bookyourtoken.data.AppPreferences
import com.example.bookyourtoken.data.CredentialStore
import com.example.bookyourtoken.data.DateUtils
import com.example.bookyourtoken.data.ReminderCheck
import com.example.bookyourtoken.data.ReminderOutcome
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSBundle
import platform.Foundation.NSDateComponents
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.time.Clock

/**
 * iOS can't reliably run a background job at a set time, so instead of logging in and checking
 * the portal like Android does, the daily reminder is a plain local notification planned for the
 * next [DAYS_AHEAD] days (re-planned every time the app opens). Sound, vibration and the banner
 * follow the phone's ring/silent switch and Focus settings automatically.
 *
 * "Skip if already booked" works from what the app has seen: when the Tokens screen finds
 * tomorrow already booked, today's pending reminder is dropped.
 */
class IosReminders(
    private val credentials: CredentialStore,
    private val preferences: AppPreferences
) : Reminders {

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()
    private val defaults get() = NSUserDefaults.standardUserDefaults
    private val scope = MainScope()

    override val checksInBackground: Boolean = false

    override fun schedule() {
        center.removePendingNotificationRequestsWithIdentifiers(plannedIds())
        if (!credentials.hasCredentials()) return

        val skipped = if (preferences.skipIfAlreadyBooked) defaults.stringForKey(KEY_SKIPPED_DATE) else null
        val now = Clock.System.now()
        val today = DateUtils.today()
        val time = LocalTime(preferences.reminderHour, preferences.reminderMinute)

        for (offset in 0 until DAYS_AHEAD) {
            val date = today.plus(offset, DateTimeUnit.DAY)
            val id = idFor(date)
            if (id == skipped) continue
            val fireAt = LocalDateTime(date, time).toInstant(DateUtils.ZONE)
            if (fireAt <= now) continue

            // Components in the phone's own time zone, so a phone set to another zone still fires
            // at the chosen India time.
            val local = fireAt.toLocalDateTime(TimeZone.currentSystemDefault())
            val components = NSDateComponents().apply {
                year = local.year.toLong()
                month = local.month.number.toLong()
                day = local.day.toLong()
                hour = local.hour.toLong()
                minute = local.minute.toLong()
            }
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
            post(id, DAILY_BODY, trigger)
        }
    }

    override fun cancel() {
        center.removePendingNotificationRequestsWithIdentifiers(plannedIds())
        defaults.removeObjectForKey(KEY_SKIPPED_DATE)
    }

    override fun checkNow() {
        scope.launch {
            when (val outcome = ReminderCheck.run(credentials, preferences)) {
                ReminderOutcome.NotSignedIn, ReminderOutcome.AlreadyBooked -> Unit
                is ReminderOutcome.Notify -> post(CHECK_NOW_ID, outcome.body, trigger = null)
            }
        }
    }

    override fun onTomorrowBooked() {
        // Today's reminder is the one about tomorrow. Remember it so re-planning doesn't bring it back.
        val id = idFor(DateUtils.today())
        defaults.setObject(id, forKey = KEY_SKIPPED_DATE)
        if (preferences.skipIfAlreadyBooked) center.removePendingNotificationRequestsWithIdentifiers(listOf(id))
    }

    /** trigger == null delivers right away. */
    private fun post(id: String, body: String, trigger: UNNotificationTrigger?) {
        val content = UNMutableNotificationContent().apply {
            setTitle(ReminderCheck.NOTIFICATION_TITLE)
            setBody(body)
            setSound(UNNotificationSound.defaultSound())
        }
        center.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(id, content, trigger),
            withCompletionHandler = null
        )
    }

    /** Ids for yesterday through the planning window, so stale entries are always cleared too. */
    private fun plannedIds(): List<String> {
        val today = DateUtils.today()
        return (-1 until DAYS_AHEAD + 1).map { idFor(today.plus(it, DateTimeUnit.DAY)) }
    }

    private fun idFor(day: LocalDate) = "reminder-${DateUtils.formatPortalDate(day)}"

    private companion object {
        const val DAYS_AHEAD = 14
        const val CHECK_NOW_ID = "check-now"
        const val KEY_SKIPPED_DATE = "skipped_reminder_id"
        const val DAILY_BODY = "Tap to see what's available tomorrow."
    }
}

class IosPlatformActions : PlatformActions {

    override fun openUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }

    /** iOS has no direct link to the notification page; this opens the app's own Settings page. */
    override fun openNotificationSettings() = openUrl(UIApplicationOpenSettingsURLString)

    override suspend fun notificationsEnabled(): Boolean = suspendCancellableCoroutine { cont ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            cont.resume(
                status == UNAuthorizationStatusAuthorized ||
                    status == UNAuthorizationStatusProvisional ||
                    status == UNAuthorizationStatusEphemeral
            )
        }
    }

    override val appVersion: String? =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
}
