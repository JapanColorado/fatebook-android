package dev.russell.fatebook.ui.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.russell.fatebook.data.repository.QuestionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt
import javax.inject.Inject

data class CreateUiState(
    val title: String = "",
    val resolveBy: LocalDate = LocalDate.now().plusDays(1),
    val forecast: Float = 0.5f,
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class CreateViewModel @Inject constructor(
    private val repository: QuestionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateUiState())
    val state: StateFlow<CreateUiState> = _state.asStateFlow()

    init {
        // Share-target flow: text shared from another app seeds the title.
        savedStateHandle.get<String>("prefill")
            ?.takeIf { it.isNotBlank() }
            ?.let { prefill -> _state.value = _state.value.copy(title = prefill) }
    }

    fun setTitle(title: String) {
        _state.value = _state.value.copy(title = title)
    }

    fun setResolveBy(date: LocalDate) {
        _state.value = _state.value.copy(resolveBy = date)
    }

    fun setForecast(value: Float) {
        _state.value = _state.value.copy(forecast = value)
    }

    fun setTagInput(input: String) {
        _state.value = _state.value.copy(tagInput = input)
    }

    /** Turn the current tag input into a chip (no-op for blank or duplicate). */
    fun addTag() {
        val tag = _state.value.tagInput.trim()
        if (tag.isEmpty()) return
        _state.value = _state.value.copy(
            tags = if (tag in _state.value.tags) _state.value.tags else _state.value.tags + tag,
            tagInput = "",
        )
    }

    fun removeTag(tag: String) {
        _state.value = _state.value.copy(tags = _state.value.tags - tag)
    }

    fun submit() {
        val current = _state.value
        if (current.title.isBlank()) {
            _state.value = current.copy(error = "Title is required")
            return
        }

        // createQuestion is optimistic — it inserts a local-id row + queue entry,
        // then returns. The actual API call happens in SyncWorker.
        viewModelScope.launch {
            _state.value = current.copy(isSubmitting = true, error = null)
            try {
                // A tag still sitting in the input field counts too.
                val pendingTag = current.tagInput.trim()
                val tags =
                    if (pendingTag.isNotEmpty() && pendingTag !in current.tags) {
                        current.tags + pendingTag
                    } else {
                        current.tags
                    }
                repository.createQuestion(
                    title = current.title.trim(),
                    resolveBy = current.resolveBy,
                    forecast = (current.forecast * 100).roundToInt() / 100.0,
                    tags = tags,
                )
                _state.value = _state.value.copy(isSubmitting = false, success = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    error = e.message ?: "Failed to create prediction",
                )
            }
        }
    }
}
