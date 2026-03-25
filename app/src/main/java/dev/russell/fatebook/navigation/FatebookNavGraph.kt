package dev.russell.fatebook.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.ui.create.CreateScreen
import dev.russell.fatebook.ui.feed.FeedScreen
import dev.russell.fatebook.ui.resolve.ResolveBottomSheet
import dev.russell.fatebook.ui.settings.SettingsScreen

@Composable
fun FatebookNavGraph(
    userPreferences: UserPreferences,
    onResolve: (String, Resolution) -> Unit,
) {
    val navController = rememberNavController()
    val startDestination = if (userPreferences.hasApiKey) Routes.FEED else Routes.SETTINGS

    var resolveQuestion by remember { mutableStateOf<Question?>(null) }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.FEED) {
            FeedScreen(
                onCreateClick = { navController.navigate(Routes.CREATE) },
                onQuestionClick = { question ->
                    if (question.isReadyToResolve) {
                        resolveQuestion = question
                    }
                },
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

    resolveQuestion?.let { question ->
        ResolveBottomSheet(
            question = question,
            onResolve = { resolution ->
                onResolve(question.id, resolution)
                resolveQuestion = null
            },
            onDismiss = { resolveQuestion = null },
        )
    }
}
