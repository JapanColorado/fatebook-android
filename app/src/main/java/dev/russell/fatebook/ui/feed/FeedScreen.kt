package dev.russell.fatebook.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.russell.fatebook.ui.components.ErrorBanner
import dev.russell.fatebook.ui.components.OfflineBanner
import dev.russell.fatebook.ui.components.QuestionCard
import dev.russell.fatebook.ui.components.ShimmerQuestionCardList
import dev.russell.fatebook.ui.components.SyncErrorsSheet
import dev.russell.fatebook.ui.components.SyncIssuesBanner
import dev.russell.fatebook.ui.detail.QuestionDetailSheet
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedScreen(
    onCreateClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAnalyticsClick: () -> Unit,
    initialFilter: FeedFilter? = null,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Handle initial filter from notification
    LaunchedEffect(initialFilter) {
        if (initialFilter != null) {
            viewModel.setFilter(initialFilter)
        }
    }

    FeedScreenContent(
        state = state,
        onCreateClick = onCreateClick,
        onSettingsClick = onSettingsClick,
        onAnalyticsClick = onAnalyticsClick,
        onFilterSelected = viewModel::setFilter,
        onSearchQueryChanged = viewModel::setSearchQuery,
        onTagSelected = viewModel::setSelectedTag,
        onSortSelected = viewModel::setSort,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onQuestionClick = viewModel::showDetailSheet,
        onResolve = viewModel::resolveQuestion,
        onForecastSliderChange = viewModel::setForecastSliderValue,
        onUpdateForecast = viewModel::updateForecast,
        onToggleOptionExpanded = viewModel::toggleOptionExpanded,
        onOptionSliderChange = viewModel::setOptionSliderValue,
        onUpdateOptionForecast = viewModel::updateOptionForecast,
        onResolveMcExclusive = viewModel::resolveMcExclusive,
        onResolveMcOption = viewModel::resolveMcOption,
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
        onToggleSharedPublicly = viewModel::toggleSharedPublicly,
        onShowSyncErrors = viewModel::showSyncErrorsSheet,
        onDismissSyncErrorsSheet = viewModel::dismissSyncErrorsSheet,
        onRetryAllSyncErrors = viewModel::retryAllSyncErrors,
        onDiscardSyncError = viewModel::discardSyncError,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedScreenContent(
    state: FeedUiState,
    onCreateClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAnalyticsClick: () -> Unit = {},
    onFilterSelected: (FeedFilter) -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onTagSelected: (String?) -> Unit = {},
    onSortSelected: (FeedSort) -> Unit = {},
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onQuestionClick: (dev.russell.fatebook.domain.model.Question) -> Unit = {},
    onResolve: (dev.russell.fatebook.domain.model.Resolution) -> Unit = {},
    onForecastSliderChange: (Float) -> Unit = {},
    onUpdateForecast: () -> Unit = {},
    onToggleOptionExpanded: (String) -> Unit = {},
    onOptionSliderChange: (Float) -> Unit = {},
    onUpdateOptionForecast: () -> Unit = {},
    onResolveMcExclusive: (String) -> Unit = {},
    onResolveMcOption: (String, Boolean) -> Unit = { _, _ -> },
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
    onToggleSharedPublicly: () -> Unit = {},
    onShowSyncErrors: () -> Unit = {},
    onDismissSyncErrorsSheet: () -> Unit = {},
    onRetryAllSyncErrors: () -> Unit = {},
    onDiscardSyncError: (Long) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fatebook") },
                actions = {
                    SortMenuAction(sort = state.sort, onSortSelected = onSortSelected)
                    IconButton(onClick = onAnalyticsClick) {
                        Icon(Icons.Default.BarChart, contentDescription = "Analytics")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick) {
                Icon(Icons.Default.Add, contentDescription = "New prediction")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Search bar — local state avoids StateFlow round-trip lag
            var searchText by remember { mutableStateOf(state.searchQuery) }
            OutlinedTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    onSearchQueryChanged(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search predictions...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
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
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
            )

            // Filter chips
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FeedFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { onFilterSelected(filter) },
                        label = {
                            Text(
                                when (filter) {
                                    FeedFilter.ACTIVE -> "Active"
                                    FeedFilter.READY_TO_RESOLVE -> "Ready to Resolve"
                                    FeedFilter.RESOLVED -> "Resolved"
                                }
                            )
                        },
                    )
                }
                if (state.availableTags.isNotEmpty()) {
                    TagFilterChip(
                        availableTags = state.availableTags,
                        selectedTag = state.selectedTag,
                        onTagSelected = onTagSelected,
                    )
                }
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
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.isInitialLoad) {
                    ShimmerQuestionCardList()
                } else if (state.questions.isEmpty() && !state.isRefreshing) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.searchQuery.isBlank() && state.filter == FeedFilter.ACTIVE) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No predictions yet",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Tap + to create your first prediction",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(
                                text = if (state.searchQuery.isNotBlank()) {
                                    "No predictions matching \"${state.searchQuery}\""
                                } else {
                                    when (state.filter) {
                                        FeedFilter.ACTIVE -> "No active predictions"
                                        FeedFilter.READY_TO_RESOLVE -> "Nothing to resolve"
                                        FeedFilter.RESOLVED -> "No resolved predictions yet"
                                    }
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    val listState = rememberLazyListState()

                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val totalItems = listState.layoutInfo.totalItemsCount
                            lastVisible >= totalItems - 3
                        }
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore && state.hasMore) {
                            onLoadMore()
                        }
                    }

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            state.questions,
                            key = { it.id },
                            contentType = { "question" },
                        ) { question ->
                            QuestionCard(
                                question = question,
                                onClick = onQuestionClick,
                            )
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

    // Detail bottom sheet
    state.detail.question?.let { question ->
        QuestionDetailSheet(
            question = question,
            detailState = state.detail,
            onForecastSliderChange = onForecastSliderChange,
            onUpdateForecast = onUpdateForecast,
            onResolve = onResolve,
            onToggleOptionExpanded = onToggleOptionExpanded,
            onOptionSliderChange = onOptionSliderChange,
            onUpdateOptionForecast = onUpdateOptionForecast,
            onResolveMcExclusive = onResolveMcExclusive,
            onResolveMcOption = onResolveMcOption,
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
            onToggleSharedPublicly = onToggleSharedPublicly,
            onDismiss = onDismissDetailSheet,
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
                                FeedSort.RESOLVE_BY -> "By resolve date"
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
 * A single chip that opens a dropdown of the user's tags — tags are unbounded,
 * so a chip-per-tag row would crowd out the main filters.
 */
@Composable
private fun TagFilterChip(
    availableTags: List<String>,
    selectedTag: String?,
    onTagSelected: (String?) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selectedTag != null,
            onClick = { menuExpanded = true },
            label = { Text(selectedTag ?: "Tags") },
            trailingIcon = {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
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
                    text = { Text(tag) },
                    onClick = {
                        onTagSelected(tag)
                        menuExpanded = false
                    },
                )
            }
        }
    }
}
