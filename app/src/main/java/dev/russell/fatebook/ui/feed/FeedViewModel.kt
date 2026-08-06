package dev.russell.fatebook.ui.feed

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.russell.fatebook.data.local.PendingMutationEntity
import dev.russell.fatebook.data.network.NetworkMonitor
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.domain.model.Comment
import dev.russell.fatebook.domain.model.Forecast
import dev.russell.fatebook.domain.model.McResolution
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.di.DefaultDispatcher
import dev.russell.fatebook.ui.components.PieChartMath
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

enum class FeedFilter { ACTIVE, READY_TO_RESOLVE, RESOLVED }

enum class FeedSort {
    /** Soonest resolve-by date first (resolved filter shows newest first). */
    RESOLVE_BY,

    /** Most recently created first. */
    CREATED_NEWEST,
    ;

    companion object {
        fun fromName(name: String): FeedSort =
            entries.firstOrNull { it.name == name } ?: RESOLVE_BY
    }
}

sealed interface FeedError {
    val message: String
    data class Network(override val message: String) : FeedError
    data class Auth(override val message: String) : FeedError
    data class RateLimited(override val message: String) : FeedError
    data class Other(override val message: String) : FeedError
}

@Immutable
data class DetailSheetState(
    val question: Question? = null,
    val forecastSliderValue: Float = 0.5f,
    val isUpdatingForecast: Boolean = false,
    // Multiple choice (non-exclusive): which option row is expanded for
    // forecasting, if any.
    val expandedOptionId: String? = null,
    val optionSliderValue: Float = 0.5f,
    // Multiple choice (exclusive): pie-editor fractions aligned with
    // question.options, always summing to 1. Empty when the pie editor
    // doesn't apply (non-MC, non-exclusive, resolved, ...).
    val pieValues: List<Float> = emptyList(),
    val isEditing: Boolean = false,
    val editTitle: String = "",
    val editResolveBy: LocalDate? = null,
    val editNotes: String = "",
    val comments: List<Comment> = emptyList(),
    val isLoadingComments: Boolean = false,
    val forecastHistory: List<Forecast> = emptyList(),
    val commentText: String = "",
    val isAddingComment: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
    val isSaving: Boolean = false,
    val isResolving: Boolean = false,
)

data class SyncErrorEntry(
    val id: Long,
    val type: String,
    val message: String,
)

@Immutable
data class FeedUiState(
    val questions: List<Question> = emptyList(),
    val filter: FeedFilter = FeedFilter.ACTIVE,
    val isRefreshing: Boolean = false,
    val error: FeedError? = null,
    val searchQuery: String = "",
    val selectedTag: String? = null,
    val availableTags: List<String> = emptyList(),
    val sort: FeedSort = FeedSort.RESOLVE_BY,
    val isInitialLoad: Boolean = true,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isOffline: Boolean = false,
    val syncErrors: List<SyncErrorEntry> = emptyList(),
    val showSyncErrorsSheet: Boolean = false,
)

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repository: QuestionRepository,
    networkMonitor: NetworkMonitor,
    private val prefs: UserPreferences,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private var retryCount = 0

    private val _filter = MutableStateFlow(FeedFilter.ACTIVE)
    private val _isRefreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<FeedError?>(null)
    private val _detail = MutableStateFlow(DetailSheetState())
    private val _searchQuery = MutableStateFlow("")
    private val _selectedTag = MutableStateFlow<String?>(null)
    private val _isInitialLoad = MutableStateFlow(true)
    private val _hasMore = MutableStateFlow(false)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _showSyncErrorsSheet = MutableStateFlow(false)

    private val isOffline: Flow<Boolean> = networkMonitor.isOnline.map { !it }

    private val syncErrors: Flow<List<SyncErrorEntry>> = repository.observeErroredMutations()
        .map { rows -> rows.map { it.toUi() } }

    private val sort: Flow<FeedSort> = prefs.feedSort.map { FeedSort.fromName(it) }

    @OptIn(FlowPreview::class)
    private val debouncedQuery: Flow<String> = _searchQuery
        .debounce(100)
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val questionsFlow: Flow<List<Question>> = _filter
        .flatMapLatest { filter ->
            when (filter) {
                FeedFilter.ACTIVE -> repository.observeActive()
                FeedFilter.READY_TO_RESOLVE -> repository.observeReadyToResolve()
                FeedFilter.RESOLVED -> repository.observeResolved()
            }
        }
        .combine(debouncedQuery) { questions, query ->
            if (query.isBlank()) questions
            else questions.filter { it.title.contains(query, ignoreCase = true) }
        }
        .combine(_selectedTag) { questions, tag ->
            if (tag == null) questions
            else questions.filter { tag in it.tags }
        }
        .combine(sort) { questions, sort ->
            when (sort) {
                // RESOLVE_BY keeps the DAO order (resolveBy ASC for active,
                // DESC for resolved) — the app's original behavior.
                FeedSort.RESOLVE_BY -> questions
                FeedSort.CREATED_NEWEST -> questions.sortedByDescending { it.createdAt }
            }
        }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)

    /** All tag names across the cache, independent of the active filter. */
    private val availableTags: Flow<List<String>> = repository.observeAllTags()
        .map { tags -> tags.sortedBy { it.lowercase() } }

    // Detail-sheet state is deliberately NOT part of uiState: slider drags and
    // comment keystrokes update it at frame rate, and folding it into the big
    // combine would rebuild + deep-compare the entire FeedUiState (including the
    // question list) on every frame.
    val detail: StateFlow<DetailSheetState> = _detail.asStateFlow()

    val uiState: StateFlow<FeedUiState> = combine(
        listOf(
            questionsFlow, _filter, _isRefreshing, _error,
            _searchQuery, _selectedTag, availableTags, sort, _isInitialLoad,
            _hasMore, _isLoadingMore, isOffline, syncErrors, _showSyncErrorsSheet,
        )
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        FeedUiState(
            questions = args[0] as List<Question>,
            filter = args[1] as FeedFilter,
            isRefreshing = args[2] as Boolean,
            error = args[3] as FeedError?,
            searchQuery = args[4] as String,
            selectedTag = args[5] as String?,
            availableTags = args[6] as List<String>,
            sort = args[7] as FeedSort,
            isInitialLoad = args[8] as Boolean,
            hasMore = args[9] as Boolean,
            isLoadingMore = args[10] as Boolean,
            isOffline = args[11] as Boolean,
            syncErrors = args[12] as List<SyncErrorEntry>,
            showSyncErrorsSheet = args[13] as Boolean,
        )
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

    /** Filter the feed to questions carrying [tag]; null clears the filter. */
    fun setSelectedTag(tag: String?) {
        _selectedTag.value = tag
    }

    /** Persisted — the chosen order survives app restarts. */
    fun setSort(sort: FeedSort) {
        viewModelScope.launch {
            prefs.setFeedSort(sort.name)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            if (retryCount > 0) {
                val delayMs = 1000L * (1 shl (retryCount - 1).coerceAtMost(4))
                delay(delayMs)
            }
            _isRefreshing.value = true
            _error.value = null
            try {
                repository.refresh()
                _hasMore.value = repository.hasMore()
                retryCount = 0
            } catch (e: Exception) {
                retryCount++
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
            pieValues = if (question.isPieEditable) {
                PieChartMath.initialValues(question.options.map { it.latestForecast })
            } else {
                emptyList()
            },
        )
        viewModelScope.launch {
            _detail.update { it.copy(isLoadingComments = true) }
            try {
                val comments = repository.getCommentsForQuestion(question.id)
                val history = repository.getForecastsForQuestion(question.id)
                _detail.update {
                    it.copy(
                        comments = comments,
                        forecastHistory = history,
                        isLoadingComments = false,
                    )
                }
            } catch (e: Exception) {
                _detail.update { it.copy(isLoadingComments = false) }
            }
        }
    }

    fun dismissDetailSheet() {
        _detail.value = DetailSheetState()
    }

    /** Open the detail sheet for a cached question by id (notification taps). */
    fun openQuestionById(questionId: String) {
        viewModelScope.launch {
            repository.getQuestion(questionId)?.let { showDetailSheet(it) }
        }
    }

    fun setForecastSliderValue(value: Float) {
        _detail.update { it.copy(forecastSliderValue = value) }
    }

    /**
     * Shared skeleton for detail-sheet mutations: flip a loading flag, run the
     * repository call, then dismiss the sheet on success or surface the error
     * and clear the flag on failure.
     */
    private fun detailMutation(
        errorMessage: String,
        setLoading: (DetailSheetState, Boolean) -> DetailSheetState,
        block: suspend (Question) -> Unit,
    ) {
        val question = _detail.value.question ?: return
        viewModelScope.launch {
            _detail.update { setLoading(it, true) }
            try {
                block(question)
                _detail.value = DetailSheetState()
            } catch (e: Exception) {
                _error.value = classifyError(e, errorMessage)
                _detail.update { setLoading(it, false) }
            }
        }
    }

    fun updateForecast() = detailMutation(
        "Failed to update forecast",
        { state, loading -> state.copy(isUpdatingForecast = loading) },
    ) { question ->
        repository.addForecast(question.id, _detail.value.forecastSliderValue.toDouble())
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
        val state = _detail.value
        detailMutation(
            "Failed to save changes",
            { s, loading -> s.copy(isSaving = loading) },
        ) { question ->
            repository.editQuestion(
                questionId = question.id,
                title = state.editTitle.takeIf { it != question.title },
                resolveBy = state.editResolveBy,
                notes = state.editNotes.takeIf { it != (question.notes ?: "") },
            )
        }
    }

    // --- Delete ---

    fun requestDelete() {
        _detail.update { it.copy(showDeleteConfirmation = true) }
    }

    fun dismissDeleteConfirmation() {
        _detail.update { it.copy(showDeleteConfirmation = false) }
    }

    fun confirmDelete() = detailMutation(
        "Failed to delete question",
        { state, loading ->
            if (loading) state.copy(isDeleting = true, showDeleteConfirmation = false)
            else state.copy(isDeleting = false)
        },
    ) { question ->
        repository.deleteQuestion(question.id)
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
            try {
                val newComment = repository.addComment(question.id, text)
                _detail.update {
                    it.copy(
                        comments = it.comments + newComment,
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
        val newShared = !question.sharedPublicly
        viewModelScope.launch {
            try {
                repository.setSharedPublicly(question.id, newShared, question.unlisted)
                _detail.update {
                    it.copy(question = question.copy(sharedPublicly = newShared))
                }
            } catch (e: Exception) {
                _error.value = classifyError(e, "Failed to update sharing")
            }
        }
    }

    /**
     * Quick action for overdue questions: bump the resolve-by date forward
     * from TODAY (an overdue date plus a week could still be in the past).
     */
    fun pushResolveBy(period: java.time.Period) = detailMutation(
        "Failed to update resolve date",
        { state, loading -> state.copy(isSaving = loading) },
    ) { question ->
        repository.editQuestion(
            questionId = question.id,
            resolveBy = LocalDate.now().plus(period),
        )
    }

    // --- Multiple choice ---

    fun toggleOptionExpanded(optionId: String) {
        _detail.update { state ->
            if (state.expandedOptionId == optionId) {
                state.copy(expandedOptionId = null)
            } else {
                val current = state.question?.options
                    ?.firstOrNull { it.id == optionId }
                    ?.latestForecast
                state.copy(
                    expandedOptionId = optionId,
                    optionSliderValue = current?.toFloat() ?: 0.5f,
                )
            }
        }
    }

    fun setOptionSliderValue(value: Float) {
        _detail.update { it.copy(optionSliderValue = value) }
    }

    fun updateOptionForecast() {
        val state = _detail.value
        val optionId = state.expandedOptionId ?: return
        detailMutation(
            "Failed to update forecast",
            { s, loading -> s.copy(isUpdatingForecast = loading) },
        ) { question ->
            repository.addForecast(question.id, state.optionSliderValue.toDouble(), optionId)
        }
    }

    /** New pie-editor fractions from a drag; aligned with question.options. */
    fun setPieValues(values: List<Float>) {
        _detail.update { it.copy(pieValues = values) }
    }

    /**
     * Submit the pie editor's values: one forecast per option whose value
     * changed (as a whole percent) from its current latest forecast, so
     * untouched options don't get duplicate forecasts.
     */
    fun updatePieForecasts() {
        val state = _detail.value
        val question = state.question ?: return
        if (state.pieValues.size != question.options.size) return
        detailMutation(
            "Failed to update forecasts",
            { s, loading -> s.copy(isUpdatingForecast = loading) },
        ) {
            question.options.zip(state.pieValues).forEach { (option, value) ->
                val rounded = (value * 100).roundToInt() / 100.0
                if (PieChartMath.differsAsPercent(option.latestForecast, rounded)) {
                    repository.addForecast(question.id, rounded, option.id)
                }
            }
        }
    }

    /**
     * Resolve an exclusive multiple-choice question to an option's text,
     * [McResolution.OTHER], or [McResolution.AMBIGUOUS].
     */
    fun resolveMcExclusive(resolution: String) = detailMutation(
        "Failed to resolve question",
        { state, loading -> state.copy(isResolving = loading) },
    ) { question ->
        repository.resolveMultipleChoice(question.id, resolution)
    }

    /** Resolve one option of a non-exclusive multiple-choice question. */
    fun resolveMcOption(optionId: String, resolvedYes: Boolean) = detailMutation(
        "Failed to resolve option",
        { state, loading -> state.copy(isResolving = loading) },
    ) { question ->
        repository.resolveOption(question.id, optionId, resolvedYes)
    }

    // --- Resolve flow ---

    fun resolveQuestion(resolution: Resolution) = detailMutation(
        "Failed to resolve question",
        { state, loading -> state.copy(isResolving = loading) },
    ) { question ->
        repository.resolveQuestion(question.id, resolution)
    }

    fun dismissError() {
        _error.value = null
    }

    // --- Sync errors ---

    fun showSyncErrorsSheet() {
        _showSyncErrorsSheet.value = true
    }

    fun dismissSyncErrorsSheet() {
        _showSyncErrorsSheet.value = false
    }

    fun retryAllSyncErrors() {
        viewModelScope.launch {
            repository.retryAllErroredMutations()
            _showSyncErrorsSheet.value = false
        }
    }

    fun discardSyncError(id: Long) {
        viewModelScope.launch {
            repository.discardErroredMutation(id)
        }
    }

    private fun classifyError(e: Exception, fallback: String): FeedError {
        return when (e) {
            is IOException -> FeedError.Network(e.message ?: fallback)
            is HttpException -> when (e.code()) {
                401, 403 -> FeedError.Auth("API key invalid or expired. Update it in Settings.")
                429 -> FeedError.RateLimited("Too many requests. Please try again later.")
                else -> FeedError.Other("Server error (${e.code()})")
            }
            else -> FeedError.Other(e.message ?: fallback)
        }
    }

    private fun PendingMutationEntity.toUi(): SyncErrorEntry = SyncErrorEntry(
        id = id,
        type = when (type) {
            PendingMutationEntity.TYPE_CREATE_QUESTION -> "Create question"
            PendingMutationEntity.TYPE_ADD_FORECAST -> "Update forecast"
            PendingMutationEntity.TYPE_RESOLVE -> "Resolve"
            PendingMutationEntity.TYPE_EDIT -> "Edit"
            PendingMutationEntity.TYPE_DELETE -> "Delete"
            PendingMutationEntity.TYPE_SET_SHARED -> "Visibility change"
            PendingMutationEntity.TYPE_ADD_COMMENT -> "Add comment"
            else -> type
        },
        message = lastError ?: "Sync failed",
    )
}
