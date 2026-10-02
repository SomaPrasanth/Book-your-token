package com.example.bookyourtoken.ui.qr

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** Decodes the portal's PNG for display only — the QR's contents are never read. Null if it isn't an image. */
internal expect fun decodeQrImage(png: ByteArray): ImageBitmap?

/**
 * Full brightness and no screen timeout while the QR is on screen (and the app in the foreground);
 * both are restored when it leaves.
 */
@Composable
internal expect fun KeepScreenBrightAndOn()
