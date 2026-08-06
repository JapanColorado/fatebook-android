package dev.russell.fatebook

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.navigation.FatebookNavGraph
import dev.russell.fatebook.notification.NotificationHelper
import dev.russell.fatebook.ui.theme.FatebookTheme
import dev.russell.fatebook.widget.WidgetRefresher
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var widgetRefresher: WidgetRefresher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Opening the app is a cheap moment to freshen the widget — but only on
        // a real launch, not on rotation/theme-change recreations.
        if (savedInstanceState == null) {
            lifecycleScope.launch { runCatching { widgetRefresher.refresh() } }
        }
        val openCreate = intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_CREATE, false)
        val openResolveFilter = intent.getBooleanExtra(
            NotificationHelper.EXTRA_OPEN_RESOLVE_FILTER, false,
        )
        val openQuestionId = intent.getStringExtra(NotificationHelper.EXTRA_QUESTION_ID)
        val sharedText = extractSharedText(intent)
        setContent {
            FatebookTheme {
                FatebookNavGraph(
                    userPreferences = userPreferences,
                    openCreate = openCreate,
                    openResolveFilter = openResolveFilter,
                    openQuestionId = openQuestionId,
                    sharedText = sharedText,
                )
            }
        }
    }

    /** ACTION_SEND text from another app becomes the new question's title. */
    private fun extractSharedText(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(200)
    }
}
