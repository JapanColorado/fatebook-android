package dev.russell.fatebook.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.QuestionOption
import dev.russell.fatebook.domain.model.QuestionType
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.ui.components.ProbabilitySlider
import dev.russell.fatebook.ui.feed.DueRow
import dev.russell.fatebook.ui.feed.QuickAction
import dev.russell.fatebook.ui.theme.FatebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/** Real pointer input, which the JVM screenshot tests can't exercise. */
@RunWith(AndroidJUnit4::class)
class LedgerGesturesTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 3, 14)

    private fun question(type: QuestionType = QuestionType.BINARY) = Question(
        id = "q",
        title = "Due question",
        resolveBy = Instant.parse("2026-03-02T00:00:00Z"),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        resolution = null,
        resolved = false,
        yourLatestForecast = 0.7,
        latestForecastAt = null,
        forecasts = emptyList(),
        url = "",
        type = type,
        options = if (type == QuestionType.MULTIPLE_CHOICE) {
            listOf(QuestionOption(id = "o", text = "Option", latestForecast = 0.5))
        } else {
            emptyList()
        },
    )

    private fun dueRow(question: Question): MutableList<QuickAction> {
        val actions = mutableListOf<QuickAction>()
        compose.setContent {
            FatebookTheme(darkTheme = false) {
                DueRow(question, today, {}, { _, action -> actions += action })
            }
        }
        return actions
    }

    @Test
    fun swipeRightResolvesYes() {
        val actions = dueRow(question())
        compose.onNodeWithText("Due question").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(listOf(QuickAction.Resolve(Resolution.YES)), actions)
    }

    @Test
    fun swipeLeftResolvesNo() {
        val actions = dueRow(question())
        compose.onNodeWithText("Due question").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(listOf(QuickAction.Resolve(Resolution.NO)), actions)
    }

    @Test
    fun multipleChoiceSwipeOnlySnoozes() {
        val actions = dueRow(question(QuestionType.MULTIPLE_CHOICE))
        compose.onNodeWithText("Due question").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(emptyList<QuickAction>(), actions)
        compose.onNodeWithText("Due question").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(listOf<QuickAction>(QuickAction.SnoozeWeek), actions)
    }

    @Test
    fun rowSnapsBackAfterSwipeSoUndoRestoresIt() {
        dueRow(question())
        compose.onNodeWithText("Due question").performTouchInput { swipeRight() }
        compose.waitForIdle()
        // Still tappable where it was: its action buttons are back in place.
        compose.onNodeWithText("YES").performClick()
    }

    @Test
    fun rulerDragTapAndAccessibilityAllSetTheValue() {
        var value by mutableFloatStateOf(0.5f)
        compose.setContent {
            FatebookTheme(darkTheme = false) {
                ProbabilitySlider(value = value, onValueChange = { value = it })
            }
        }
        val ruler = compose.onNodeWithContentDescription("Probability: 50 percent")
        ruler.performTouchInput { swipeRight(startX = centerX, endX = right) }
        compose.waitForIdle()
        assertEquals(0.99f, value, 0.001f)

        compose.onNodeWithContentDescription("Set to 25 percent").performClick()
        compose.waitForIdle()
        assertEquals(0.25f, value, 0.001f)

        compose.onNodeWithContentDescription("Probability: 25 percent")
            .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0.6f)) }
        compose.waitForIdle()
        assertEquals(0.6f, value, 0.001f)
    }
}
