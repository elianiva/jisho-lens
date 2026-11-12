package com.elianiva.jisholens.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.elianiva.jisholens.data.AppContainer
import com.elianiva.jisholens.ui.components.ComponentDownloadPill
import com.elianiva.jisholens.ui.screens.DictionariesScreen
import com.elianiva.jisholens.ui.screens.OnboardingScreen
import com.elianiva.jisholens.ui.screens.ScanHomeScreen
import com.elianiva.jisholens.ui.screens.ScanScreen
import com.elianiva.jisholens.ui.screens.SearchScreen
import com.elianiva.jisholens.ui.screens.SettingsScreen
import com.elianiva.jisholens.ui.theme.JishoLensTheme

/** Looking a word up is the core loop, so it opens first; scanning and settings sit beside it. */
enum class TopLevel(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val route: Any,
) {
    Lookup("Look up", Icons.Outlined.Search, Icons.Rounded.Search, SearchRoute),
    Scan("Scan", Icons.Outlined.CameraAlt, Icons.Rounded.CameraAlt, ScanHomeRoute),
    Settings("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings, SettingsRoute),
}

private fun NavDestination?.topLevel(): TopLevel? = when {
    this == null -> null
    hasRoute<SearchRoute>() -> TopLevel.Lookup
    hasRoute<ScanHomeRoute>() -> TopLevel.Scan
    hasRoute<SettingsRoute>() -> TopLevel.Settings
    else -> null
}

private const val MEDIUM_BREAKPOINT_DP = 600
private const val EXPANDED_BREAKPOINT_DP = 840

@Composable
fun AppRoot(
    container: AppContainer,
    pendingScanPath: String?,
    onScanConsumed: () -> Unit = {},
) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val dictionaries by container.dictionaries.collectAsStateWithLifecycle()
    val components by container.components.statuses.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        container.refreshDictionaries()
    }

    val loaded = settings
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        return
    }

    JishoLensTheme(
        darkTheme = loaded.theme.isDark(isSystemInDarkTheme()),
        dynamicColor = loaded.dynamicColor,
    ) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val current = backStackEntry?.destination.topLevel()
        val widthDp = LocalConfiguration.current.screenWidthDp
        val wide = widthDp >= EXPANDED_BREAKPOINT_DP
        val medium = widthDp >= MEDIUM_BREAKPOINT_DP

        LaunchedEffect(pendingScanPath) {
            pendingScanPath?.let {
                navController.navigate(ScanRoute(it))
                onScanConsumed()
            }
        }

        val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val slide = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()

        // Tabs cross-fade with a small scale, as siblings. Pushed screens slide over the top.
        fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
            if (targetState.destination.topLevel() != null) {
                fadeIn(effects) + scaleIn(spatial, initialScale = 0.94f)
            } else {
                slideInHorizontally(slide) { it / 4 } + fadeIn(effects)
            }

        fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
            if (targetState.destination.topLevel() != null && initialState.destination.topLevel() != null) {
                fadeOut(effects)
            } else {
                fadeOut(effects) + scaleOut(spatial, targetScale = 0.96f)
            }

        fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
            slideOutHorizontally(slide) { it / 4 } + fadeOut(effects)

        val barVisible = current != null
        Scaffold(
            bottomBar = {
                if (barVisible && !wide) {
                    ShortNavigationBar {
                        TopLevel.entries.forEach { item ->
                            ShortNavigationBarItem(
                                selected = current == item,
                                onClick = { navController.openTopLevel(item) },
                                icon = {
                                    Icon(
                                        imageVector = if (current == item) item.selectedIcon else item.icon,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(item.label) },
                                iconPosition = if (medium) {
                                    NavigationItemIconPosition.Start
                                } else {
                                    NavigationItemIconPosition.Top
                                },
                            )
                        }
                    }
                }
            },
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (barVisible && wide) {
                    WideNavigationRail {
                        TopLevel.entries.forEach { item ->
                            WideNavigationRailItem(
                                selected = current == item,
                                onClick = { navController.openTopLevel(item) },
                                icon = {
                                    Icon(
                                        imageVector = if (current == item) item.selectedIcon else item.icon,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(item.label) },
                                railExpanded = false,
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = if (!loaded.onboardingComplete && dictionaries.isEmpty()) {
                            OnboardingRoute
                        } else {
                            SearchRoute
                        },
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { enter() },
                        exitTransition = { exit() },
                        popEnterTransition = { fadeIn(effects) + scaleIn(spatial, initialScale = 0.96f) },
                        popExitTransition = { popExit() },
                    ) {
                        composable<SearchRoute> {
                            SearchScreen(
                                container = container,
                                onOpenDictionaries = { navController.navigate(DictionariesRoute) },
                            )
                        }
                        composable<ScanHomeRoute> {
                            ScanHomeScreen(
                                container = container,
                                onScan = { path -> navController.navigate(ScanRoute(path)) },
                                onOpenDictionaries = { navController.navigate(DictionariesRoute) },
                            )
                        }
                        composable<SettingsRoute> {
                            SettingsScreen(
                                container = container,
                                onOpenDictionaries = { navController.navigate(DictionariesRoute) },
                            )
                        }
                        composable<DictionariesRoute> {
                            DictionariesScreen(
                                container = container,
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable<OnboardingRoute> {
                            OnboardingScreen(
                                container = container,
                                onDone = {
                                    navController.navigate(SearchRoute) {
                                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        composable<ScanRoute> { entry ->
                            val route = entry.toRoute<ScanRoute>()
                            ScanScreen(
                                container = container,
                                imagePath = route.imagePath,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    // The frozen reader is a full-screen view with its own status, so it
                    // keeps the downloads to itself.
                    val readerOpen = backStackEntry?.destination?.hasRoute<ScanRoute>() == true
                    if (!readerOpen) {
                        ComponentDownloadPill(
                            statuses = components,
                            onRetry = container.components::retry,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .then(if (barVisible && !wide) Modifier else Modifier.navigationBarsPadding())
                                .padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun NavHostController.openTopLevel(item: TopLevel) {
    navigate(item.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
