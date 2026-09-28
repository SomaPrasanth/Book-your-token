package com.example.bookyourtoken.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.bookyourtoken.PlatformActions

/** Opening links and system settings, provided once at the root by [com.example.bookyourtoken.ui.App]. */
val LocalPlatformActions = staticCompositionLocalOf<PlatformActions> {
    error("PlatformActions not provided")
}
