package com.example.bookyourtoken.ui.qr

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.jetbrains.skia.Image
import platform.UIKit.UIApplication
import platform.UIKit.UIScreen

internal actual fun decodeQrImage(png: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(png).toComposeImageBitmap() }.getOrNull()

/** iOS doesn't undo an app's brightness change when it goes to the background, so restore on pause. */
@Composable
internal actual fun KeepScreenBrightAndOn() {
    LifecycleResumeEffect(Unit) {
        val screen = UIScreen.mainScreen
        val previousBrightness = screen.brightness
        screen.brightness = 1.0
        UIApplication.sharedApplication.idleTimerDisabled = true
        onPauseOrDispose {
            screen.brightness = previousBrightness
            UIApplication.sharedApplication.idleTimerDisabled = false
        }
    }
}
