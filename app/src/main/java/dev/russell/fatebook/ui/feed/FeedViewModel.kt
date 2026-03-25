package dev.russell.fatebook.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.domain.model.Question
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FeedFilter { ACTIVE, READY_TO_RESOLVE, RESOLVED }

data class FeedUiState(
    val questions: List<Question> = emptyList(),
    val filter: FeedFilter = FeedFilter.ACTIVE,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repository: QuestionRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(FeedFilter.ACTIVE)
    private val _isRefreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FeedUiState> = combine(
        _filter,
        _isRefreshing,
        _error,
        repository.observeAll(),
    ) { filter, refreshing, error, allQuestions ->
        val filtered = when (filter) {
            FeedFilter.ACTIVE -> allQuestions.filter { !it.resolved }
            FeedFilter.READY_TO_RESOLVE -> allQuestions.filter { it.isReadyToResolve }
            FeedFilter.RESOLVED -> allQuestions.filter { it.resolved }
        }
        FeedUiState(
            questions = filtered,
            filter = filter,
            isRefreshing = refreshing,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())

    init {
        refresh()
    }

    fun setFilter(filter: FeedFilter) {
        _filter.value = filter
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            try {
                repository.refresh()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load questions"
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
