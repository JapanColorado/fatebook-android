package dev.russell.fatebook.screenshot

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import dev.russell.fatebook.domain.model.Comment
import dev.russell.fatebook.domain.model.Forecast
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.QuestionType
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.testutil.TestData
import dev.russell.fatebook.ui.detail.QuestionDetailContent
import dev.russell.fatebook.ui.feed.DetailSheetState
import dev.russell.fatebook.ui.theme.FatebookTheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** The sheet's body on the sheet's surface, without the sheet's window. */
@Composable
private fun DetailContentTestHost(
    question: Question,
    state: DetailSheetState,
    darkTheme: Boolean = false,
) {
    FatebookTheme(darkTheme = darkTheme) {
        Surface {
            QuestionDetailContent(question = question, detailState = state)
        }
    }
}

class DetailSheetScreenshotTest {

    // V_SCROLL: the sheet's body is taller than a screen, and the lower sections matter.
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        renderingMode = SessionParams.RenderingMode.V_SCROLL,
    )

    private val activeBinary = TestData.question(
        title = "Will GPT-5 be released by the end of the decade?",
        yourLatestForecast = 0.7,
        resolveBy = Instant.parse("2030-12-31T00:00:00Z"),
        tags = listOf("ai", "tech"),
    )

    private val comments = listOf(
        Comment(
            id = "c1",
            userId = "u1",
            userName = "Russell",
            comment = "Base rate looks higher than I first assumed.",
            createdAt = Instant.parse("2030-02-03T12:00:00Z"),
        ),
        Comment(
            id = "c2",
            userId = "u2",
            userName = "Sam",
            comment = "Disagree, but we'll see.",
            createdAt = Instant.parse("2030-02-05T12:00:00Z"),
        ),
    )

    @Test
    fun detail_activeBinary() {
        paparazzi.snapshot {
            DetailContentTestHost(
                question = activeBinary,
                state = DetailSheetState(question = activeBinary, forecastSliderValue = 0.7f),
            )
        }
    }

    @Test
    fun detail_readyToResolveBinary() {
        val question = TestData.question(
            title = "Will the CI run go green on the first try?",
            yourLatestForecast = 0.3,
            // In the past, so it stays due regardless of when the test runs.
            resolveBy = Instant.parse("2024-03-02T00:00:00Z"),
        )
        paparazzi.snapshot {
            DetailContentTestHost(question = question, state = DetailSheetState(question = question))
        }
    }

    @Test
    fun detail_resolvedWithHistory() {
        val question = TestData.question(
            title = "Will I finish this book by Friday?",
            yourLatestForecast = 0.85,
            createdAt = Instant.parse("2030-01-01T12:00:00Z"),
            resolveBy = Instant.parse("2030-03-01T00:00:00Z"),
            resolved = true,
            resolution = Resolution.YES,
            resolvedAt = Instant.parse("2030-03-01T12:00:00Z"),
        )
        val history = listOf(
            Forecast("me", 0.4, Instant.parse("2030-01-01T12:00:00Z"), "Russell"),
            Forecast("sam", 0.2, Instant.parse("2030-01-10T12:00:00Z"), "Sam"),
            Forecast("me", 0.65, Instant.parse("2030-01-20T12:00:00Z"), "Russell"),
            Forecast("me", 0.85, Instant.parse("2030-02-15T12:00:00Z"), "Russell"),
        )
        paparazzi.snapshot {
            DetailContentTestHost(
                question = question,
                state = DetailSheetState(
                    question = question,
                    forecastHistory = history,
                    currentUserId = "me",
                    comments = comments,
                ),
            )
        }
    }

    private val activeExclusiveMc = TestData.question(
        title = "Which framework will we pick?",
        type = QuestionType.MULTIPLE_CHOICE,
        yourLatestForecast = null,
        latestForecastAt = null,
        resolveBy = Instant.parse("2030-12-31T00:00:00Z"),
        options = listOf(
            TestData.questionOption(id = "o1", text = "Compose", latestForecast = 0.55),
            TestData.questionOption(id = "o2", text = "Flutter", latestForecast = 0.3),
            TestData.questionOption(id = "o3", text = "React Native", latestForecast = 0.15),
        ),
    )

    @Test
    fun detail_activeExclusiveMc() {
        paparazzi.snapshot {
            DetailContentTestHost(
                question = activeExclusiveMc,
                state = DetailSheetState(
                    question = activeExclusiveMc,
                    optionDrafts = mapOf("o1" to 55, "o2" to 30, "o3" to 15),
                ),
            )
        }
    }

    @Test
    fun detail_activeBinary_dark() {
        paparazzi.snapshot {
            DetailContentTestHost(
                question = activeBinary,
                state = DetailSheetState(
                    question = activeBinary,
                    forecastSliderValue = 0.7f,
                    comments = comments,
                ),
                darkTheme = true,
            )
        }
    }

    @Test
    fun detail_readyToResolveExclusiveMc() {
        val question = activeExclusiveMc.copy(
            resolveBy = Instant.parse("2024-03-02T00:00:00Z"),
        )
        paparazzi.snapshot {
            DetailContentTestHost(question = question, state = DetailSheetState(question = question))
        }
    }

    @Test
    fun detail_activeExclusiveMc_leftForOtherAndUnset_dark() {
        // Flutter was 30: the row shows the change. React Native has no forecast yet.
        val question = activeExclusiveMc.copy(
            options = activeExclusiveMc.options.map { option ->
                if (option.id == "o3") option.copy(latestForecast = null) else option
            },
        )
        paparazzi.snapshot {
            DetailContentTestHost(
                question = question,
                state = DetailSheetState(
                    question = question,
                    optionDrafts = mapOf("o1" to 55, "o2" to 20),
                ),
                darkTheme = true,
            )
        }
    }

    @Test
    fun detail_activeExclusiveMc_overAllocated() {
        paparazzi.snapshot {
            DetailContentTestHost(
                question = activeExclusiveMc,
                state = DetailSheetState(
                    question = activeExclusiveMc,
                    optionDrafts = mapOf("o1" to 67, "o2" to 30, "o3" to 15),
                ),
            )
        }
    }

    @Test
    fun detail_activeNonExclusiveMc() {
        // Independent options: no tally, and they can total more than 100%.
        val question = activeExclusiveMc.copy(
            exclusiveAnswers = false,
            options = activeExclusiveMc.options.map { option ->
                if (option.id == "o3") option.copy(resolution = Resolution.NO) else option
            },
        )
        paparazzi.snapshot {
            DetailContentTestHost(
                question = question,
                state = DetailSheetState(
                    question = question,
                    optionDrafts = mapOf("o1" to 80, "o2" to 60),
                ),
            )
        }
    }

    @Test
    fun detail_editMode() {
        paparazzi.snapshot {
            DetailContentTestHost(
                question = activeBinary,
                state = DetailSheetState(
                    question = activeBinary,
                    isEditing = true,
                    editTitle = activeBinary.title,
                    editResolveBy = java.time.LocalDate.of(2030, 12, 31),
                    editNotes = "Counting any model with that name.",
                ),
            )
        }
    }
}
