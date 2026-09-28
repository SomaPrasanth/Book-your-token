package com.example.bookyourtoken.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bookyourtoken.AppContainer
import com.example.bookyourtoken.ui.common.LocalPlatformActions
import com.example.bookyourtoken.ui.mytokens.MyTokensScreen
import com.example.bookyourtoken.ui.mytokens.MyTokensViewModel
import com.example.bookyourtoken.ui.settings.SettingsScreen
import com.example.bookyourtoken.ui.settings.SettingsViewModel
import com.example.bookyourtoken.ui.setup.SetupScreen
import com.example.bookyourtoken.ui.setup.SetupViewModel
import com.example.bookyourtoken.ui.theme.HostelTheme
import com.example.bookyourtoken.ui.tokens.TokensScreen
import com.example.bookyourtoken.ui.tokens.TokensViewModel

private object Routes {
    const val SETUP = "setup"
    const val TOKENS = "tokens"
    const val MY_TOKENS = "my_tokens"
    const val SETTINGS = "settings"
}

/** Set on the Tokens back-stack entry by My Tokens after a cancel changed the booked list. */
private const val KEY_TOKENS_CHANGED = "tokens_changed"

/** The whole app UI, shared by Android (MainActivity) and iOS (MainViewController). */
@Composable
fun App(container: AppContainer) {
    CompositionLocalProvider(LocalPlatformActions provides container.platform) {
        HostelTheme {
            AppNavHost(container)
        }
    }
}

@Composable
private fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val startDestination = remember {
        if (container.credentials.hasCredentials()) Routes.TOKENS else Routes.SETUP
    }

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
    }
}
