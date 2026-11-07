package com.elianiva.jisholens.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.elianiva.jisholens.data.AppContainer
import com.elianiva.jisholens.ui.components.DictionaryWarningBanner
import com.elianiva.jisholens.ui.components.LogoBar
import com.elianiva.jisholens.ui.screens.AboutScreen
import com.elianiva.jisholens.ui.screens.DictionariesScreen
import com.elianiva.jisholens.ui.screens.HomeScreen
import com.elianiva.jisholens.ui.screens.OnboardingScreen
import com.elianiva.jisholens.ui.screens.ScanScreen
import com.elianiva.jisholens.ui.screens.SearchScreen
import com.elianiva.jisholens.ui.screens.SettingsScreen
import com.elianiva.jisholens.ui.theme.JishoLensTheme

private data class TopLevelDestination(
    val label: String,
    val icon: ImageVector,
    val route: Any,
)

private val destinations = listOf(
    TopLevelDestination("Home", Icons.Outlined.Home, HomeRoute),
    TopLevelDestination("Search", Icons.Outlined.Search, SearchRoute),
    TopLevelDestination("Settings", Icons.Outlined.Settings, SettingsRoute),
    TopLevelDestination("About", Icons.Outlined.Info, AboutRoute),
)

@Composable
fun AppRoot(
    container: AppContainer,
    pendingScanPath: String?,
) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val dictionaries by container.dictionaries.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        container.refreshDictionaries()
    }

    val loaded = settings
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    JishoLensTheme(
        darkTheme = loaded.theme.isDark(androidx.compose.foundation.isSystemInDarkTheme()),
    ) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val destination = backStackEntry?.destination
        val fullScreen = destination?.hasRoute<ScanRoute>() == true ||
            destination?.hasRoute<DictionariesRoute>() == true ||
            destination?.hasRoute<OnboardingRoute>() == true

        LaunchedEffect(pendingScanPath) {
            pendingScanPath?.let { navController.navigate(ScanRoute(it)) }
        }

        Scaffold(
            topBar = { if (!fullScreen) LogoBar() },
            bottomBar = {
                if (!fullScreen) {
                    Column {
                        if (dictionaries.none { it.enabled }) {
                            DictionaryWarningBanner(
                                onClick = { navController.navigate(DictionariesRoute) },
                            )
                        }
                        AppBottomBar(
                            navController = navController,
                            currentDestination = destination,
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = if (!loaded.onboardingComplete && dictionaries.isEmpty()) {
                    OnboardingRoute
                } else {
                    HomeRoute
                },
                modifier = if (fullScreen) Modifier else Modifier.padding(padding),
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        container = container,
                        onScan = { path -> navController.navigate(ScanRoute(path)) },
                        onOpenDictionaries = { navController.navigate(DictionariesRoute) },
                    )
                }
                composable<SearchRoute> {
                    SearchScreen(
                        container = container,
                        onOpenDictionaries = { navController.navigate(DictionariesRoute) },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        container = container,
                        onOpenDictionaries = { navController.navigate(DictionariesRoute) },
                    )
                }
                composable<AboutRoute> {
                    AboutScreen()
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
                        onImport = { navController.navigate(DictionariesRoute) },
                        onDone = {
                            navController.navigate(HomeRoute) {
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
        }
    }
}

@Composable
private fun AppBottomBar(
    navController: NavHostController,
    currentDestination: androidx.navigation.NavDestination?,
) {
    NavigationBar {
        destinations.forEachIndexed { index, destination ->
            val selected = when (index) {
                0 -> currentDestination?.hasRoute<HomeRoute>() == true
                1 -> currentDestination?.hasRoute<SearchRoute>() == true
                2 -> currentDestination?.hasRoute<SettingsRoute>() == true
                else -> currentDestination?.hasRoute<AboutRoute>() == true
            }
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}
