package com.example.bookyourtoken

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.bookyourtoken.data.PrivateFiles
import com.example.bookyourtoken.work.ReminderScheduler
import java.io.File
import java.io.IOException

class AndroidReminders(private val context: Context) : Reminders {
    override fun schedule() {
        ReminderScheduler.schedule(context)
        ReminderScheduler.scheduleQrReady(context)
    }

    override fun cancel() {
        ReminderScheduler.cancel(context)
        ReminderScheduler.cancelQrReady(context)
    }

    override fun checkNow() = ReminderScheduler.runNow(context)

    override val checksInBackground: Boolean = true

    override fun scheduleQrReadyCheck() = ReminderScheduler.scheduleQrReady(context)
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

/** App-private internal storage (Context.filesDir/qr). Excluded from backups in res/xml. */
class AndroidPrivateFiles(context: Context) : PrivateFiles {
    private val dir = File(context.filesDir, "qr")

    override fun read(name: String): ByteArray? =
        File(dir, name).takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }

    override fun write(name: String, bytes: ByteArray) {
        dir.mkdirs()
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(File(dir, name))) {
            tmp.delete()
            throw IOException("Couldn't save $name")
        }
    }

    override fun delete(name: String) {
        File(dir, name).delete()
    }
}
