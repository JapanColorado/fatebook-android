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
import dev.russell.fatebook.domain.model.Visibility
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.di.DefaultDispatcher
import dev.russell.fatebook.ui.components.OptionForecastMath
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

enum class FeedFilter {
    ACTIVE,
    READY_TO_RESOLVE,
    RESOLVING_SOON,
    RESOLVED,
    ;

    companion object {
        /** RESOLVING_SOON shows unresolved questions due within this many days (excluding today). */
        const val RESOLVING_SOON_DAYS = 7
    }
}

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
    // Multiple choice: the option editor's whole percents by option id. An option
    // with no entry has no forecast yet. Empty when the editor doesn't apply.
    val optionDrafts: Map<String, Int> = emptyMap(),
    val isEditing: Boolean = false,
    val editTitle: String = "",
    val editResolveBy: LocalDate? = null,
    val editNotes: String = "",
    val comments: List<Comment> = emptyList(),
    val isLoadingComments: Boolean = false,
    val forecastHistory: List<Forecast> = emptyList(),
    /** Inferred id of the signed-in user, for picking "your" forecasts out of [forecastHistory]. */
    val currentUserId: String? = null,
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
    /** Unresolved questions at or past their date: drives the Due tab badge and masthead. */
    val readyCount: Int = 0,
    /** All unresolved questions. */
    val openCount: Int = 0,
    /** A row action inside its undo window; its row is hidden until it commits or is undone. */
    val pendingQuickAction: PendingQuickAction? = null,
)

/** One-tap (or swipe) actions on a due row, without opening the detail sheet. */
sealed interface QuickAction {
    data class Resolve(val resolution: Resolution) : QuickAction
    data object SnoozeWeek : QuickAction
}

data class PendingQuickAction(val question: Question, val action: QuickAction)

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
    private val _pendingQuickAction = MutableStateFlow<PendingQuickAction?>(null)
    private var quickActionJob: Job? = null

    private val readyCount: Flow<Int> =
        repository.observeReadyToResolve().map { it.size }.distinctUntilChanged()
    private val openCount: Flow<Int> =
        repository.observeActive().map { it.size }.distinctUntilChanged()

    private val isOffline: Flow<Boolean> = networkMonitor.isOnline.map { !it }

    private val syncErrors: Flow<List<SyncErrorEntry>> = repository.observeErroredMutations()
        .map { rows -> rows.map { it.toUi() } }

    private val sort: Flow<FeedSort> = prefs.feedSort.map { FeedSort.fromName(it) }

    @OptIn(FlowPreview::class)
    private val debouncedQuery: Flow<String> = _searchQuery
        .debounce(100)
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val questionsByFilter: Flow<List<Question>> = _filter
        .flatMapLatest { filter ->
            when (filter) {
                FeedFilter.ACTIVE -> repository.observeActive()
                FeedFilter.READY_TO_RESOLVE -> repository.observeReadyToResolve()
                FeedFilter.RESOLVING_SOON ->
                    repository.observeResolvingSoon(FeedFilter.RESOLVING_SOON_DAYS)
                FeedFilter.RESOLVED -> repository.observeResolved()
            }
        }

    // Comments aren't on feed Question objects, so search reads a separate
    // questionId → text index. Only observed while a query is active.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val commentIndex: Flow<Map<String, String>> = debouncedQuery
        .map { it.isBlank() }
        .distinctUntilChanged()
        .flatMapLatest { blank ->
            if (blank) flowOf(emptyMap()) else repository.observeCommentSearchIndex()
        }

    private val questionsFlow: Flow<List<Question>> =
        combine(questionsByFilter, debouncedQuery, commentIndex) { questions, query, comments ->
            if (query.isBlank()) {
                questions
            } else {
                val q = query.trim().lowercase()
                questions.filter { QuestionSearch.matches(it, comments[it.id], q) }
            }
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
            readyCount, openCount, _pendingQuickAction,
        )
    ) { args ->
        val pending = args[16] as PendingQuickAction?
        @Suppress("UNCHECKED_CAST")
        FeedUiState(
            questions = (args[0] as List<Question>).let { questions ->
                if (pending == null) questions else questions.filterNot { it.id == pending.question.id }
            },
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
            readyCount = args[14] as Int,
            openCount = args[15] as Int,
            pendingQuickAction = pending,
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
            currentUserId = prefs.userId,
            forecastSliderValue = question.yourLatestForecast?.toFloat() ?: 0.5f,
            optionDrafts = if (question.hasOptionForecastEditor) {
                OptionForecastMath.initialDrafts(question.options)
            } else {
                emptyMap()
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

    fun setVisibility(visibility: Visibility) {
        val question = _detail.value.question ?: return
        if (question.visibility == visibility) return
        val shared = visibility != Visibility.PRIVATE
        val unlisted = visibility == Visibility.UNLISTED
        viewModelScope.launch {
            try {
                repository.setSharedPublicly(question.id, shared, unlisted)
                _detail.update {
                    it.copy(question = question.copy(sharedPublicly = shared, unlisted = unlisted))
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

    // --- Row quick actions ---

    /**
     * Run [action] on [question] after an undo window. Nothing is written until
     * the window closes: resolutions can't be taken back through the public API,
     * and a swipe is easy to do by accident. A second action commits the first.
     */
    fun quickAction(question: Question, action: QuickAction) {
        flushQuickAction()
        _pendingQuickAction.value = PendingQuickAction(question, action)
        quickActionJob = viewModelScope.launch {
            delay(QUICK_ACTION_UNDO_MS)
            flushQuickAction()
        }
    }

    fun undoQuickAction() {
        quickActionJob?.cancel()
        _pendingQuickAction.value = null
    }

    /** Commit now, e.g. the screen is going away and can't offer Undo any longer. */
    fun flushQuickAction() {
        val job = quickActionJob
        quickActionJob = null
        val pending = _pendingQuickAction.value ?: return
        _pendingQuickAction.value = null
        // NonCancellable: an app close flushes moments before viewModelScope dies.
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    when (val action = pending.action) {
                        is QuickAction.Resolve ->
                            repository.resolveQuestion(pending.question.id, action.resolution)
                        QuickAction.SnoozeWeek -> repository.editQuestion(
                            questionId = pending.question.id,
                            resolveBy = LocalDate.now().plusWeeks(1),
                        )
                    }
                } catch (e: Exception) {
                    _error.value = classifyError(e, "Failed to update question")
                }
            }
        }
        // Last: when the timer itself lands here, this cancels the running coroutine.
        job?.cancel()
    }

    // --- Multiple choice ---

    fun setOptionDraft(optionId: String, percent: Int) {
        _detail.update { it.copy(optionDrafts = it.optionDrafts + (optionId to percent)) }
    }

    /** Scale an over-allocated exclusive question's drafts down to 100%. */
    fun balanceOptionDrafts() {
        _detail.update { it.copy(optionDrafts = OptionForecastMath.balance(it.optionDrafts)) }
    }

    /**
     * Submit the option editor: one forecast per option whose whole percent
     * changed, so untouched options don't get duplicate forecasts. An exclusive
     * question over 100% has to be brought back within it first.
     */
    fun updateOptionForecasts() {
        val state = _detail.value
        val question = state.question ?: return
        if (question.exclusiveAnswers && OptionForecastMath.overBy(state.optionDrafts) > 0) return
        detailMutation(
            "Failed to update forecasts",
            { s, loading -> s.copy(isUpdatingForecast = loading) },
        ) {
            OptionForecastMath.changes(question.options, state.optionDrafts).forEach { (option, percent) ->
                repository.addForecast(question.id, percent / 100.0, option.id)
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

    private companion object {
        const val QUICK_ACTION_UNDO_MS = 5_000L
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
