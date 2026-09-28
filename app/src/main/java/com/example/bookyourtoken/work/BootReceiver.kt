package com.example.bookyourtoken.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.bookyourtoken.appContainer

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (context.appContainer.credentials.hasCredentials()) {
                ReminderScheduler.schedule(context)
            }
        }
    }
}
