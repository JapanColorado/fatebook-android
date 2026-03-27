package dev.russell.fatebook.ui.settings

import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.notification.ReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val prefs = mockk<UserPreferences>(relaxed = true)
    private val repository = mockk<QuestionRepository>(relaxed = true)
    private val reminderScheduler = mockk<ReminderScheduler>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { prefs.apiKey } returns "existing-key"
        every { prefs.notificationsEnabled } returns flowOf(false)
        every { prefs.reminderHour } returns flowOf(9)
        every { prefs.reminderMinute } returns flowOf(0)
        every { prefs.lastPredictionDateEpochMs } returns flowOf(0L)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SettingsViewModel(prefs, repository, reminderScheduler)

    @Test
    fun `initial state loads existing API key`() = runTest {
        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        advanceUntilIdle()

        assertThat(vm.state.value.apiKey).isEqualTo("existing-key")
    }

    @Test
    fun `setApiKey updates state`() = runTest {
        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        advanceUntilIdle()

        vm.setApiKey("new-key")
        advanceUntilIdle()

        assertThat(vm.state.value.apiKey).isEqualTo("new-key")
    }

    @Test
    fun `validateAndSave sets validationResult true on success`() = runTest {
        coEvery { repository.validateApiKey() } returns true

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        advanceUntilIdle()

        vm.validateAndSave()
        advanceUntilIdle()

        assertThat(vm.state.value.validationResult).isTrue()
        assertThat(vm.state.value.isValidating).isFalse()
    }

    @Test
    fun `validateAndSave clears API key and sets error on failure`() = runTest {
        coEvery { repository.validateApiKey() } returns false

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        advanceUntilIdle()

        vm.validateAndSave()
        advanceUntilIdle()

        verify { prefs.apiKey = null }
        assertThat(vm.state.value.validationResult).isFalse()
        assertThat(vm.state.value.validationError).isNotNull()
    }

    @Test
    fun `setNotificationsEnabled true requests permission`() = runTest {
        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        advanceUntilIdle()

        vm.setNotificationsEnabled(true)
        advanceUntilIdle()

        assertThat(vm.state.value.shouldRequestNotificationPermission).isTrue()
    }

    @Test
    fun `setNotificationsEnabled false cancels reminder`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.setNotificationsEnabled(false)
        advanceUntilIdle()

        coVerify { prefs.setNotificationsEnabled(false) }
        verify { reminderScheduler.cancel() }
    }

    @Test
    fun `onNotificationPermissionResult granted enables and schedules`() = runTest {
        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        advanceUntilIdle()

        vm.setNotificationsEnabled(true)
        vm.onNotificationPermissionResult(granted = true)
        advanceUntilIdle()

        coVerify { prefs.setNotificationsEnabled(true) }
        verify { reminderScheduler.schedule(any(), any()) }
    }

    @Test
    fun `onNotificationPermissionResult denied does not enable`() = runTest {
        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        advanceUntilIdle()

        vm.setNotificationsEnabled(true)
        vm.onNotificationPermissionResult(granted = false)
        advanceUntilIdle()

        coVerify(exactly = 0) { prefs.setNotificationsEnabled(true) }
        assertThat(vm.state.value.shouldRequestNotificationPermission).isFalse()
    }

    @Test
    fun `setReminderTime updates prefs and reschedules when enabled`() = runTest {
        every { prefs.notificationsEnabled } returns flowOf(true)

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        advanceUntilIdle()

        vm.setReminderTime(14, 30)
        advanceUntilIdle()

        coVerify { prefs.setReminderTime(14, 30) }
        verify { reminderScheduler.schedule(14, 30) }
    }

    @Test
    fun `setReminderTime does not reschedule when disabled`() = runTest {
        every { prefs.notificationsEnabled } returns flowOf(false)

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        advanceUntilIdle()

        vm.setReminderTime(14, 30)
        advanceUntilIdle()

        coVerify { prefs.setReminderTime(14, 30) }
        verify(exactly = 0) { reminderScheduler.schedule(any(), any()) }
    }
}
