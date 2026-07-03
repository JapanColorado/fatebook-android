package dev.russell.fatebook.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.russell.fatebook.ui.settings.SettingsScreen

@Composable
fun FatebookNavGraph(
    userPreferences: UserPreferences,
    openCreate: Boolean = false,
    openResolveFilter: Boolean = false,
    openQuestionId: String? = null,
    sharedText: String? = null,
) {
    val navController = rememberNavController()
    val startDestination = if (userPreferences.hasApiKey) Routes.FEED else Routes.SETTINGS

    LaunchedEffect(openCreate, sharedText) {
        if ((openCreate || sharedText != null) && startDestination == Routes.FEED) {
            navController.navigate(Routes.create(sharedText))
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.FEED) {
            FeedScreen(
                onCreateClick = { navController.navigate(Routes.create()) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onAnalyticsClick = { navController.navigate(Routes.ANALYTICS) },
                initialFilter = if (openResolveFilter) FeedFilter.READY_TO_RESOLVE else null,
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
