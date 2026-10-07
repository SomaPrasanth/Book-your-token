package com.example.bookyourtoken.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.bookyourtoken.AppContainer
import com.example.bookyourtoken.ui.ahead.BookAheadScreen
import com.example.bookyourtoken.ui.ahead.BookAheadViewModel
import com.example.bookyourtoken.ui.common.AppIcons
import com.example.bookyourtoken.ui.common.LocalPlatformActions
import com.example.bookyourtoken.ui.leave.LeaveApplyScreen
import com.example.bookyourtoken.ui.leave.LeaveApplyViewModel
import com.example.bookyourtoken.ui.leave.LeaveScreen
import com.example.bookyourtoken.ui.leave.LeaveViewModel
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
import com.example.bookyourtoken.ui.update.UpdateDialogHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object Routes {
    const val SETUP = "setup"
    const val TOKENS = "tokens"
    const val AHEAD = "book_ahead"
    const val MY_TOKENS = "my_tokens"
    const val SETTINGS = "settings"
    const val QR = "qr"
    const val LEAVE = "leave"
    const val LEAVE_APPLY = "leave_apply"
}

/** The sections, always visible as labelled tabs once signed in — no hidden icon menus. */
private enum class Tab(
    val route: String,
    val label: String,
    val icon: () -> ImageVector,
    /** Read by screen readers when the label is shortened to fit the bar. */
    val spokenLabel: String = label
) {
    Tomorrow(Routes.TOKENS, "Tomorrow", { AppIcons.Restaurant }),
    Ahead(Routes.AHEAD, "Ahead", { Icons.Filled.DateRange }, spokenLabel = "Book ahead"),
    Booked(Routes.MY_TOKENS, "Booked", { AppIcons.ConfirmationNumber }),
    Qr(Routes.QR, "QR", { AppIcons.QrCode }),
    Leave(Routes.LEAVE, "Leave", { AppIcons.Luggage }),
    Settings(Routes.SETTINGS, "Settings", { Icons.Filled.Settings })
}

/** Set on the Tokens back-stack entry when another screen booked or cancelled something. */
private const val KEY_TOKENS_CHANGED = "tokens_changed"

/** Set on the Leave back-stack entry when the apply form sent a request. */
private const val KEY_LEAVE_CHANGED = "leave_changed"

/**
 * The whole app UI, shared by Android (MainActivity) and iOS (MainViewController).
 *
 * [startOnQr] opens straight on the QR tab (Android's "Show QR" shortcut and notification), so the
 * Tomorrow tab doesn't also start loading behind it. Each increase of [openQrRequest] switches an app
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
    // Upcoming booked tokens, shown as a badge on the Booked tab. Reported by the booking tabs and Booked.
    var upcomingCount by remember { mutableIntStateOf(0) }
    // Items picked on Book ahead but not booked yet. They live only in that screen, so leaving asks first.
    var aheadPicked by remember { mutableIntStateOf(0) }
    var pendingLeave by remember { mutableStateOf<(() -> Unit)?>(null) }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val currentTab = Tab.entries.firstOrNull { it.route == currentRoute }

    val openTab: (Tab) -> Unit = { tab ->
        val go = { navController.openTab(tab.route, rootRoute) }
        if (currentTab == Tab.Ahead && tab != Tab.Ahead && aheadPicked > 0) pendingLeave = go else go()
    }

    // Tomorrow is alive under the other tabs: have it reload when they book or cancel something.
    val markTokensChanged = {
        runCatching { navController.getBackStackEntry(Routes.TOKENS) }.getOrNull()
            ?.savedStateHandle?.set(KEY_TOKENS_CHANGED, true)
    }

    // The offline QR is a redemption credential: drop it as soon as it no longer covers today.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { container.qr.deleteIfStale() }
    }

    // "Updated to 1.3.0", once, on the first launch after an update (Android only).
    val rootSnackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        container.updater?.takeUpdatedMessage()?.let { rootSnackbar.showSnackbar(it) }
    }

    LaunchedEffect(openQrRequest) {
        if (openQrRequest > 0 && container.credentials.hasCredentials()) openTab(Tab.Qr)
    }

    Scaffold(
        // Each screen draws its own top bar and handles the status bar itself.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(rootSnackbar) },
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
            composable(Routes.AHEAD) {
                BookAheadScreen(
                    onOpenMyTokens = { openTab(Tab.Booked) },
                    onUpcomingCount = { upcomingCount = it },
                    onSelectionCount = { aheadPicked = it },
                    onBackWithSelections = { pendingLeave = { navController.popBackStack() } },
                    onBooked = { markTokensChanged() },
                    viewModel = viewModel { BookAheadViewModel(container) }
                )
            }
            composable(Routes.MY_TOKENS) {
                MyTokensScreen(
                    onTokensChanged = { markTokensChanged() },
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
            composable(Routes.LEAVE) { entry ->
                val leaveChanged by entry.savedStateHandle
                    .getStateFlow(KEY_LEAVE_CHANGED, false)
                    .collectAsStateWithLifecycle()
                LeaveScreen(
                    onApply = { navController.navigate(Routes.LEAVE_APPLY) { launchSingleTop = true } },
                    historyChanged = leaveChanged,
                    onHistoryChangedHandled = { entry.savedStateHandle[KEY_LEAVE_CHANGED] = false },
                    viewModel = viewModel { LeaveViewModel(container) }
                )
            }
            // Over the Leave tab, without the bottom bar. Back returns to the history.
            composable(Routes.LEAVE_APPLY) {
                LeaveApplyScreen(
                    onSent = {
                        runCatching { navController.getBackStackEntry(Routes.LEAVE) }.getOrNull()
                            ?.savedStateHandle?.set(KEY_LEAVE_CHANGED, true)
                    },
                    onClose = {
                        if (navController.currentBackStackEntry?.destination?.route == Routes.LEAVE_APPLY) {
                            navController.popBackStack()
                        }
                    },
                    viewModel = viewModel { LeaveApplyViewModel(container) }
                )
            }
        }
    }

    container.updater?.let { UpdateDialogHost(it) }

    pendingLeave?.let { leave ->
        AlertDialog(
            onDismissRequest = { pendingLeave = null },
            title = { Text(if (aheadPicked == 1) "Discard 1 selected item?" else "Discard $aheadPicked selected items?") },
            text = { Text("Your Book ahead selections haven't been booked yet.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingLeave = null
                    leave()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { pendingLeave = null }) { Text("Keep selecting") } }
        )
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
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary
                ),
                label = {
                    // Six tabs: on narrow phones "Tomorrow" shrinks a little rather than being cut off.
                    Text(
                        tab.label,
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
                        modifier = Modifier.semantics { contentDescription = tab.spokenLabel }
                    )
                },
                icon = {
                    if (tab == Tab.Booked && upcomingCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
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
