package dev.russell.fatebook.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.Resolution
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FeedFilter { ACTIVE, READY_TO_RESOLVE, RESOLVED }

data class FeedUiState(
    val questions: List<Question> = emptyList(),
    val filter: FeedFilter = FeedFilter.ACTIVE,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val resolveTarget: Question? = null,
    val isResolving: Boolean = false,
    val detailTarget: Question? = null,
    val searchQuery: String = "",
)

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repository: QuestionRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(FeedFilter.ACTIVE)
    private val _isRefreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _resolveTarget = MutableStateFlow<Question?>(null)
    private val _isResolving = MutableStateFlow(false)
    private val _detailTarget = MutableStateFlow<Question?>(null)
    private val _searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<FeedUiState> = combine(_filter, _searchQuery) { filter, query ->
        filter to query
    }.flatMapLatest { (filter, query) ->
        val questionsFlow = when (filter) {
            FeedFilter.ACTIVE -> repository.observeActive()
            FeedFilter.READY_TO_RESOLVE -> repository.observeReadyToResolve()
            FeedFilter.RESOLVED -> repository.observeResolved()
        }.map { questions ->
            if (query.isBlank()) questions
            else questions.filter { it.title.contains(query, ignoreCase = true) }
        }
        combine(
            questionsFlow, _isRefreshing, _error, _resolveTarget, _isResolving, _detailTarget,
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            FeedUiState(
                questions = args[0] as List<Question>,
                filter = filter,
                isRefreshing = args[1] as Boolean,
                error = args[2] as String?,
                resolveTarget = args[3] as Question?,
                isResolving = args[4] as Boolean,
                detailTarget = args[5] as Question?,
                searchQuery = query,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())

    init {
        refresh()
    }

    fun setFilter(filter: FeedFilter) {
        _filter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
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

    // --- Detail sheet ---

    fun showDetailSheet(question: Question) {
        _detailTarget.value = question
    }

    fun dismissDetailSheet() {
        _detailTarget.value = null
    }

    // --- Resolve flow ---

    fun showResolveSheet(question: Question) {
        _resolveTarget.value = question
    }

    fun dismissResolveSheet() {
        _resolveTarget.value = null
    }

    fun resolveQuestion(resolution: Resolution) {
        val question = _resolveTarget.value ?: return
        viewModelScope.launch {
            _isResolving.value = true
            _error.value = null
            try {
                repository.resolveQuestion(question.id, resolution)
                _resolveTarget.value = null // close sheet on success
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to resolve question"
            } finally {
                _isResolving.value = false
            }
        }
    }
}
