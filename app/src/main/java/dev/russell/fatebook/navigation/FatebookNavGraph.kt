package dev.russell.fatebook.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.ui.create.CreateScreen
import dev.russell.fatebook.ui.feed.FeedScreen
import dev.russell.fatebook.ui.settings.SettingsScreen

@Composable
fun FatebookNavGraph(
    userPreferences: UserPreferences,
    openCreate: Boolean = false,
) {
    val navController = rememberNavController()
    val startDestination = if (userPreferences.hasApiKey) Routes.FEED else Routes.SETTINGS

    LaunchedEffect(openCreate) {
        if (openCreate && startDestination == Routes.FEED) {
            navController.navigate(Routes.CREATE)
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.FEED) {
            FeedScreen(
                onCreateClick = { navController.navigate(Routes.CREATE) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.CREATE) {
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
