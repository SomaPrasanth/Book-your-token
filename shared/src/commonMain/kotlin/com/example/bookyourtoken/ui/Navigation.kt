package com.example.bookyourtoken.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bookyourtoken.AppContainer
import com.example.bookyourtoken.ui.common.LocalPlatformActions
import com.example.bookyourtoken.ui.mytokens.MyTokensScreen
import com.example.bookyourtoken.ui.mytokens.MyTokensViewModel
import com.example.bookyourtoken.ui.qr.QrScreen
import com.example.bookyourtoken.ui.qr.QrViewModel
import com.example.bookyourtoken.ui.settings.SettingsScreen
import com.example.bookyourtoken.ui.settings.SettingsViewModel
import com.example.bookyourtoken.ui.setup.SetupScreen
import com.example.bookyourtoken.ui.setup.SetupViewModel
import com.example.bookyourtoken.ui.theme.HostelTheme
import com.example.bookyourtoken.ui.tokens.TokensScreen
import com.example.bookyourtoken.ui.tokens.TokensViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object Routes {
    const val SETUP = "setup"
    const val TOKENS = "tokens"
    const val MY_TOKENS = "my_tokens"
    const val SETTINGS = "settings"
    const val QR = "qr"
}

/** Set on the Tokens back-stack entry by My Tokens after a cancel changed the booked list. */
private const val KEY_TOKENS_CHANGED = "tokens_changed"

/**
 * The whole app UI, shared by Android (MainActivity) and iOS (MainViewController).
 *
 * [startOnQr] opens straight on the QR screen (Android's "Show QR" shortcut and notification), so the
 * Tokens screen doesn't also start loading behind it. Each increase of [openQrRequest] opens the QR
 * screen in an app that's already running.
 */
@Composable
fun App(container: AppContainer, startOnQr: Boolean = false, openQrRequest: Int = 0) {
    CompositionLocalProvider(LocalPlatformActions provides container.platform) {
        HostelTheme {
            AppNavHost(container, startOnQr, openQrRequest)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AppNavHost(container: AppContainer, startOnQr: Boolean, openQrRequest: Int) {
    val navController = rememberNavController()
    val startDestination = remember {
        when {
            !container.credentials.hasCredentials() -> Routes.SETUP
            startOnQr -> Routes.QR
            else -> Routes.TOKENS
        }
    }

    // The offline QR is a redemption credential: drop it as soon as it no longer covers today.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { container.qr.deleteIfStale() }
    }

    LaunchedEffect(openQrRequest) {
        if (openQrRequest > 0 && container.credentials.hasCredentials()) {
            navController.navigate(Routes.QR) { launchSingleTop = true }
        }
    }

    val openQr = { navController.navigate(Routes.QR) { launchSingleTop = true } }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SETUP) {
            SetupScreen(
                onSaved = {
                    navController.navigate(Routes.TOKENS) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                },
                viewModel = viewModel { SetupViewModel(container) }
            )
        }
        composable(Routes.TOKENS) { entry ->
            val tokensChanged by entry.savedStateHandle
                .getStateFlow(KEY_TOKENS_CHANGED, false)
                .collectAsStateWithLifecycle()
            TokensScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenMyTokens = { navController.navigate(Routes.MY_TOKENS) },
                onOpenQr = openQr,
                tokensChanged = tokensChanged,
                onTokensChangedHandled = { entry.savedStateHandle[KEY_TOKENS_CHANGED] = false },
                viewModel = viewModel { TokensViewModel(container) }
            )
        }
        composable(Routes.MY_TOKENS) {
            MyTokensScreen(
                onBack = { navController.popBackStack() },
                onTokensChanged = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(KEY_TOKENS_CHANGED, true)
                },
                onOpenQr = openQr,
                viewModel = viewModel { MyTokensViewModel(container) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(Routes.SETUP) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                viewModel = viewModel { SettingsViewModel(container) }
            )
        }
        composable(Routes.QR) {
            // Opened from a shortcut or notification it's the only screen: back goes on to Tokens
            // rather than closing the app.
            val isOnlyScreen = navController.previousBackStackEntry == null
            val toTokens = {
                navController.navigate(Routes.TOKENS) {
                    popUpTo(Routes.QR) { inclusive = true }
                }
            }
            BackHandler(enabled = isOnlyScreen, onBack = toTokens)
            QrScreen(
                onBack = { if (isOnlyScreen) toTokens() else navController.popBackStack() },
                viewModel = viewModel { QrViewModel(container) }
            )
        }
    }
}
