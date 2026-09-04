package dev.russell.fatebook.data.repository

import dev.russell.fatebook.data.preferences.UserPreferences
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ConnectResult {
    data object Success : ConnectResult
    data object Invalid : ConnectResult
}

/**
 * Validates an API key against Fatebook and persists it on success.
 *
 * The key is written to prefs *before* the request so [dev.russell.fatebook.data.remote.ApiKeyInterceptor]
 * picks it up; on failure the previous key is restored rather than wiped, so a
 * typo while re-entering a key never logs the user out. Shared by Settings and
 * Onboarding.
 */
@Singleton
class ApiKeyConnector @Inject constructor(
    private val prefs: UserPreferences,
    private val repository: QuestionRepository,
) {
    suspend fun connect(rawKey: String): ConnectResult {
        val key = rawKey.trim()
        val previous = prefs.apiKey
        prefs.apiKey = key
        val valid = repository.validateApiKey()
        if (!valid) {
            prefs.apiKey = previous
            return ConnectResult.Invalid
        }
        if (previous != null && previous != key) {
            // A different account may be behind the new key; forget the
            // inferred identity so it's re-captured on the next refresh.
            prefs.userId = null
            prefs.displayName = null
        }
        return ConnectResult.Success
    }
}
