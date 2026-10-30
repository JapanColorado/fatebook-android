package dev.russell.fatebook.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.russell.fatebook.data.preferences.NotificationToggles
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.repository.ApiKeyConnector
import dev.russell.fatebook.data.repository.ConnectResult
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
    val dailyReminderEnabled: Boolean = true,
    val readyToResolveEnabled: Boolean = true,
    val resolvingSoonEnabled: Boolean = true,
    val exactAlarmsAllowed: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val connector: ApiKeyConnector,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private val _apiKey = MutableStateFlow(prefs.apiKey ?: "")
    private val _isValidating = MutableStateFlow(false)
    private val _validationResult = MutableStateFlow<Boolean?>(null)
    private val _validationError = MutableStateFlow<String?>(null)
    private val _shouldRequestPermission = MutableStateFlow(false)
    private val _pendingNotificationEnable = MutableStateFlow(false)
    private val _exactAlarmsAllowed = MutableStateFlow(reminderScheduler.canScheduleExact())

    val state: StateFlow<SettingsUiState> = combine(
        _apiKey,
        _isValidating,
        _validationResult,
        _validationError,
        prefs.notificationsEnabled,
        prefs.reminderHour,
        prefs.reminderMinute,
        _shouldRequestPermission,
        prefs.notificationToggles,
        _exactAlarmsAllowed,
    ) { values ->
        val toggles = values[8] as NotificationToggles
        SettingsUiState(
            apiKey = values[0] as String,
            isValidating = values[1] as Boolean,
            validationResult = values[2] as Boolean?,
            validationError = values[3] as String?,
            notificationsEnabled = values[4] as Boolean,
            reminderHour = values[5] as Int,
            reminderMinute = values[6] as Int,
            shouldRequestNotificationPermission = values[7] as Boolean,
            dailyReminderEnabled = toggles.dailyReminder,
            readyToResolveEnabled = toggles.readyToResolve,
            resolvingSoonEnabled = toggles.resolvingSoon,
            exactAlarmsAllowed = values[9] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** The grant happens in system settings, so re-check on returning to the screen. */
    fun refreshExactAlarmAccess() {
        _exactAlarmsAllowed.value = reminderScheduler.canScheduleExact()
    }

    fun setApiKey(key: String) {
        _apiKey.value = key
    }

    fun validateAndSave() {
        viewModelScope.launch {
            _isValidating.value = true
            _validationError.value = null
            _validationResult.value = null

            when (connector.connect(_apiKey.value)) {
                ConnectResult.Success -> _validationResult.value = true
                ConnectResult.Invalid -> {
                    _validationResult.value = false
                    _validationError.value = "Invalid API key — could not connect to Fatebook"
                }
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

    fun setDailyReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setDailyReminderEnabled(enabled) }
    }

    fun setReadyToResolveEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setReadyToResolveEnabled(enabled) }
    }

    fun setResolvingSoonEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setResolvingSoonEnabled(enabled) }
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
