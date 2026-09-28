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
import com.example.bookyourtoken.data.ReminderCheck

object NotificationHelper {
    // Channel settings are frozen once created (even across delete + recreate with the same id),
    // so any change to importance/vibration needs a new id and removal of the old one.
    const val CHANNEL_ID = "token_reminder_v2"
    private const val LEGACY_CHANNEL_ID = "token_reminder"
    private const val NOTIFICATION_ID = 1001

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

        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (canPost) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
