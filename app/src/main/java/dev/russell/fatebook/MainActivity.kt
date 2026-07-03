package dev.russell.fatebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.navigation.FatebookNavGraph
import dev.russell.fatebook.notification.NotificationHelper
import dev.russell.fatebook.ui.theme.FatebookTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openCreate = intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_CREATE, false)
        val openResolveFilter = intent.getBooleanExtra(
            NotificationHelper.EXTRA_OPEN_RESOLVE_FILTER, false,
        )
        val openQuestionId = intent.getStringExtra(NotificationHelper.EXTRA_QUESTION_ID)
        setContent {
            FatebookTheme {
                FatebookNavGraph(
                    userPreferences = userPreferences,
                    openCreate = openCreate,
                    openResolveFilter = openResolveFilter,
                    openQuestionId = openQuestionId,
                )
            }
        }
    }
}
