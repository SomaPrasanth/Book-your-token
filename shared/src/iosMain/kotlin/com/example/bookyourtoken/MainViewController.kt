package com.example.bookyourtoken

import androidx.compose.ui.window.ComposeUIViewController
import com.example.bookyourtoken.data.AppPreferences
import com.example.bookyourtoken.data.CredentialStore
import com.example.bookyourtoken.data.IosPrivateFiles
import com.example.bookyourtoken.data.QrStore
import com.example.bookyourtoken.ui.App
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.NSUserDefaultsSettings
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

@OptIn(ExperimentalSettingsImplementation::class)
private val container: AppContainer by lazy {
    val credentials = CredentialStore(KeychainSettings(service = "com.example.bookyourtoken.credentials"))
    val preferences = AppPreferences(NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults))
    AppContainer(
        credentials = credentials,
        preferences = preferences,
        reminders = IosReminders(credentials, preferences),
        platform = IosPlatformActions(),
        qr = QrStore(IosPrivateFiles())
    )
}

/** Called from Swift (iosApp/ContentView.swift) as `MainViewControllerKt.MainViewController()`. */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController {
    // iOS reminders are planned two weeks ahead; top the plan up whenever the app opens.
    if (container.credentials.hasCredentials()) container.reminders.schedule()
    return ComposeUIViewController { App(container) }
}
