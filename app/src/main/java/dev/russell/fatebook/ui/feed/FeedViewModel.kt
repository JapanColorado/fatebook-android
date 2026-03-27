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
import java.io.IOException
import javax.inject.Inject

enum class FeedFilter { ACTIVE, READY_TO_RESOLVE, RESOLVED }

sealed interface FeedError {
    val message: String
    data class Network(override val message: String) : FeedError
    data class Other(override val message: String) : FeedError
}

data class FeedUiState(
    val questions: List<Question> = emptyList(),
    val filter: FeedFilter = FeedFilter.ACTIVE,
    val isRefreshing: Boolean = false,
    val error: FeedError? = null,
    val resolveTarget: Question? = null,
    val isResolving: Boolean = false,
    val detailTarget: Question? = null,
    val searchQuery: String = "",
    val forecastSliderValue: Float = 0.5f,
    val isUpdatingForecast: Boolean = false,
    val isInitialLoad: Boolean = true,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
)

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repository: QuestionRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(FeedFilter.ACTIVE)
    private val _isRefreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<FeedError?>(null)
    private val _resolveTarget = MutableStateFlow<Question?>(null)
    private val _isResolving = MutableStateFlow(false)
    private val _detailTarget = MutableStateFlow<Question?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _forecastSliderValue = MutableStateFlow(0.5f)
    private val _isUpdatingForecast = MutableStateFlow(false)
    private val _isInitialLoad = MutableStateFlow(true)
    private val _hasMore = MutableStateFlow(false)
    private val _isLoadingMore = MutableStateFlow(false)

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
            _forecastSliderValue, _isUpdatingForecast, _isInitialLoad, _hasMore, _isLoadingMore,
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            FeedUiState(
                questions = args[0] as List<Question>,
                filter = filter,
                isRefreshing = args[1] as Boolean,
                error = args[2] as FeedError?,
                resolveTarget = args[3] as Question?,
                isResolving = args[4] as Boolean,
                detailTarget = args[5] as Question?,
                searchQuery = query,
                forecastSliderValue = args[6] as Float,
                isUpdatingForecast = args[7] as Boolean,
                isInitialLoad = args[8] as Boolean,
                hasMore = args[9] as Boolean,
                isLoadingMore = args[10] as Boolean,
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
                _hasMore.value = repository.hasMore()
            } catch (e: Exception) {
                _error.value = classifyError(e, "Failed to load questions")
            } finally {
                _isRefreshing.value = false
                _isInitialLoad.value = false
            }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val hasMore = repository.loadMore()
                _hasMore.value = hasMore
            } catch (e: Exception) {
                _error.value = classifyError(e, "Failed to load more")
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    // --- Detail sheet ---

    fun showDetailSheet(question: Question) {
        _detailTarget.value = question
        _forecastSliderValue.value = question.yourLatestForecast?.toFloat() ?: 0.5f
    }

    fun dismissDetailSheet() {
        _detailTarget.value = null
    }

    fun setForecastSliderValue(value: Float) {
        _forecastSliderValue.value = value
    }

    fun updateForecast() {
        val question = _detailTarget.value ?: return
        viewModelScope.launch {
            _isUpdatingForecast.value = true
            _error.value = null
            try {
                repository.addForecast(question.id, _forecastSliderValue.value.toDouble())
                _detailTarget.value = null // close sheet on success
            } catch (e: Exception) {
                _error.value = classifyError(e, "Failed to update forecast")
            } finally {
                _isUpdatingForecast.value = false
            }
        }
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
                _error.value = classifyError(e, "Failed to resolve question")
            } finally {
                _isResolving.value = false
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    private fun classifyError(e: Exception, fallback: String): FeedError {
        val message = e.message ?: fallback
        return when (e) {
            is IOException -> FeedError.Network(message)
            else -> FeedError.Other(message)
        }
    }
}
