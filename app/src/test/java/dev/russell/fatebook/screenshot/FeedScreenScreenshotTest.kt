package dev.russell.fatebook.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.russell.fatebook.domain.model.QuestionType
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.navigation.LedgerBottomBar
import dev.russell.fatebook.navigation.LedgerTab
import dev.russell.fatebook.testutil.TestData
import dev.russell.fatebook.ui.feed.FeedError
import dev.russell.fatebook.ui.feed.FeedFilter
import dev.russell.fatebook.ui.feed.FeedScreenContent
import dev.russell.fatebook.ui.feed.FeedUiState
import dev.russell.fatebook.ui.feed.PendingQuickAction
import dev.russell.fatebook.ui.feed.QuickAction
import dev.russell.fatebook.ui.theme.FatebookTheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant

private val TEST_TODAY = java.time.LocalDate.of(2026, 3, 14)

class FeedScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
    )

    @Test
    fun feedScreen_loading() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = false) {
                FeedScreenContent(
                    today = TEST_TODAY,
                    state = FeedUiState(isInitialLoad = true),
                )
            }
        }
    }

    @Test
    fun feedScreen_emptyActive() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = false) {
                FeedScreenContent(
                    today = TEST_TODAY,
                    state = FeedUiState(
                        isInitialLoad = false,
                        questions = emptyList(),
                        filter = FeedFilter.ACTIVE,
                    ),
                )
            }
        }
    }

    @Test
    fun feedScreen_emptyResolved() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = false) {
                FeedScreenContent(
                    today = TEST_TODAY,
                    state = FeedUiState(
                        isInitialLoad = false,
                        questions = emptyList(),
                        filter = FeedFilter.RESOLVED,
                    ),
                )
            }
        }
    }

    @Test
    fun feedScreen_emptyResolvingSoon() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = false) {
                FeedScreenContent(
                    today = TEST_TODAY,
                    state = FeedUiState(
                        isInitialLoad = false,
                        questions = emptyList(),
                        filter = FeedFilter.RESOLVING_SOON,
                    ),
                )
            }
        }
    }

    @Test
    fun feedScreen_resolvingSoon() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = false) {
                FeedScreenContent(
                    today = TEST_TODAY,
                    state = FeedUiState(
                        isInitialLoad = false,
                        filter = FeedFilter.RESOLVING_SOON,
                        questions = listOf(
                            TestData.question(
                                id = "1",
                                title = "Will the CI run go green on the first try?",
                                yourLatestForecast = 0.3,
                                resolveBy = Instant.parse("2030-06-03T00:00:00Z"),
                            ),
                            TestData.question(
                                id = "2",
                                title = "Will I hit my step goal this week?",
                                yourLatestForecast = 0.7,
                                resolveBy = Instant.parse("2030-06-07T00:00:00Z"),
                            ),
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun feedScreen_withQuestions() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = false) {
                FeedScreenContent(
                    today = TEST_TODAY,
                    state = FeedUiState(
                        isInitialLoad = false,
                        questions = listOf(
                            TestData.question(
                                id = "1",
                                title = "Will GPT-5 be released by 2026?",
                                yourLatestForecast = 0.85,
                                resolveBy = Instant.parse("2030-12-31T00:00:00Z"),
                            ),
                            TestData.question(
                                id = "2",
                                title = "Will I finish this book by Friday?",
                                yourLatestForecast = 0.50,
                                resolveBy = Instant.parse("2030-06-15T00:00:00Z"),
                            ),
                            TestData.question(
                                id = "3",
                                title = "Will we colonize Mars by 2030?",
                                yourLatestForecast = 0.12,
                                resolveBy = Instant.parse("2030-12-31T00:00:00Z"),
                            ),
                            TestData.question(
                                id = "4",
                                title = "Which framework will we pick?",
                                type = QuestionType.MULTIPLE_CHOICE,
                                yourLatestForecast = null,
                                latestForecastAt = null,
                                resolveBy = Instant.parse("2030-12-31T00:00:00Z"),
                                options = listOf(
                                    TestData.questionOption(
                                        id = "o1",
                                        text = "Compose",
                                        latestForecast = 0.65,
                                    ),
                                    TestData.questionOption(
                                        id = "o2",
                                        text = "Flutter",
                                        latestForecast = 0.25,
                                    ),
                                ),
                            ),
                        ),
                        filter = FeedFilter.ACTIVE,
                    ),
                )
            }
        }
    }

    @Test
    fun feedScreen_resolvedFilter() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = false) {
                FeedScreenContent(
                    today = TEST_TODAY,
                    state = FeedUiState(
                        isInitialLoad = false,
                        questions = listOf(
                            TestData.question(
                                id = "1",
                                title = "Did it rain yesterday?",
                                resolved = true,
                                resolution = Resolution.YES,
                            ),
                            TestData.question(
                                id = "2",
                                title = "Was the stock market up?",
                                resolved = true,
                                resolution = Resolution.NO,
                            ),
                        ),
                        filter = FeedFilter.RESOLVED,
                    ),
                )
            }
        }
    }

    @Test
    fun feedScreen_searchNoResults() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = false) {
                FeedScreenContent(
                    today = TEST_TODAY,
                    state = FeedUiState(
                        isInitialLoad = false,
                        questions = emptyList(),
                        searchQuery = "xyzzy",
                    ),
                )
            }
        }
    }

    @Test
    fun feedScreen_dark() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = true) {
                FeedScreenContent(
                    today = TEST_TODAY,
                    state = FeedUiState(
                        isInitialLoad = false,
                        questions = listOf(
                            TestData.question(
                                id = "1",
                                title = "Will GPT-5 be released by 2026?",
                                yourLatestForecast = 0.85,
                            ),
                            TestData.question(
                                id = "2",
                                title = "Will I finish this book?",
                                yourLatestForecast = 0.50,
                            ),
                        ),
                        filter = FeedFilter.ACTIVE,
                    ),
                )
            }
        }
    }

    @Test
    fun feedScreen_networkError() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = false) {
                FeedScreenContent(
                    today = TEST_TODAY,
                    state = FeedUiState(
                        isInitialLoad = false,
                        questions = emptyList(),
                        error = FeedError.Network("Unable to connect. Check your internet connection."),
                    ),
                )
            }
        }
    }

    private val dueQuestions = listOf(
        TestData.question(
            id = "1",
            title = "Will the CI run go green on the first try?",
            yourLatestForecast = 0.3,
            resolveBy = Instant.parse("2026-03-02T00:00:00Z"),
            tags = listOf("work"),
        ),
        TestData.question(
            id = "2",
            title = "Will I hit my step goal this week?",
            yourLatestForecast = 0.82,
            resolveBy = Instant.parse("2026-03-14T00:00:00Z"),
        ),
        TestData.question(
            id = "3",
            title = "Which framework will we pick?",
            type = QuestionType.MULTIPLE_CHOICE,
            yourLatestForecast = null,
            resolveBy = Instant.parse("2026-03-10T00:00:00Z"),
            options = listOf(
                TestData.questionOption(id = "o1", text = "Compose", latestForecast = 0.65),
                TestData.questionOption(id = "o2", text = "Flutter", latestForecast = 0.25),
            ),
        ),
    )

    @Test
    fun feedScreen_dueWithActions() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = false) {
                FeedScreenContent(
                    today = TEST_TODAY,
                    state = FeedUiState(
                        isInitialLoad = false,
                        filter = FeedFilter.READY_TO_RESOLVE,
                        readyCount = 3,
                        openCount = 12,
                        questions = dueQuestions,
                    ),
                )
            }
        }
    }

    @Test
    fun feedScreen_dueWithActions_dark() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = true) {
                FeedScreenContent(
                    today = TEST_TODAY,
                    state = FeedUiState(
                        isInitialLoad = false,
                        filter = FeedFilter.READY_TO_RESOLVE,
                        readyCount = 3,
                        openCount = 12,
                        questions = dueQuestions,
                    ),
                )
            }
        }
    }

    @Test
    fun feedScreen_undoWindow() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = false) {
                FeedScreenContent(
                    today = TEST_TODAY,
                    state = FeedUiState(
                        isInitialLoad = false,
                        filter = FeedFilter.READY_TO_RESOLVE,
                        readyCount = 2,
                        openCount = 12,
                        questions = dueQuestions.drop(1),
                        pendingQuickAction = PendingQuickAction(
                            dueQuestions.first(),
                            QuickAction.Resolve(Resolution.YES),
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun feedScreen_inAppShell() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = false) {
                Scaffold(
                    bottomBar = {
                        LedgerBottomBar(
                            selected = LedgerTab.PREDICTIONS,
                            onSelect = {},
                            onCreate = {},
                        )
                    },
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        FeedScreenContent(
                            today = TEST_TODAY,
                            state = FeedUiState(
                                isInitialLoad = false,
                                readyCount = 3,
                                openCount = 12,
                                availableTags = listOf("work"),
                                questions = dueQuestions.drop(1) + TestData.question(
                                    id = "9",
                                    title = "Will we colonize Mars by 2030?",
                                    yourLatestForecast = 0.12,
                                    resolveBy = Instant.parse("2030-12-31T00:00:00Z"),
                                    tags = listOf("space"),
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }
}
