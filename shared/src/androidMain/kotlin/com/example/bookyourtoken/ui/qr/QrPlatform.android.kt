package com.example.bookyourtoken.ui.qr

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

internal actual fun decodeQrImage(png: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(png, 0, png.size)?.asImageBitmap()

@Composable
internal actual fun KeepScreenBrightAndOn() {
    val activity = LocalContext.current.findActivity() ?: return
    LifecycleResumeEffect(activity) {
        val window = activity.window
        val previousBrightness = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onPauseOrDispose {
            window.attributes = window.attributes.apply { screenBrightness = previousBrightness }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
