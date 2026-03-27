package dev.russell.fatebook.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.testutil.TestData
import dev.russell.fatebook.ui.feed.FeedFilter
import dev.russell.fatebook.ui.feed.FeedScreenContent
import dev.russell.fatebook.ui.feed.FeedUiState
import dev.russell.fatebook.ui.theme.FatebookTheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class FeedScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
    )

    @Test
    fun feedScreen_loading() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                FeedScreenContent(
                    state = FeedUiState(isInitialLoad = true),
                )
            }
        }
    }

    @Test
    fun feedScreen_emptyActive() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                FeedScreenContent(
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
            FatebookTheme(dynamicColor = false) {
                FeedScreenContent(
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
    fun feedScreen_withQuestions() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                FeedScreenContent(
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
            FatebookTheme(dynamicColor = false) {
                FeedScreenContent(
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
            FatebookTheme(dynamicColor = false) {
                FeedScreenContent(
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
            FatebookTheme(darkTheme = true, dynamicColor = false) {
                FeedScreenContent(
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
}
