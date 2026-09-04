package dev.russell.fatebook.ui.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.russell.fatebook.domain.model.QuestionTemplate
import dev.russell.fatebook.ui.components.DatePickerField
import dev.russell.fatebook.ui.components.ProbabilitySlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(
    onBack: () -> Unit,
    viewModel: CreateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.success) {
        if (state.success) onBack()
    }

    CreateScreenContent(
        state = state,
        onBack = onBack,
        onTitleChanged = viewModel::setTitle,
        onResolveByChanged = viewModel::setResolveBy,
        onForecastChanged = viewModel::setForecast,
        onTagInputChanged = viewModel::setTagInput,
        onAddTag = viewModel::addTag,
        onRemoveTag = viewModel::removeTag,
        onSubmit = viewModel::submit,
        onApplyTemplate = viewModel::applyTemplate,
        onSaveAsTemplate = viewModel::saveCurrentAsTemplate,
        onDeleteTemplate = viewModel::deleteTemplate,
        onTemplateMessageShown = viewModel::templateMessageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun CreateScreenContent(
    state: CreateUiState,
    onBack: () -> Unit = {},
    onTitleChanged: (String) -> Unit = {},
    onResolveByChanged: (java.time.LocalDate) -> Unit = {},
    onForecastChanged: (Float) -> Unit = {},
    onTagInputChanged: (String) -> Unit = {},
    onAddTag: () -> Unit = {},
    onRemoveTag: (String) -> Unit = {},
    onSubmit: () -> Unit = {},
    onApplyTemplate: (QuestionTemplate) -> Unit = {},
    onSaveAsTemplate: () -> Unit = {},
    onDeleteTemplate: (Long) -> Unit = {},
    onTemplateMessageShown: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.templateMessage) {
        val message = state.templateMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onTemplateMessageShown()
    }
    var templateToDelete by remember { mutableStateOf<QuestionTemplate?>(null) }

    templateToDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { templateToDelete = null },
            title = { Text("Delete template?") },
            text = { Text(template.title) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTemplate(template.id)
                    templateToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { templateToDelete = null }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("New Prediction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSaveAsTemplate, enabled = state.title.isNotBlank()) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "Save as template")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.templates.isNotEmpty()) {
                TemplateRow(
                    templates = state.templates,
                    onApply = onApplyTemplate,
                    onLongPress = { templateToDelete = it },
                )
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChanged,
                label = { Text("What are you predicting?") },
                placeholder = { Text("Will X happen by Y?") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
            )

            DatePickerField(
                selectedDate = state.resolveBy,
                onDateSelected = onResolveByChanged,
                modifier = Modifier.fillMaxWidth(),
            )

            // Tags (optional, sent with the createQuestion call)
            OutlinedTextField(
                value = state.tagInput,
                onValueChange = onTagInputChanged,
                label = { Text("Add tag (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = onAddTag, enabled = state.tagInput.isNotBlank()) {
                        Icon(Icons.Default.Add, contentDescription = "Add tag")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAddTag() }),
            )

            if (state.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { onRemoveTag(tag) },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove tag $tag",
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            ProbabilitySlider(
                value = state.forecast,
                onValueChange = onForecastChanged,
            )

            Spacer(modifier = Modifier.weight(1f))

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting && state.title.isNotBlank(),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator()
                } else {
                    Text("Create Prediction")
                }
            }
        }
    }
}

/** Saved patterns as chips: tap fills the form, long-press offers delete. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TemplateRow(
    templates: List<QuestionTemplate>,
    onApply: (QuestionTemplate) -> Unit,
    onLongPress: (QuestionTemplate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Templates",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(templates, key = { it.id }) { template ->
                // A hand-rolled chip: Material chips own their click handling,
                // which would swallow the long-press used for delete.
                Surface(
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.combinedClickable(
                        onClick = { onApply(template) },
                        onLongClick = { onLongPress(template) },
                    ),
                ) {
                    Text(
                        text = template.title,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
