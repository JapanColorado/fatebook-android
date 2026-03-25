package dev.russell.fatebook.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.russell.fatebook.data.repository.QuestionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class CreateUiState(
    val title: String = "",
    val resolveBy: LocalDate = LocalDate.now().plusDays(1),
    val forecast: Float = 0.5f,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class CreateViewModel @Inject constructor(
    private val repository: QuestionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateUiState())
    val state: StateFlow<CreateUiState> = _state.asStateFlow()

    fun setTitle(title: String) {
        _state.value = _state.value.copy(title = title)
    }

    fun setResolveBy(date: LocalDate) {
        _state.value = _state.value.copy(resolveBy = date)
    }

    fun setForecast(value: Float) {
        _state.value = _state.value.copy(forecast = value)
    }

    fun submit() {
        val current = _state.value
        if (current.title.isBlank()) {
            _state.value = current.copy(error = "Title is required")
            return
        }

        viewModelScope.launch {
            _state.value = current.copy(isSubmitting = true, error = null)
            try {
                repository.createQuestion(
                    title = current.title.trim(),
                    resolveBy = current.resolveBy,
                    forecast = current.forecast.toDouble(),
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
