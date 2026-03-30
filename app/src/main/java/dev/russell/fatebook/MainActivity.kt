package dev.russell.fatebook

import android.content.Intent
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
        val deepLinkQuestionId = parseDeepLink(intent)
        setContent {
            FatebookTheme {
                FatebookNavGraph(
                    userPreferences = userPreferences,
                    openCreate = openCreate,
                    deepLinkQuestionId = deepLinkQuestionId,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val deepLinkQuestionId = parseDeepLink(intent)
        if (deepLinkQuestionId != null) {
            // Re-compose with the new deep link
            setContent {
                FatebookTheme {
                    FatebookNavGraph(
                        userPreferences = userPreferences,
                        deepLinkQuestionId = deepLinkQuestionId,
                    )
                }
            }
        }
    }

    private fun parseDeepLink(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.host != "fatebook.io") return null
        val segments = uri.pathSegments
        if (segments.firstOrNull() != "q" || segments.size < 2) return null
        return segments[1] // full slug like "will-it-rain--clq1abc123"
    }
}
