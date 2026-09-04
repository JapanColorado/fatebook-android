package dev.russell.fatebook.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.repository.ApiKeyConnector
import dev.russell.fatebook.data.repository.ConnectResult
import dev.russell.fatebook.notification.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

const val ONBOARDING_PAGE_COUNT = 3

data class OnboardingUiState(
    val page: Int = 0,
    val apiKey: String = "",
    val isConnecting: Boolean = false,
    val connectError: String? = null,
    val connected: Boolean = false,
    // Notification choices stay local until Done; nothing is written on Skip.
    val notificationsEnabled: Boolean = false,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val shouldRequestNotificationPermission: Boolean = false,
    val finished: Boolean = false,
) {
    val isLastPage: Boolean get() = page == ONBOARDING_PAGE_COUNT - 1
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val connector: ApiKeyConnector,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun next() {
        _state.update {
            when {
                it.page == PAGE_API_KEY && !it.connected -> it
                it.isLastPage -> it
                else -> it.copy(page = it.page + 1)
            }
        }
    }

    fun back() {
        _state.update { if (it.page > 0) it.copy(page = it.page - 1) else it }
    }

    fun setApiKey(key: String) {
        _state.update { it.copy(apiKey = key, connectError = null) }
    }

    fun connect() {
        val key = _state.value.apiKey
        if (key.isBlank() || _state.value.isConnecting) return
        viewModelScope.launch {
            _state.update { it.copy(isConnecting = true, connectError = null) }
            when (connector.connect(key)) {
                ConnectResult.Success -> _state.update {
                    it.copy(isConnecting = false, connected = true, page = PAGE_NOTIFICATIONS)
                }
                ConnectResult.Invalid -> _state.update {
                    it.copy(
                        isConnecting = false,
                        connected = false,
                        connectError = "Invalid API key — could not connect to Fatebook",
                    )
                }
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        if (enabled) {
            _state.update { it.copy(shouldRequestNotificationPermission = true) }
        } else {
            _state.update { it.copy(notificationsEnabled = false) }
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        _state.update {
            it.copy(shouldRequestNotificationPermission = false, notificationsEnabled = granted)
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        _state.update { it.copy(reminderHour = hour, reminderMinute = minute) }
    }

    /** Persist the notification choice (if any) and leave onboarding. */
    fun finish() {
        val current = _state.value
        viewModelScope.launch {
            if (current.notificationsEnabled) {
                prefs.setReminderTime(current.reminderHour, current.reminderMinute)
                prefs.setNotificationsEnabled(true)
                reminderScheduler.schedule(current.reminderHour, current.reminderMinute)
            }
            prefs.setOnboardingComplete(true)
            _state.update { it.copy(finished = true) }
        }
    }

    companion object {
        const val PAGE_WELCOME = 0
        const val PAGE_API_KEY = 1
        const val PAGE_NOTIFICATIONS = 2
    }
}
