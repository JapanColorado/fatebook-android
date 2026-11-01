package dev.russell.fatebook.ui.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.russell.fatebook.domain.model.QuestionTemplate
import dev.russell.fatebook.ui.components.DatePickerField
import dev.russell.fatebook.ui.components.LedgerAction
import dev.russell.fatebook.ui.components.LedgerMargin
import dev.russell.fatebook.ui.components.LedgerRule
import dev.russell.fatebook.ui.components.LedgerSection
import dev.russell.fatebook.ui.components.ProbabilitySlider
import dev.russell.fatebook.ui.components.SectionGap
import dev.russell.fatebook.ui.components.SectionLabel
import java.time.LocalDate
import java.time.Period
import kotlin.math.roundToInt

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
    today: LocalDate = LocalDate.now(),
    onBack: () -> Unit = {},
    onTitleChanged: (String) -> Unit = {},
    onResolveByChanged: (LocalDate) -> Unit = {},
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
                title = {
                    Text("New prediction".uppercase(), style = MaterialTheme.typography.labelMedium)
                },
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
        bottomBar = {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                LedgerRule()
                state.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = LedgerMargin, end = LedgerMargin, top = 12.dp),
                    )
                }
                Button(
                    onClick = onSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LedgerMargin, vertical = 12.dp)
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    enabled = !state.isSubmitting && state.title.isNotBlank(),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            // The button is disabled while submitting, so its fill is pale.
                            color = MaterialTheme.colorScheme.onSurface,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Enter in ledger at ${(state.forecast * 100).roundToInt()}%")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LedgerMargin),
        ) {
            if (state.templates.isNotEmpty()) {
                TemplateRow(
                    templates = state.templates,
                    onApply = onApplyTemplate,
                    onLongPress = { templateToDelete = it },
                )
                SectionGap()
            }

            // The question is the page's headline, so it's typed as one.
            // BasicTextField because TextField's built-in inset would pull the
            // headline off the page margin everything else is aligned to.
            BasicTextField(
                value = state.title,
                onValueChange = onTitleChanged,
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp)
                    .semantics { contentDescription = "What are you predicting?" },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                decorationBox = { innerTextField ->
                    if (state.title.isEmpty()) {
                        Text(
                            "Will X happen by Y?",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Normal,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )

            SectionGap()

            LedgerSection(title = "Your forecast") {
                ProbabilitySlider(
                    value = state.forecast,
                    onValueChange = onForecastChanged,
                )
            }

            SectionGap()

            LedgerSection(title = "Resolve by") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickDates(
                        selected = state.resolveBy,
                        today = today,
                        onSelected = onResolveByChanged,
                    )
                    DatePickerField(
                        selectedDate = state.resolveBy,
                        onDateSelected = onResolveByChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = "Date",
                    )
                }
            }

            SectionGap()

            // Tags (optional, sent with the createQuestion call)
            LedgerSection(title = "Tags", aside = "Optional") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.tagInput,
                        onValueChange = onTagInputChanged,
                        placeholder = { Text("Add a tag") },
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
                                    label = { Text("#$tag") },
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
                }
            }

            SectionGap()
        }
    }
}

private val QUICK_DATES = listOf(
    "Tomorrow" to Period.ofDays(1),
    "1 week" to Period.ofWeeks(1),
    "1 month" to Period.ofMonths(1),
    "3 months" to Period.ofMonths(3),
    "1 year" to Period.ofYears(1),
)

/** Most predictions fall on a round horizon: one tap, instead of a calendar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickDates(
    selected: LocalDate,
    today: LocalDate,
    onSelected: (LocalDate) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QUICK_DATES.forEach { (label, period) ->
            val date = today.plus(period)
            LedgerAction(
                text = label,
                onClick = { onSelected(date) },
                selected = date == selected,
            )
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
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel("Templates")
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
