package dev.russell.fatebook.ui.create

import androidx.lifecycle.SavedStateHandle
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

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
        CreateViewModel(repository, savedStateHandle)

    @Test
    fun `prefill from SavedStateHandle seeds the title`() {
        val vm = createViewModel(SavedStateHandle(mapOf("prefill" to "Will shared text work?")))

        assertThat(vm.state.value.title).isEqualTo("Will shared text work?")
    }

    @Test
    fun `blank prefill is ignored`() {
        val vm = createViewModel(SavedStateHandle(mapOf("prefill" to "   ")))

        assertThat(vm.state.value.title).isEmpty()
    }

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
        coVerify(exactly = 0) { repository.createQuestion(any(), any(), any(), any()) }
    }

    @Test
    fun `submit sets success on completion`() = runTest {
        coEvery { repository.createQuestion(any(), any(), any(), any()) } returns "https://fatebook.io/q/new"

        val vm = createViewModel()
        vm.setTitle("Will it rain?")

        vm.submit()
        advanceUntilIdle()

        assertThat(vm.state.value.success).isTrue()
        assertThat(vm.state.value.isSubmitting).isFalse()
    }

    @Test
    fun `submit sets error on failure`() = runTest {
        coEvery { repository.createQuestion(any(), any(), any(), any()) } throws RuntimeException("API error")

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
        coEvery { repository.createQuestion(any(), any(), any(), any()) } returns "url"

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
                tags = any(),
            )
        }
    }

    @Test
    fun `submit trims title`() = runTest {
        coEvery { repository.createQuestion(any(), any(), any(), any()) } returns "url"

        val vm = createViewModel()
        vm.setTitle("  Will it rain?  ")

        vm.submit()
        advanceUntilIdle()

        coVerify {
            repository.createQuestion(
                title = "Will it rain?",
                resolveBy = any(),
                forecast = any(),
                tags = any(),
            )
        }
    }

    // --- tags ---

    @Test
    fun `addTag turns input into a chip and clears the field`() {
        val vm = createViewModel()
        vm.setTagInput("work")
        vm.addTag()

        assertThat(vm.state.value.tags).containsExactly("work")
        assertThat(vm.state.value.tagInput).isEmpty()
    }

    @Test
    fun `addTag ignores blanks and duplicates`() {
        val vm = createViewModel()
        vm.setTagInput("   ")
        vm.addTag()
        assertThat(vm.state.value.tags).isEmpty()

        vm.setTagInput("work")
        vm.addTag()
        vm.setTagInput("work")
        vm.addTag()
        assertThat(vm.state.value.tags).containsExactly("work")
    }

    @Test
    fun `removeTag drops the chip`() {
        val vm = createViewModel()
        vm.setTagInput("work")
        vm.addTag()
        vm.setTagInput("health")
        vm.addTag()

        vm.removeTag("work")

        assertThat(vm.state.value.tags).containsExactly("health")
    }

    @Test
    fun `submit sends chips plus any tag still in the input field`() = runTest {
        coEvery { repository.createQuestion(any(), any(), any(), any()) } returns "url"

        val vm = createViewModel()
        vm.setTitle("Test?")
        vm.setTagInput("work")
        vm.addTag()
        vm.setTagInput("health") // not chipped — should still be sent

        vm.submit()
        advanceUntilIdle()

        coVerify {
            repository.createQuestion(
                title = any(),
                resolveBy = any(),
                forecast = any(),
                tags = listOf("work", "health"),
            )
        }
    }
}
