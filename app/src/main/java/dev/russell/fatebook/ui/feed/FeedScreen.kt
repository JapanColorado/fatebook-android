package dev.russell.fatebook.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.Visibility
import dev.russell.fatebook.ui.components.ErrorBanner
import dev.russell.fatebook.ui.components.LedgerMargin
import dev.russell.fatebook.ui.components.LedgerRule
import dev.russell.fatebook.ui.components.MastheadRule
import dev.russell.fatebook.ui.components.OfflineBanner
import dev.russell.fatebook.ui.components.QuestionCard
import dev.russell.fatebook.ui.components.ShimmerQuestionCardList
import dev.russell.fatebook.ui.components.SyncErrorsSheet
import dev.russell.fatebook.ui.components.SyncIssuesBanner
import dev.russell.fatebook.ui.detail.McActions
import dev.russell.fatebook.ui.detail.QuestionDetailSheet
import dev.russell.fatebook.ui.theme.Ledger
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onSettingsClick: () -> Unit,
    initialFilter: FeedFilter? = null,
    initialQuestionId: String? = null,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()

    // Launch-intent targets apply once. This composable leaves composition on every
    // tab switch; re-applying them on return would undo the user's own filter and
    // reopen a sheet they'd already closed.
    var launchTargetsApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (launchTargetsApplied) return@LaunchedEffect
        launchTargetsApplied = true
        initialFilter?.let(viewModel::setFilter)
        // A per-question notification tap opens that question's detail sheet
        initialQuestionId?.let(viewModel::openQuestionById)
    }

    // The undo strip can't be seen from another tab or from outside the app, so
    // an action in its window commits rather than waiting on an invisible timer.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flushQuickAction() }
    DisposableEffect(viewModel) {
        onDispose { viewModel.flushQuickAction() }
    }

    // McActions is a constructor call, which strong skipping does not memoize —
    // remember it so QuestionCard/detail-sheet callbacks keep a stable identity.
    val mcActions = remember(viewModel) {
        McActions(
            onOptionDraftChange = viewModel::setOptionDraft,
            onBalanceOptionDrafts = viewModel::balanceOptionDrafts,
            onUpdateOptionForecasts = viewModel::updateOptionForecasts,
            onResolveExclusive = viewModel::resolveMcExclusive,
            onResolveOption = viewModel::resolveMcOption,
        )
    }

    FeedScreenContent(
        state = state,
        detail = detail,
        onSettingsClick = onSettingsClick,
        onFilterSelected = viewModel::setFilter,
        onSearchQueryChanged = viewModel::setSearchQuery,
        onTagSelected = viewModel::setSelectedTag,
        onSortSelected = viewModel::setSort,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onQuestionClick = viewModel::showDetailSheet,
        onQuickAction = viewModel::quickAction,
        onUndoQuickAction = viewModel::undoQuickAction,
        onResolve = viewModel::resolveQuestion,
        onForecastSliderChange = viewModel::setForecastSliderValue,
        onUpdateForecast = viewModel::updateForecast,
        mcActions = mcActions,
        onPushResolveBy = viewModel::pushResolveBy,
        onDismissDetailSheet = viewModel::dismissDetailSheet,
        onDismissError = viewModel::dismissError,
        onEnterEditMode = viewModel::enterEditMode,
        onEditTitleChange = viewModel::setEditTitle,
        onEditResolveByChange = viewModel::setEditResolveBy,
        onEditNotesChange = viewModel::setEditNotes,
        onSaveEdit = viewModel::saveEdit,
        onCancelEdit = viewModel::cancelEdit,
        onDeleteClick = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDeleteConfirmation = viewModel::dismissDeleteConfirmation,
        onCommentTextChange = viewModel::setCommentText,
        onAddComment = viewModel::addComment,
        onVisibilityChange = viewModel::setVisibility,
        onShowSyncErrors = viewModel::showSyncErrorsSheet,
        onDismissSyncErrorsSheet = viewModel::dismissSyncErrorsSheet,
        onRetryAllSyncErrors = viewModel::retryAllSyncErrors,
        onDiscardSyncError = viewModel::discardSyncError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreenContent(
    state: FeedUiState,
    detail: DetailSheetState = DetailSheetState(),
    today: LocalDate = LocalDate.now(),
    onSettingsClick: () -> Unit = {},
    onFilterSelected: (FeedFilter) -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onTagSelected: (String?) -> Unit = {},
    onSortSelected: (FeedSort) -> Unit = {},
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onQuestionClick: (Question) -> Unit = {},
    onQuickAction: (Question, QuickAction) -> Unit = { _, _ -> },
    onUndoQuickAction: () -> Unit = {},
    onResolve: (dev.russell.fatebook.domain.model.Resolution) -> Unit = {},
    onForecastSliderChange: (Float) -> Unit = {},
    onUpdateForecast: () -> Unit = {},
    mcActions: McActions = McActions(),
    onPushResolveBy: (java.time.Period) -> Unit = {},
    onDismissDetailSheet: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onEnterEditMode: () -> Unit = {},
    onEditTitleChange: (String) -> Unit = {},
    onEditResolveByChange: (java.time.LocalDate) -> Unit = {},
    onEditNotesChange: (String) -> Unit = {},
    onSaveEdit: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onDismissDeleteConfirmation: () -> Unit = {},
    onCommentTextChange: (String) -> Unit = {},
    onAddComment: () -> Unit = {},
    onVisibilityChange: (Visibility) -> Unit = {},
    onShowSyncErrors: () -> Unit = {},
    onDismissSyncErrorsSheet: () -> Unit = {},
    onRetryAllSyncErrors: () -> Unit = {},
    onDiscardSyncError: (Long) -> Unit = {},
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Local state avoids a StateFlow round-trip on every keystroke.
            var searchText by remember { mutableStateOf(state.searchQuery) }
            var searchOpen by rememberSaveable { mutableStateOf(state.searchQuery.isNotEmpty()) }

            Masthead(
                today = today,
                readyCount = state.readyCount,
                openCount = state.openCount,
                onSearchClick = {
                    searchOpen = !searchOpen
                    if (!searchOpen) {
                        searchText = ""
                        onSearchQueryChanged("")
                    }
                },
                searchOpen = searchOpen,
                actions = {
                    if (state.availableTags.isNotEmpty()) {
                        TagMenuAction(
                            availableTags = state.availableTags,
                            selectedTag = state.selectedTag,
                            onTagSelected = onTagSelected,
                        )
                    }
                    SortMenuAction(sort = state.sort, onSortSelected = onSortSelected)
                },
            )

            AnimatedVisibility(searchOpen) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        onSearchQueryChanged(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LedgerMargin, vertical = 8.dp),
                    placeholder = { Text("Titles, notes, tags, comments") },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = {
                                searchText = ""
                                onSearchQueryChanged("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                )
            }

            FilterTabs(
                selected = state.filter,
                readyCount = state.readyCount,
                onSelected = onFilterSelected,
            )
            LedgerRule()

            state.selectedTag?.let { tag ->
                ActiveTagLine(tag = tag, onClear = { onTagSelected(null) })
                LedgerRule()
            }

            if (state.error != null) {
                ErrorBanner(
                    message = state.error.message,
                    onRetry = onRefresh,
                    onDismiss = onDismissError,
                    actionLabel = if (state.error is FeedError.Auth) "Settings" else "Retry",
                    onAction = if (state.error is FeedError.Auth) onSettingsClick else null,
                )
            }

            if (state.isOffline) {
                OfflineBanner()
            }

            if (state.syncErrors.isNotEmpty()) {
                SyncIssuesBanner(
                    count = state.syncErrors.size,
                    onView = onShowSyncErrors,
                )
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (state.isInitialLoad) {
                    ShimmerQuestionCardList()
                } else if (state.questions.isEmpty() && !state.isRefreshing) {
                    EmptyLedger(state)
                } else {
                    val listState = rememberLazyListState()

                    // snapshotFlow instead of derivedStateOf: layoutInfo is
                    // recreated every measure pass, so a derivedStateOf over it
                    // recomputes (and allocates) on every scroll frame.
                    LaunchedEffect(listState) {
                        snapshotFlow {
                            val info = listState.layoutInfo
                            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisible >= info.totalItemsCount - 3
                        }
                            .distinctUntilChanged()
                            .collect { nearEnd -> if (nearEnd) onLoadMore() }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            state.questions,
                            key = { it.id },
                            contentType = { "question" },
                        ) { question ->
                            Column(Modifier.animateItem()) {
                                if (question.resolved || !isDue(question, today)) {
                                    QuestionCard(
                                        question = question,
                                        onClick = onQuestionClick,
                                        today = today,
                                    )
                                } else {
                                    DueRow(
                                        question = question,
                                        today = today,
                                        onClick = onQuestionClick,
                                        onQuickAction = onQuickAction,
                                    )
                                }
                                LedgerRule()
                            }
                        }
                        if (state.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(state.pendingQuickAction != null) {
                // Held so the strip keeps its text while animating out.
                var last by remember { mutableStateOf(state.pendingQuickAction) }
                state.pendingQuickAction?.let { last = it }
                last?.let { UndoStrip(pending = it, onUndo = onUndoQuickAction) }
            }
        }
    }

    // Sync errors sheet
    if (state.showSyncErrorsSheet) {
        SyncErrorsSheet(
            errors = state.syncErrors,
            onRetryAll = onRetryAllSyncErrors,
            onDiscard = onDiscardSyncError,
            onDismiss = onDismissSyncErrorsSheet,
        )
    }

    // Detail bottom sheet — reads only `detail`, so slider drags and comment
    // keystrokes recompose this call alone, not the feed behind the sheet.
    detail.question?.let { question ->
        QuestionDetailSheet(
            question = question,
            detailState = detail,
            onForecastSliderChange = onForecastSliderChange,
            onUpdateForecast = onUpdateForecast,
            onResolve = onResolve,
            mcActions = mcActions,
            onPushResolveBy = onPushResolveBy,
            onEnterEditMode = onEnterEditMode,
            onEditTitleChange = onEditTitleChange,
            onEditResolveByChange = onEditResolveByChange,
            onEditNotesChange = onEditNotesChange,
            onSaveEdit = onSaveEdit,
            onCancelEdit = onCancelEdit,
            onDeleteClick = onDeleteClick,
            onConfirmDelete = onConfirmDelete,
            onDismissDeleteConfirmation = onDismissDeleteConfirmation,
            onCommentTextChange = onCommentTextChange,
            onAddComment = onAddComment,
            onVisibilityChange = onVisibilityChange,
            onDismiss = onDismissDetailSheet,
        )
    }
}

private fun isDue(question: Question, today: LocalDate): Boolean =
    !today.isBefore(question.resolveByDate)

private val MASTHEAD_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

/** Front page of the ledger: wordmark, today's date, and what needs doing. */
@Composable
private fun Masthead(
    today: LocalDate,
    readyCount: Int,
    openCount: Int,
    searchOpen: Boolean,
    onSearchClick: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = LedgerMargin)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Fatebook",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            IconButton(onClick = onSearchClick) {
                Icon(
                    if (searchOpen) Icons.Default.SearchOff else Icons.Default.Search,
                    contentDescription = if (searchOpen) "Close search" else "Search",
                )
            }
            actions()
        }
        MastheadRule(Modifier.padding(top = 2.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = today.format(MASTHEAD_DATE).uppercase(Locale.US),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = buildAnnotatedString {
                    if (readyCount > 0) {
                        withStyle(SpanStyle(color = Ledger.colors.accent)) {
                            append("$readyCount DUE")
                        }
                        append(" · ")
                    }
                    append("$openCount OPEN")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Text tabs in place of chips: one line however many there are, with the count
 * on Due as the only thing competing for attention.
 */
@Composable
private fun FilterTabs(
    selected: FeedFilter,
    readyCount: Int,
    onSelected: (FeedFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = LedgerMargin - 8.dp)
            .selectableGroup(),
    ) {
        FeedFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            val underline by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                label = "tabUnderline",
            )
            val label = when (filter) {
                FeedFilter.ACTIVE -> "Open"
                FeedFilter.READY_TO_RESOLVE -> "Due"
                FeedFilter.RESOLVING_SOON -> "Soon"
                FeedFilter.RESOLVED -> "Resolved"
            }
            Column(
                modifier = Modifier
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelected(filter) },
                    )
                    .padding(horizontal = 8.dp)
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (filter == FeedFilter.READY_TO_RESOLVE && readyCount > 0) {
                        Text(
                            text = "$readyCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .background(Ledger.colors.accent, MaterialTheme.shapes.small)
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
                Spacer(
                    Modifier
                        .padding(top = 8.dp)
                        .height(2.dp)
                        .width(28.dp)
                        .background(underline),
                )
            }
        }
    }
}

@Composable
private fun ActiveTagLine(tag: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = LedgerMargin, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "FILED UNDER #$tag",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onClear) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Clear tag filter",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun EmptyLedger(state: FeedUiState) {
    val (headline, detail) = when {
        state.searchQuery.isNotBlank() ->
            "Nothing on record" to "No predictions matching \"${state.searchQuery}\""
        state.filter == FeedFilter.ACTIVE ->
            "No predictions yet" to "Tap + to create your first prediction"
        state.filter == FeedFilter.READY_TO_RESOLVE ->
            "All settled" to "Nothing to resolve"
        state.filter == FeedFilter.RESOLVING_SOON ->
            "A quiet week" to "Nothing resolving this week"
        else ->
            "The record is empty" to "No resolved predictions yet"
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LedgerMargin, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SortMenuAction(
    sort: FeedSort,
    onSortSelected: (FeedSort) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            FeedSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (option) {
                                FeedSort.RESOLVE_BY -> "By resolve date (soonest first)"
                                FeedSort.CREATED_NEWEST -> "Newest first"
                            },
                        )
                    },
                    leadingIcon = {
                        RadioButton(
                            selected = sort == option,
                            onClick = null,
                        )
                    },
                    onClick = {
                        onSortSelected(option)
                        menuExpanded = false
                    },
                )
            }
        }
    }
}

/**
 * A menu of the user's tags: tags are unbounded, so one chip per tag would crowd
 * out the main filters.
 */
@Composable
private fun TagMenuAction(
    availableTags: List<String>,
    selectedTag: String?,
    onTagSelected: (String?) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                Icons.AutoMirrored.Filled.Label,
                contentDescription = if (selectedTag != null) "Tags: $selectedTag" else "Tags",
                tint = if (selectedTag != null) Ledger.colors.accent else LocalContentColor.current,
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("All tags") },
                onClick = {
                    onTagSelected(null)
                    menuExpanded = false
                },
            )
            availableTags.forEach { tag ->
                DropdownMenuItem(
                    text = { Text("#$tag") },
                    onClick = {
                        onTagSelected(tag)
                        menuExpanded = false
                    },
                )
            }
        }
    }
}
