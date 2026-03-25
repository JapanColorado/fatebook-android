package dev.russell.fatebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.navigation.FatebookNavGraph
import dev.russell.fatebook.ui.theme.FatebookTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var repository: QuestionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FatebookTheme {
                FatebookNavGraph(
                    userPreferences = userPreferences,
                    onResolve = { questionId, resolution ->
                        lifecycleScope.launch {
                            try {
                                repository.resolveQuestion(questionId, resolution)
                            } catch (_: Exception) {
                                // TODO: surface error to user
                            }
                        }
                    },
                )
            }
        }
    }
}
