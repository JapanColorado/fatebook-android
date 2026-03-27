package dev.russell.fatebook.ui.create

import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.data.repository.QuestionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CreateViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<QuestionRepository>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = CreateViewModel(repository)

    @Test
    fun `initial state has correct defaults`() {
        val vm = createViewModel()
        val state = vm.state.value
        assertThat(state.title).isEmpty()
        assertThat(state.forecast).isEqualTo(0.5f)
        assertThat(state.isSubmitting).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.success).isFalse()
    }

    @Test
    fun `setTitle updates state`() {
        val vm = createViewModel()
        vm.setTitle("Will it rain?")
        assertThat(vm.state.value.title).isEqualTo("Will it rain?")
    }

    @Test
    fun `setResolveBy updates state`() {
        val vm = createViewModel()
        val date = LocalDate.of(2030, 6, 15)
        vm.setResolveBy(date)
        assertThat(vm.state.value.resolveBy).isEqualTo(date)
    }

    @Test
    fun `setForecast updates state`() {
        val vm = createViewModel()
        vm.setForecast(0.75f)
        assertThat(vm.state.value.forecast).isEqualTo(0.75f)
    }

    @Test
    fun `submit with blank title sets error`() = runTest {
        val vm = createViewModel()
        vm.setTitle("   ")

        vm.submit()
        advanceUntilIdle()

        assertThat(vm.state.value.error).isEqualTo("Title is required")
        coVerify(exactly = 0) { repository.createQuestion(any(), any(), any()) }
    }

    @Test
    fun `submit sets success on completion`() = runTest {
        coEvery { repository.createQuestion(any(), any(), any()) } returns "https://fatebook.io/q/new"

        val vm = createViewModel()
        vm.setTitle("Will it rain?")

        vm.submit()
        advanceUntilIdle()

        assertThat(vm.state.value.success).isTrue()
        assertThat(vm.state.value.isSubmitting).isFalse()
    }

    @Test
    fun `submit sets error on failure`() = runTest {
        coEvery { repository.createQuestion(any(), any(), any()) } throws RuntimeException("API error")

        val vm = createViewModel()
        vm.setTitle("Will it rain?")

        vm.submit()
        advanceUntilIdle()

        assertThat(vm.state.value.error).isEqualTo("API error")
        assertThat(vm.state.value.success).isFalse()
        assertThat(vm.state.value.isSubmitting).isFalse()
    }

    @Test
    fun `submit rounds forecast to two decimals`() = runTest {
        coEvery { repository.createQuestion(any(), any(), any()) } returns "url"

        val vm = createViewModel()
        vm.setTitle("Test?")
        vm.setForecast(0.756f)

        vm.submit()
        advanceUntilIdle()

        coVerify {
            repository.createQuestion(
                title = "Test?",
                resolveBy = any(),
                forecast = 0.76, // (0.756 * 100).roundToInt() / 100.0 = 76/100 = 0.76
            )
        }
    }

    @Test
    fun `submit trims title`() = runTest {
        coEvery { repository.createQuestion(any(), any(), any()) } returns "url"

        val vm = createViewModel()
        vm.setTitle("  Will it rain?  ")

        vm.submit()
        advanceUntilIdle()

        coVerify {
            repository.createQuestion(
                title = "Will it rain?",
                resolveBy = any(),
                forecast = any(),
            )
        }
    }
}
