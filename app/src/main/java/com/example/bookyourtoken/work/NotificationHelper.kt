package com.example.bookyourtoken.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.bookyourtoken.MainActivity
import com.example.bookyourtoken.R
import com.example.bookyourtoken.data.QrReadyCheck
import com.example.bookyourtoken.data.ReminderCheck

object NotificationHelper {
    // Channel settings are frozen once created (even across delete + recreate with the same id),
    // so any change to importance/vibration needs a new id and removal of the old one.
    const val CHANNEL_ID = "token_reminder_v2"
    private const val LEGACY_CHANNEL_ID = "token_reminder"
    private const val NOTIFICATION_ID = 1001
    private const val QR_NOTIFICATION_ID = 1002
    private const val UPDATE_CHANNEL_ID = "app_updates"
    private const val UPDATE_NOTIFICATION_ID = 1003

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
            // HIGH = heads-up popup. Default sound + vibration enabled lets the system follow the
            // ringer: ring → sound, vibrate → vibration only, silent → nothing.
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Token reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun postReminder(context: Context, body: String) {
        ensureChannel(context)

        // Tokens is the start destination whenever credentials exist. NEW_TASK alone brings a running
        // app to the front instead of recreating it, so an in-flight booking isn't cancelled.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentIntent = PendingIntent.getActivity(context, 0, openIntent, flags)
        val actionIntent = PendingIntent.getActivity(context, 1, openIntent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ReminderCheck.NOTIFICATION_TITLE)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .addAction(0, "What's available tomorrow?", actionIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()

        notifyIfAllowed(context, NOTIFICATION_ID, notification)
    }

    /** "Your food QR is ready" — tapping it opens the QR screen. */
    fun postQrReady(context: Context) {
        ensureChannel(context)

        // SINGLE_TOP delivers the intent to a running MainActivity (onNewIntent) instead of ignoring it.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHOW_QR
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            2,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(QrReadyCheck.NOTIFICATION_TITLE)
            .setContentText(QrReadyCheck.NOTIFICATION_BODY)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()

        notifyIfAllowed(context, QR_NOTIFICATION_ID, notification)
    }

    /** "Update available" on its own channel, so it can be silenced without losing reminders. */
    fun postUpdateAvailable(context: Context, versionName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(UPDATE_CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHOW_UPDATE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            3,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Update available")
            .setContentText("Version $versionName is ready. Tap to update.")
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notifyIfAllowed(context, UPDATE_NOTIFICATION_ID, notification)
    }

    private fun notifyIfAllowed(context: Context, id: Int, notification: android.app.Notification) {
        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (canPost) {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}
