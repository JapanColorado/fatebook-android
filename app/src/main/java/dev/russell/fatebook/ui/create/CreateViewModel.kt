package dev.russell.fatebook.ui.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.data.repository.TemplateRepository
import dev.russell.fatebook.domain.model.QuestionTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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
    val templates: List<QuestionTemplate> = emptyList(),
    /** One-shot snackbar text after saving a template; cleared via [CreateViewModel.templateMessageShown]. */
    val templateMessage: String? = null,
)

@HiltViewModel
class CreateViewModel @Inject constructor(
    private val repository: QuestionRepository,
    private val templateRepository: TemplateRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateUiState())
    val state: StateFlow<CreateUiState> = _state.asStateFlow()

    init {
        // Share-target flow: text shared from another app seeds the title.
        savedStateHandle.get<String>("prefill")
            ?.takeIf { it.isNotBlank() }
            ?.let { prefill -> _state.value = _state.value.copy(title = prefill) }

        templateRepository.observeTemplates()
            .onEach { templates -> _state.update { it.copy(templates = templates) } }
            .launchIn(viewModelScope)
    }

    // --- Templates ---

    /** Fill the form from a saved pattern; the resolve date is offset from today. */
    fun applyTemplate(template: QuestionTemplate) {
        _state.update {
            it.copy(
                title = template.title,
                forecast = template.defaultProbability.toFloat(),
                tags = template.tags,
                tagInput = "",
                resolveBy = LocalDate.now().plusDays(template.resolveOffsetDays.toLong()),
                error = null,
            )
        }
    }

    /** Save the current form as a template. Needs a title; a pending tag input counts. */
    fun saveCurrentAsTemplate() {
        val current = _state.value
        if (current.title.isBlank()) {
            _state.update { it.copy(error = "Give the prediction a title before saving it as a template") }
            return
        }
        val offset = ChronoUnit.DAYS.between(LocalDate.now(), current.resolveBy).toInt()
        viewModelScope.launch {
            templateRepository.save(
                title = current.title,
                defaultProbability = (current.forecast * 100).roundToInt() / 100.0,
                tags = withTagInput(current.tags, current.tagInput),
                resolveOffsetDays = offset,
            )
            _state.update { it.copy(templateMessage = "Template saved") }
        }
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch { templateRepository.delete(id) }
    }

    fun templateMessageShown() {
        _state.update { it.copy(templateMessage = null) }
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
        if (_state.value.tagInput.isBlank()) return
        _state.value = _state.value.copy(
            tags = withTagInput(_state.value.tags, _state.value.tagInput),
            tagInput = "",
        )
    }

    /** [tags] plus the trimmed [input], unless it's blank or already present. */
    private fun withTagInput(tags: List<String>, input: String): List<String> {
        val tag = input.trim()
        return if (tag.isEmpty() || tag in tags) tags else tags + tag
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
                val tags = withTagInput(current.tags, current.tagInput)
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
