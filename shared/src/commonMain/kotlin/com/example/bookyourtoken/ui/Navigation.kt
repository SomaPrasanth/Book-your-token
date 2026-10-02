package com.example.bookyourtoken.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.bookyourtoken.AppContainer
import com.example.bookyourtoken.ui.common.AppIcons
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

/** The four sections, always visible as labelled tabs once signed in — no hidden icon menus. */
private enum class Tab(val route: String, val label: String, val icon: () -> ImageVector) {
    Book(Routes.TOKENS, "Book", { AppIcons.Restaurant }),
    Booked(Routes.MY_TOKENS, "Booked", { AppIcons.ConfirmationNumber }),
    Qr(Routes.QR, "QR", { AppIcons.QrCode }),
    Settings(Routes.SETTINGS, "Settings", { Icons.Filled.Settings })
}

/** Set on the Tokens back-stack entry by My Tokens after a cancel changed the booked list. */
private const val KEY_TOKENS_CHANGED = "tokens_changed"

/**
 * The whole app UI, shared by Android (MainActivity) and iOS (MainViewController).
 *
 * [startOnQr] opens straight on the QR tab (Android's "Show QR" shortcut and notification), so the
 * Book tab doesn't also start loading behind it. Each increase of [openQrRequest] switches an app
 * that's already running to the QR tab.
 */
@Composable
fun App(container: AppContainer, startOnQr: Boolean = false, openQrRequest: Int = 0) {
    CompositionLocalProvider(LocalPlatformActions provides container.platform) {
        HostelTheme {
            AppNavHost(container, startOnQr, openQrRequest)
        }
    }
}

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
    // The tab at the bottom of the back stack. Switching tabs returns to it first, so Back from any
    // other tab lands there and the stack never grows.
    var rootRoute by remember { mutableStateOf(startDestination) }
    // Upcoming booked tokens, shown as a badge on the Booked tab. Reported by Book and Booked.
    var upcomingCount by remember { mutableIntStateOf(0) }

    val openTab: (Tab) -> Unit = { tab -> navController.openTab(tab.route, rootRoute) }

    // The offline QR is a redemption credential: drop it as soon as it no longer covers today.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { container.qr.deleteIfStale() }
    }

    LaunchedEffect(openQrRequest) {
        if (openQrRequest > 0 && container.credentials.hasCredentials()) openTab(Tab.Qr)
    }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val currentTab = Tab.entries.firstOrNull { it.route == currentRoute }

    Scaffold(
        // Each screen draws its own top bar and handles the status bar itself.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentTab != null) {
                AppNavigationBar(currentTab, upcomingCount, onSelect = openTab)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            composable(Routes.SETUP) {
                SetupScreen(
                    onSaved = {
                        rootRoute = Routes.TOKENS
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
                    onOpenMyTokens = { openTab(Tab.Booked) },
                    onOpenQr = { openTab(Tab.Qr) },
                    onUpcomingCount = { upcomingCount = it },
                    tokensChanged = tokensChanged,
                    onTokensChangedHandled = { entry.savedStateHandle[KEY_TOKENS_CHANGED] = false },
                    viewModel = viewModel { TokensViewModel(container) }
                )
            }
            composable(Routes.MY_TOKENS) {
                MyTokensScreen(
                    onTokensChanged = {
                        // Only matters if Book is alive underneath; otherwise it loads fresh anyway.
                        runCatching { navController.getBackStackEntry(Routes.TOKENS) }.getOrNull()
                            ?.savedStateHandle?.set(KEY_TOKENS_CHANGED, true)
                    },
                    onOpenQr = { openTab(Tab.Qr) },
                    onUpcomingCount = { upcomingCount = it },
                    viewModel = viewModel { MyTokensViewModel(container) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onSignedOut = {
                        rootRoute = Routes.SETUP
                        upcomingCount = 0
                        navController.navigate(Routes.SETUP) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    },
                    viewModel = viewModel { SettingsViewModel(container) }
                )
            }
            composable(Routes.QR) {
                QrScreen(viewModel = viewModel { QrViewModel(container) })
            }
        }
    }
}

/**
 * Pops back to [rootRoute] and opens [route] on top of it (or just shows the root). Every visit to a
 * non-root tab is a fresh screen, so Booked and QR always show the portal's current state.
 */
private fun NavHostController.openTab(route: String, rootRoute: String) {
    if (currentBackStackEntry?.destination?.route == route) return
    if (route == rootRoute) {
        popBackStack(rootRoute, inclusive = false)
    } else {
        navigate(route) {
            popUpTo(rootRoute)
            launchSingleTop = true
        }
    }
}

@Composable
private fun AppNavigationBar(current: Tab, upcomingCount: Int, onSelect: (Tab) -> Unit) {
    NavigationBar {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { onSelect(tab) },
                label = { Text(tab.label) },
                icon = {
                    if (tab == Tab.Booked && upcomingCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    modifier = Modifier.semantics {
                                        contentDescription = "$upcomingCount upcoming"
                                    }
                                ) { Text(upcomingCount.toString()) }
                            }
                        ) {
                            Icon(tab.icon(), contentDescription = null)
                        }
                    } else {
                        Icon(tab.icon(), contentDescription = null)
                    }
                }
            )
        }
    }
}
