package dev.russell.fatebook.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.domain.model.Comment
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

enum class FeedFilter { ACTIVE, READY_TO_RESOLVE, RESOLVED }

sealed interface FeedError {
    val message: String
    data class Network(override val message: String) : FeedError
    data class Other(override val message: String) : FeedError
}

data class DetailSheetState(
    val question: Question? = null,
    val forecastSliderValue: Float = 0.5f,
    val isUpdatingForecast: Boolean = false,
    val isEditing: Boolean = false,
    val editTitle: String = "",
    val editResolveBy: LocalDate? = null,
    val editNotes: String = "",
    val comments: List<Comment> = emptyList(),
    val isLoadingComments: Boolean = false,
    val commentText: String = "",
    val isAddingComment: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
    val isSaving: Boolean = false,
    val isResolving: Boolean = false,
)

data class FeedUiState(
    val questions: List<Question> = emptyList(),
    val filter: FeedFilter = FeedFilter.ACTIVE,
    val isRefreshing: Boolean = false,
    val error: FeedError? = null,
    val detail: DetailSheetState = DetailSheetState(),
    val searchQuery: String = "",
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
    private val _detail = MutableStateFlow(DetailSheetState())
    private val _searchQuery = MutableStateFlow("")
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
            questionsFlow, _isRefreshing, _error,
            _detail, _isInitialLoad, _hasMore, _isLoadingMore,
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            FeedUiState(
                questions = args[0] as List<Question>,
                filter = filter,
                isRefreshing = args[1] as Boolean,
                error = args[2] as FeedError?,
                detail = args[3] as DetailSheetState,
                searchQuery = query,
                isInitialLoad = args[4] as Boolean,
                hasMore = args[5] as Boolean,
                isLoadingMore = args[6] as Boolean,
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
        _detail.value = DetailSheetState(
            question = question,
            forecastSliderValue = question.yourLatestForecast?.toFloat() ?: 0.5f,
            comments = question.comments,
        )
        // Enrich with full data (comments, visibility state) from API
        viewModelScope.launch {
            _detail.update { it.copy(isLoadingComments = true) }
            try {
                val full = repository.getQuestion(question.id)
                _detail.update {
                    it.copy(
                        question = full,
                        comments = full.comments,
                        isLoadingComments = false,
                    )
                }
            } catch (_: Exception) {
                _detail.update { it.copy(isLoadingComments = false) }
            }
        }
    }

    fun dismissDetailSheet() {
        _detail.value = DetailSheetState()
    }

    fun setForecastSliderValue(value: Float) {
        _detail.update { it.copy(forecastSliderValue = value) }
    }

    fun updateForecast() {
        val question = _detail.value.question ?: return
        viewModelScope.launch {
            _detail.update { it.copy(isUpdatingForecast = true) }
            _error.value = null
            try {
                repository.addForecast(question.id, _detail.value.forecastSliderValue.toDouble())
                _detail.value = DetailSheetState() // close sheet on success
            } catch (e: Exception) {
                _error.value = classifyError(e, "Failed to update forecast")
            } finally {
                _detail.update { it.copy(isUpdatingForecast = false) }
            }
        }
    }

    // --- Edit ---

    fun enterEditMode() {
        val question = _detail.value.question ?: return
        _detail.update {
            it.copy(
                isEditing = true,
                editTitle = question.title,
                editResolveBy = question.resolveByDate,
                editNotes = question.notes ?: "",
            )
        }
    }

    fun cancelEdit() {
        _detail.update {
            it.copy(isEditing = false, editTitle = "", editNotes = "", editResolveBy = null)
        }
    }

    fun setEditTitle(title: String) {
        _detail.update { it.copy(editTitle = title) }
    }

    fun setEditResolveBy(date: LocalDate) {
        _detail.update { it.copy(editResolveBy = date) }
    }

    fun setEditNotes(notes: String) {
        _detail.update { it.copy(editNotes = notes) }
    }

    fun saveEdit() {
        val question = _detail.value.question ?: return
        val state = _detail.value
        viewModelScope.launch {
            _detail.update { it.copy(isSaving = true) }
            _error.value = null
            try {
                repository.editQuestion(
                    questionId = question.id,
                    title = state.editTitle.takeIf { it != question.title },
                    resolveBy = state.editResolveBy,
                    notes = state.editNotes.takeIf { it != (question.notes ?: "") },
                )
                _detail.value = DetailSheetState() // close sheet on success
            } catch (e: Exception) {
                _error.value = classifyError(e, "Failed to save changes")
                _detail.update { it.copy(isSaving = false) }
            }
        }
    }

    // --- Delete ---

    fun requestDelete() {
        _detail.update { it.copy(showDeleteConfirmation = true) }
    }

    fun dismissDeleteConfirmation() {
        _detail.update { it.copy(showDeleteConfirmation = false) }
    }

    fun confirmDelete() {
        val question = _detail.value.question ?: return
        viewModelScope.launch {
            _detail.update { it.copy(isDeleting = true, showDeleteConfirmation = false) }
            _error.value = null
            try {
                repository.deleteQuestion(question.id)
                _detail.value = DetailSheetState() // close sheet
            } catch (e: Exception) {
                _error.value = classifyError(e, "Failed to delete question")
                _detail.update { it.copy(isDeleting = false) }
            }
        }
    }

    // --- Comments ---

    fun setCommentText(text: String) {
        _detail.update { it.copy(commentText = text) }
    }

    fun addComment() {
        val question = _detail.value.question ?: return
        val text = _detail.value.commentText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            _detail.update { it.copy(isAddingComment = true) }
            _error.value = null
            try {
                val updated = repository.addComment(question, text)
                _detail.update {
                    it.copy(
                        question = updated,
                        comments = updated.comments,
                        commentText = "",
                        isAddingComment = false,
                    )
                }
            } catch (e: Exception) {
                _error.value = classifyError(e, "Failed to add comment")
                _detail.update { it.copy(isAddingComment = false) }
            }
        }
    }

    // --- Share / Visibility ---

    fun toggleSharedPublicly() {
        val question = _detail.value.question ?: return
        viewModelScope.launch {
            _error.value = null
            try {
                val newShared = !question.sharedPublicly
                repository.setSharedPublicly(question.id, newShared, question.unlisted)
                _detail.update {
                    it.copy(
                        question = question.copy(sharedPublicly = newShared),
                    )
                }
            } catch (e: Exception) {
                _error.value = classifyError(e, "Failed to update sharing")
            }
        }
    }

    // --- Deep links ---

    fun openDeepLinkedQuestion(questionId: String) {
        viewModelScope.launch {
            _error.value = null
            try {
                // Try local cache first, fall back to API
                val question = repository.getCachedQuestion(questionId)
                    ?: repository.getQuestion(questionId)
                showDetailSheet(question)
            } catch (e: Exception) {
                _error.value = classifyError(e, "Failed to load question")
            }
        }
    }

    // --- Resolve flow ---

    fun resolveQuestion(resolution: Resolution) {
        val question = _detail.value.question ?: return
        viewModelScope.launch {
            _detail.update { it.copy(isResolving = true) }
            _error.value = null
            try {
                repository.resolveQuestion(question.id, resolution)
                _detail.value = DetailSheetState() // close sheet on success
            } catch (e: Exception) {
                _error.value = classifyError(e, "Failed to resolve question")
            } finally {
                _detail.update { it.copy(isResolving = false) }
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
