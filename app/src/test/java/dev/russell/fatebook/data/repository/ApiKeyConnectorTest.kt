package dev.russell.fatebook.data.repository

import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.data.preferences.UserPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ApiKeyConnectorTest {

    private val prefs = mockk<UserPreferences>(relaxed = true)
    private val repository = mockk<QuestionRepository>(relaxed = true)
    private val connector = ApiKeyConnector(prefs, repository)

    @Test
    fun `valid key is trimmed and kept`() = runTest {
        every { prefs.apiKey } returns null
        coEvery { repository.validateApiKey() } returns true

        val result = connector.connect("  abc  ")

        assertThat(result).isEqualTo(ConnectResult.Success)
        verify { prefs.apiKey = "abc" }
        verify(exactly = 0) { prefs.userId = null }
    }

    @Test
    fun `invalid key restores the previous key`() = runTest {
        every { prefs.apiKey } returns "old"
        coEvery { repository.validateApiKey() } returns false

        val result = connector.connect("new")

        assertThat(result).isEqualTo(ConnectResult.Invalid)
        verifyOrder {
            prefs.apiKey = "new"
            prefs.apiKey = "old"
        }
    }

    @Test
    fun `changing to a different valid key forgets the inferred identity`() = runTest {
        every { prefs.apiKey } returns "old"
        coEvery { repository.validateApiKey() } returns true

        connector.connect("new")

        verify { prefs.userId = null }
        verify { prefs.displayName = null }
    }

    @Test
    fun `re-entering the same key keeps the identity`() = runTest {
        every { prefs.apiKey } returns "same"
        coEvery { repository.validateApiKey() } returns true

        connector.connect("same")

        verify(exactly = 0) { prefs.userId = null }
    }
}
