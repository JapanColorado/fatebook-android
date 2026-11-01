package dev.russell.fatebook.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.ui.analytics.AnalyticsScreen
import dev.russell.fatebook.ui.create.CreateScreen
import dev.russell.fatebook.ui.feed.FeedFilter
import dev.russell.fatebook.ui.feed.FeedScreen
import dev.russell.fatebook.ui.onboarding.OnboardingScreen
import dev.russell.fatebook.ui.settings.SettingsScreen

@Composable
fun FatebookNavGraph(
    userPreferences: UserPreferences,
    openCreate: Boolean = false,
    openResolveFilter: Boolean = false,
    openSoonFilter: Boolean = false,
    openQuestionId: String? = null,
    sharedText: String? = null,
) {
    val navController = rememberNavController()
    // Keyless users go through onboarding; anyone with a key (including users
    // upgrading from before onboarding existed) lands on the feed. A sync read
    // keeps the first frame right — no flash of the wrong screen.
    val startDestination = remember {
        if (userPreferences.hasApiKey) Routes.FEED else Routes.ONBOARDING
    }

    // If the key ever disappears while the app is running, drop back to onboarding.
    val hasApiKey by userPreferences.hasApiKeyFlow.collectAsStateWithLifecycle()
    LaunchedEffect(hasApiKey) {
        val current = navController.currentDestination?.route
        if (!hasApiKey && current != null && current != Routes.ONBOARDING) {
            navController.navigate(Routes.ONBOARDING) { popUpTo(0) { inclusive = true } }
        }
    }

    LaunchedEffect(openCreate, sharedText) {
        if ((openCreate || sharedText != null) && startDestination == Routes.FEED) {
            navController.navigate(Routes.create(sharedText))
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTab = LedgerTab.entries.firstOrNull { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (currentTab != null) {
                LedgerBottomBar(
                    selected = currentTab,
                    onSelect = { tab ->
                        navController.navigate(tab.route) {
                            // Feed is the root of every tab (onboarding pops itself on
                            // finish), so this holds each tab's state as you move between.
                            popUpTo(Routes.FEED) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onCreate = { navController.navigate(Routes.create()) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            enterTransition = { fadeIn(tween(TAB_FADE_MS)) },
            exitTransition = { fadeOut(tween(TAB_FADE_MS)) },
            popEnterTransition = { fadeIn(tween(TAB_FADE_MS)) },
            popExitTransition = { fadeOut(tween(TAB_FADE_MS)) },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(Routes.FEED) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                        // A shortcut/share that arrived keyless still gets honoured.
                        if (openCreate || sharedText != null) {
                            navController.navigate(Routes.create(sharedText))
                        }
                    },
                )
            }

            composable(Routes.FEED) {
                FeedScreen(
                    onSettingsClick = {
                        navController.navigate(Routes.SETTINGS) {
                            popUpTo(Routes.FEED) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    initialFilter = when {
                        openResolveFilter -> FeedFilter.READY_TO_RESOLVE
                        openSoonFilter -> FeedFilter.RESOLVING_SOON
                        else -> null
                    },
                    initialQuestionId = openQuestionId,
                )
            }

            composable(Routes.ANALYTICS) {
                AnalyticsScreen()
            }

            composable(
                route = Routes.CREATE,
                arguments = listOf(
                    navArgument("prefill") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
                enterTransition = { slideInVertically { it / 8 } + fadeIn(tween(TAB_FADE_MS)) },
                popExitTransition = { slideOutVertically { it / 8 } + fadeOut(tween(TAB_FADE_MS)) },
            ) {
                CreateScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.SETTINGS) {
                // A validated key no longer bounces to the feed: Settings is a tab
                // now, and the user may well carry on configuring.
                SettingsScreen(onBack = null)
            }
        }
    }
}

private const val TAB_FADE_MS = 150
