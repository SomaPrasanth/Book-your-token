package com.example.bookyourtoken

import android.Manifest
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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

    // "Install unknown apps" for this app; the update dialog re-checks it when the user comes back.
    private val installPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            appUpdates.onInstallPermissionResult()
        }

    private val requestInstallPermission: () -> Unit = {
        try {
            installPermissionLauncher.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            )
        } catch (_: ActivityNotFoundException) {
            appUpdates.onInstallPermissionResult()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Every screen starts with the gradient header, so the status-bar icons are always white.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Only a fresh launch from the shortcut/notification; not a restore after rotation or process death.
        val startOnQr = savedInstanceState == null && intent?.action == ACTION_SHOW_QR
        val updates = appUpdates
        updates.permissionRequester = requestInstallPermission
        if (savedInstanceState == null) {
            // Runs in the background; the UI never waits for it.
            updates.onAppLaunch()
            if (intent?.action == ACTION_SHOW_UPDATE) updates.showFromNotification()
        }

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
        if (intent.action == ACTION_SHOW_UPDATE) appUpdates.showFromNotification()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app drops a running update download (rotation doesn't).
        if (!isChangingConfigurations) appUpdates.onAppBackgrounded()
    }

    override fun onDestroy() {
        if (appUpdates.permissionRequester === requestInstallPermission) appUpdates.permissionRequester = null
        super.onDestroy()
    }

    companion object {
        /** Used by the "Show QR" launcher shortcut (res/xml/shortcuts.xml) and the QR-ready notification. */
        const val ACTION_SHOW_QR = "com.example.bookyourtoken.SHOW_QR"

        /** The "Update available" notification: opens the app on the update dialog. */
        const val ACTION_SHOW_UPDATE = "com.example.bookyourtoken.SHOW_UPDATE"
    }
}
