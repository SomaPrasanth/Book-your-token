package com.example.bookyourtoken

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.bookyourtoken.work.ReminderScheduler

class AndroidReminders(private val context: Context) : Reminders {
    override fun schedule() = ReminderScheduler.schedule(context)

    override fun cancel() = ReminderScheduler.cancel(context)

    override fun checkNow() = ReminderScheduler.runNow(context)

    override val checksInBackground: Boolean = true
}

class AndroidPlatformActions(private val context: Context) : PlatformActions {

    // Started from the application context, so every intent needs its own task.
    override fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // No browser installed — nothing sensible to fall back to.
        }
    }

    override fun openNotificationSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override suspend fun notificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    override val appVersion: String? by lazy {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
}
