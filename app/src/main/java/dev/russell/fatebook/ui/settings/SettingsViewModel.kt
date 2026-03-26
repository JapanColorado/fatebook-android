package dev.russell.fatebook.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.notification.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiKey: String = "",
    val isValidating: Boolean = false,
    val validationResult: Boolean? = null,
    val validationError: String? = null,
    val notificationsEnabled: Boolean = false,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val shouldRequestNotificationPermission: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val repository: QuestionRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private val _apiKey = MutableStateFlow(prefs.apiKey ?: "")
    private val _isValidating = MutableStateFlow(false)
    private val _validationResult = MutableStateFlow<Boolean?>(null)
    private val _validationError = MutableStateFlow<String?>(null)
    private val _shouldRequestPermission = MutableStateFlow(false)
    private val _pendingNotificationEnable = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(
        _apiKey,
        _isValidating,
        _validationResult,
        _validationError,
        prefs.notificationsEnabled,
        prefs.reminderHour,
        prefs.reminderMinute,
        _shouldRequestPermission,
    ) { values ->
        SettingsUiState(
            apiKey = values[0] as String,
            isValidating = values[1] as Boolean,
            validationResult = values[2] as Boolean?,
            validationError = values[3] as String?,
            notificationsEnabled = values[4] as Boolean,
            reminderHour = values[5] as Int,
            reminderMinute = values[6] as Int,
            shouldRequestNotificationPermission = values[7] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setApiKey(key: String) {
        _apiKey.value = key
    }

    fun validateAndSave() {
        viewModelScope.launch {
            _isValidating.value = true
            _validationError.value = null
            _validationResult.value = null

            // Temporarily set the key so the interceptor uses it
            val key = _apiKey.value.trim()
            prefs.apiKey = key

            val valid = repository.validateApiKey()
            if (valid) {
                _validationResult.value = true
            } else {
                prefs.apiKey = null
                _validationResult.value = false
                _validationError.value = "Invalid API key — could not connect to Fatebook"
            }
            _isValidating.value = false
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        if (enabled) {
            _pendingNotificationEnable.value = true
            _shouldRequestPermission.value = true
        } else {
            viewModelScope.launch {
                prefs.setNotificationsEnabled(false)
                reminderScheduler.cancel()
            }
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        _shouldRequestPermission.value = false
        if (granted && _pendingNotificationEnable.value) {
            viewModelScope.launch {
                prefs.setNotificationsEnabled(true)
                reminderScheduler.schedule(state.value.reminderHour, state.value.reminderMinute)
            }
        }
        _pendingNotificationEnable.value = false
    }

    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            prefs.setReminderTime(hour, minute)
            if (state.value.notificationsEnabled) {
                reminderScheduler.schedule(hour, minute)
            }
        }
    }
}
