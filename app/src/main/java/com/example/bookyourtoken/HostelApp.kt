package com.example.bookyourtoken

import android.app.Application
import com.example.bookyourtoken.work.NotificationHelper

class HostelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
    }
}
