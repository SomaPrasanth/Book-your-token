package com.example.bookyourtoken

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.bookyourtoken.ui.App

class MainActivity : ComponentActivity() {

    // If denied, Settings shows a "Notifications are off" row that links to the system page.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Only a fresh launch from the shortcut/notification; not a restore after rotation or process death.
        val startOnQr = savedInstanceState == null && intent?.action == ACTION_SHOW_QR
        val container = appContainer
        setContent {
            App(container, startOnQr = startOnQr, openQrRequest = openQrRequest)
        }
    }

    /** Bumped whenever a "Show QR" intent reaches the already-running activity. */
    private var openQrRequest by mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_SHOW_QR) openQrRequest++
    }

    companion object {
        /** Used by the "Show QR" launcher shortcut (res/xml/shortcuts.xml) and the QR-ready notification. */
        const val ACTION_SHOW_QR = "com.example.bookyourtoken.SHOW_QR"
    }
}
