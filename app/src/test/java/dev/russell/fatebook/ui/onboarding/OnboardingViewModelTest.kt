package dev.russell.fatebook.ui.onboarding

import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.repository.ApiKeyConnector
import dev.russell.fatebook.data.repository.ConnectResult
import dev.russell.fatebook.notification.ReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val prefs = mockk<UserPreferences>(relaxed = true)
    private val connector = mockk<ApiKeyConnector>()
    private val scheduler = mockk<ReminderScheduler>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = OnboardingViewModel(prefs, connector, scheduler)

    @Test
    fun `next advances from welcome but not past an unconnected key page`() {
        val vm = vm()

        vm.next()
        assertThat(vm.state.value.page).isEqualTo(OnboardingViewModel.PAGE_API_KEY)

        vm.next()
        assertThat(vm.state.value.page).isEqualTo(OnboardingViewModel.PAGE_API_KEY)
    }

    @Test
    fun `back never goes below the first page`() {
        val vm = vm()
        vm.back()
        assertThat(vm.state.value.page).isEqualTo(0)
    }

    @Test
    fun `successful connect marks connected and advances`() = runTest {
        coEvery { connector.connect("key") } returns ConnectResult.Success
        val vm = vm()
        vm.next()
        vm.setApiKey("key")

        vm.connect()
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.connected).isTrue()
        assertThat(state.isConnecting).isFalse()
        assertThat(state.page).isEqualTo(OnboardingViewModel.PAGE_NOTIFICATIONS)
    }

    @Test
    fun `failed connect shows an error and stays put`() = runTest {
        coEvery { connector.connect(any()) } returns ConnectResult.Invalid
        val vm = vm()
        vm.next()
        vm.setApiKey("bad")

        vm.connect()
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.connected).isFalse()
        assertThat(state.connectError).isNotNull()
        assertThat(state.page).isEqualTo(OnboardingViewModel.PAGE_API_KEY)
    }

    @Test
    fun `blank key does not attempt a connection`() = runTest {
        val vm = vm()
        vm.connect()
        advanceUntilIdle()

        coVerify(exactly = 0) { connector.connect(any()) }
    }

    @Test
    fun `enabling notifications asks for permission and honours the result`() {
        val vm = vm()

        vm.setNotificationsEnabled(true)
        assertThat(vm.state.value.shouldRequestNotificationPermission).isTrue()
        assertThat(vm.state.value.notificationsEnabled).isFalse()

        vm.onNotificationPermissionResult(granted = false)
        assertThat(vm.state.value.shouldRequestNotificationPermission).isFalse()
        assertThat(vm.state.value.notificationsEnabled).isFalse()

        vm.setNotificationsEnabled(true)
        vm.onNotificationPermissionResult(granted = true)
        assertThat(vm.state.value.notificationsEnabled).isTrue()
    }

    @Test
    fun `finish with notifications on persists the time and schedules`() = runTest {
        val vm = vm()
        vm.setNotificationsEnabled(true)
        vm.onNotificationPermissionResult(true)
        vm.setReminderTime(20, 30)

        vm.finish()
        advanceUntilIdle()

        coVerify { prefs.setReminderTime(20, 30) }
        coVerify { prefs.setNotificationsEnabled(true) }
        verify { scheduler.schedule(20, 30) }
        coVerify { prefs.setOnboardingComplete(true) }
        assertThat(vm.state.value.finished).isTrue()
    }

    @Test
    fun `finish with notifications off schedules nothing`() = runTest {
        val vm = vm()

        vm.finish()
        advanceUntilIdle()

        verify(exactly = 0) { scheduler.schedule(any(), any()) }
        coVerify(exactly = 0) { prefs.setNotificationsEnabled(any()) }
        coVerify { prefs.setOnboardingComplete(true) }
        assertThat(vm.state.value.finished).isTrue()
    }
}
