package dev.russell.fatebook.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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

    NavHost(navController = navController, startDestination = startDestination) {
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
                onCreateClick = { navController.navigate(Routes.create()) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onAnalyticsClick = { navController.navigate(Routes.ANALYTICS) },
                initialFilter = when {
                    openResolveFilter -> FeedFilter.READY_TO_RESOLVE
                    openSoonFilter -> FeedFilter.RESOLVING_SOON
                    else -> null
                },
                initialQuestionId = openQuestionId,
            )
        }

        composable(Routes.ANALYTICS) {
            AnalyticsScreen(
                onBack = { navController.popBackStack() },
            )
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
        ) {
            CreateScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = if (userPreferences.hasApiKey) {
                    { navController.popBackStack() }
                } else {
                    null
                },
                onValidated = {
                    navController.navigate(Routes.FEED) {
                        popUpTo(Routes.SETTINGS) { inclusive = true }
                    }
                },
            )
        }
    }
}
