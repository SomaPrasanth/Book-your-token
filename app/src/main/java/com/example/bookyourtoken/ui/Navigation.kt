package com.example.bookyourtoken.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bookyourtoken.data.CredentialStore
import com.example.bookyourtoken.ui.mytokens.MyTokensScreen
import com.example.bookyourtoken.ui.settings.SettingsScreen
import com.example.bookyourtoken.ui.setup.SetupScreen
import com.example.bookyourtoken.ui.tokens.TokensScreen

private object Routes {
    const val SETUP = "setup"
    const val TOKENS = "tokens"
    const val MY_TOKENS = "my_tokens"
    const val SETTINGS = "settings"
}

/** Set on the Tokens back-stack entry by My Tokens after a cancel changed the booked list. */
private const val KEY_TOKENS_CHANGED = "tokens_changed"

@Composable
fun AppNavHost() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val startDestination = remember {
        if (CredentialStore(context).hasCredentials()) Routes.TOKENS else Routes.SETUP
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SETUP) {
            SetupScreen(
                onSaved = {
                    navController.navigate(Routes.TOKENS) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                }
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
                onTokensChangedHandled = { entry.savedStateHandle[KEY_TOKENS_CHANGED] = false }
            )
        }
        composable(Routes.MY_TOKENS) {
            MyTokensScreen(
                onBack = { navController.popBackStack() },
                onTokensChanged = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(KEY_TOKENS_CHANGED, true)
                }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(Routes.SETUP) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            )
        }
    }
}
