package com.example.bookyourtoken.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bookyourtoken.data.CredentialStore
import com.example.bookyourtoken.ui.settings.SettingsScreen
import com.example.bookyourtoken.ui.setup.SetupScreen
import com.example.bookyourtoken.ui.tokens.TokensScreen

private object Routes {
    const val SETUP = "setup"
    const val TOKENS = "tokens"
    const val SETTINGS = "settings"
}

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
        composable(Routes.TOKENS) {
            TokensScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
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
