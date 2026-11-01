package dev.russell.fatebook.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.russell.fatebook.domain.model.Comment
import dev.russell.fatebook.domain.model.Forecast
import dev.russell.fatebook.domain.model.McResolution
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.QuestionOption
import dev.russell.fatebook.domain.model.QuestionType
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.domain.model.Visibility
import dev.russell.fatebook.ui.components.DatePickerField
import dev.russell.fatebook.ui.components.ForecastSparkline
import dev.russell.fatebook.ui.components.LedgerAction
import dev.russell.fatebook.ui.components.LedgerMargin
import dev.russell.fatebook.ui.components.LedgerRule
import dev.russell.fatebook.ui.components.LedgerSection
import dev.russell.fatebook.ui.components.OptionForecastMath
import dev.russell.fatebook.ui.components.OutcomeStamp
import dev.russell.fatebook.ui.components.ProbabilityBar
import dev.russell.fatebook.ui.components.ProbabilitySlider
import dev.russell.fatebook.ui.components.SectionGap
import dev.russell.fatebook.ui.components.SectionLabel
import dev.russell.fatebook.ui.components.SparklineMath
import dev.russell.fatebook.ui.components.TagChipRow
import dev.russell.fatebook.ui.feed.DetailSheetState
import dev.russell.fatebook.ui.theme.Ledger
import dev.russell.fatebook.ui.theme.ResolveAmbiguous
import dev.russell.fatebook.ui.theme.ResolveNo
import dev.russell.fatebook.ui.theme.ResolveYes
import dev.russell.fatebook.ui.theme.forecastColor
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Callbacks for multiple-choice forecasting and resolution, bundled so a new
 * MC action doesn't ripple through every composable signature on the way to
 * [MultipleChoiceSection].
 */
@Immutable
data class McActions(
    val onOptionDraftChange: (optionId: String, percent: Int) -> Unit = { _, _ -> },
    val onBalanceOptionDrafts: () -> Unit = {},
    val onUpdateOptionForecasts: () -> Unit = {},
    val onResolveExclusive: (String) -> Unit = {},
    val onResolveOption: (optionId: String, resolvedYes: Boolean) -> Unit = { _, _ -> },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionDetailSheet(
    question: Question,
    detailState: DetailSheetState,
    onForecastSliderChange: (Float) -> Unit,
    onUpdateForecast: () -> Unit,
    onResolve: (Resolution) -> Unit,
    mcActions: McActions = McActions(),
    onPushResolveBy: (Period) -> Unit = {},
    onEnterEditMode: () -> Unit,
    onEditTitleChange: (String) -> Unit,
    onEditResolveByChange: (LocalDate) -> Unit,
    onEditNotesChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDeleteClick: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDeleteConfirmation: () -> Unit,
    onCommentTextChange: (String) -> Unit,
    onAddComment: () -> Unit,
    onVisibilityChange: (Visibility) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        QuestionDetailContent(
            question = question,
            detailState = detailState,
            modifier = Modifier.verticalScroll(rememberScrollState()),
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
            onCommentTextChange = onCommentTextChange,
            onAddComment = onAddComment,
            onVisibilityChange = onVisibilityChange,
        )
    }

    // Delete confirmation dialog
    if (detailState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDeleteConfirmation,
            title = { Text("Delete question?") },
            text = { Text("This will permanently delete \"${question.title}\". This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    if (detailState.isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteConfirmation) {
                    Text("Cancel")
                }
            },
        )
    }
}

/** The sheet's body, separate from the sheet window so Paparazzi can render it directly. */
@Composable
internal fun QuestionDetailContent(
    question: Question,
    detailState: DetailSheetState,
    modifier: Modifier = Modifier,
    onForecastSliderChange: (Float) -> Unit = {},
    onUpdateForecast: () -> Unit = {},
    onResolve: (Resolution) -> Unit = {},
    mcActions: McActions = McActions(),
    onPushResolveBy: (Period) -> Unit = {},
    onEnterEditMode: () -> Unit = {},
    onEditTitleChange: (String) -> Unit = {},
    onEditResolveByChange: (LocalDate) -> Unit = {},
    onEditNotesChange: (String) -> Unit = {},
    onSaveEdit: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onCommentTextChange: (String) -> Unit = {},
    onAddComment: () -> Unit = {},
    onVisibilityChange: (Visibility) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = LedgerMargin, end = LedgerMargin, top = 4.dp, bottom = 32.dp),
    ) {
        if (detailState.isEditing) {
            EditModeContent(
                detailState = detailState,
                onEditTitleChange = onEditTitleChange,
                onEditResolveByChange = onEditResolveByChange,
                onEditNotesChange = onEditNotesChange,
                onSaveEdit = onSaveEdit,
                onCancelEdit = onCancelEdit,
            )
        } else {
            ReadModeContent(
                question = question,
                detailState = detailState,
                onForecastSliderChange = onForecastSliderChange,
                onUpdateForecast = onUpdateForecast,
                onResolve = onResolve,
                mcActions = mcActions,
                onPushResolveBy = onPushResolveBy,
                onEnterEditMode = onEnterEditMode,
                onDeleteClick = onDeleteClick,
                onVisibilityChange = onVisibilityChange,
                onCommentTextChange = onCommentTextChange,
                onAddComment = onAddComment,
            )
        }
    }
}

@Composable
private fun ColumnScope.ReadModeContent(
    question: Question,
    detailState: DetailSheetState,
    onForecastSliderChange: (Float) -> Unit,
    onUpdateForecast: () -> Unit,
    onResolve: (Resolution) -> Unit,
    mcActions: McActions,
    onPushResolveBy: (Period) -> Unit,
    onEnterEditMode: () -> Unit,
    onDeleteClick: () -> Unit,
    onVisibilityChange: (Visibility) -> Unit,
    onCommentTextChange: (String) -> Unit,
    onAddComment: () -> Unit,
) {
    val dateFormatter = monthDayYearFormatter

    // Date line, then the question as the sheet's headline.
    Text(
        text = "${question.resolvesLabel} ${question.resolvedOnDate.format(dateFormatter)}".uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (question.isReadyToResolve) {
            Ledger.colors.accent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = question.title,
        style = MaterialTheme.typography.headlineSmall,
    )

    // Tags (read-only; the public API only supports tags at creation)
    if (question.tags.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        TagChipRow(tags = question.tags)
    }

    // Notes
    if (!question.notes.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = question.notes,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // The forecast slider has its own large readout, so the static figure only
    // appears when the forecast can't be changed here.
    val showSlider = question.type == QuestionType.BINARY &&
        !question.isReadyToResolve && !question.resolved && !question.isForecastHidden

    if (!showSlider) {
        ForecastFigure(question)
    }

    if (question.type == QuestionType.MULTIPLE_CHOICE) {
        SectionGap()
        MultipleChoiceSection(
            question = question,
            detailState = detailState,
            actions = mcActions,
        )
        if (detailState.isResolving) {
            Spacer(modifier = Modifier.height(12.dp))
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.CenterHorizontally),
            )
        }
    } else if (question.type == QuestionType.QUANTITY) {
        if (!question.resolved) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Quantity questions can be forecast and resolved on fatebook.io",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else if (question.isReadyToResolve) {
        SectionGap()
        LedgerSection(title = "Resolve") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ResolveButton(
                        label = "Yes",
                        color = ResolveYes,
                        enabled = !detailState.isResolving,
                        onClick = { onResolve(Resolution.YES) },
                        modifier = Modifier.weight(1f),
                    )
                    ResolveButton(
                        label = "No",
                        color = ResolveNo,
                        enabled = !detailState.isResolving,
                        onClick = { onResolve(Resolution.NO) },
                        modifier = Modifier.weight(1f),
                    )
                }
                LedgerAction(
                    text = "Ambiguous",
                    onClick = { onResolve(Resolution.AMBIGUOUS) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !detailState.isResolving,
                )
            }
        }

        if (detailState.isResolving) {
            Spacer(modifier = Modifier.height(12.dp))
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.CenterHorizontally),
            )
        }
    } else if (showSlider) {
        SectionGap()
        LedgerSection(
            title = "Update forecast",
            aside = question.forecastPercent?.let { "Currently $it%" },
        ) {
            Column {
                ProbabilitySlider(
                    value = detailState.forecastSliderValue,
                    onValueChange = onForecastSliderChange,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SubmittingButton(
                    label = "Update Forecast",
                    submitting = detailState.isUpdatingForecast,
                    onClick = onUpdateForecast,
                )
            }
        }
    }

    // Quick action for overdue questions: not ready to call it yet? Push the
    // deadline instead of resolving.
    if (question.isReadyToResolve && question.type != QuestionType.QUANTITY) {
        SectionGap()
        LedgerSection(title = "Not ready? Push the date") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LedgerAction(
                    text = "+1 week",
                    onClick = { onPushResolveBy(Period.ofWeeks(1)) },
                    modifier = Modifier.weight(1f),
                    enabled = !detailState.isSaving,
                )
                LedgerAction(
                    text = "+1 month",
                    onClick = { onPushResolveBy(Period.ofMonths(1)) },
                    modifier = Modifier.weight(1f),
                    enabled = !detailState.isSaving,
                )
            }
        }
    }

    SectionGap()
    ActionRow(
        question = question,
        onEnterEditMode = onEnterEditMode,
        onDeleteClick = onDeleteClick,
    )

    // Forecast history — only interesting when someone besides you has
    // forecast (shared questions). Server-scrubbed hidden forecasts arrive
    // with a null value and are skipped. Remembered because slider drags
    // recompose this content every frame.
    val visibleHistory = remember(detailState.forecastHistory) {
        detailState.forecastHistory.filter { it.forecast != null }
    }
    val hasSharedHistory = remember(visibleHistory) {
        visibleHistory.map { it.userId }.distinct().size > 1
    }

    // Your own trajectory, whether or not anyone else has forecast.
    val yourForecasts = remember(detailState.forecastHistory, detailState.currentUserId) {
        SparklineMath.yourForecasts(detailState.forecastHistory, detailState.currentUserId)
    }
    if (yourForecasts.size >= 2) {
        SectionGap()
        val points = remember(yourForecasts, question.createdAt, question.resolvedAt) {
            SparklineMath.normalize(
                samples = yourForecasts.map { it.createdAt to (it.forecast ?: 0.0) },
                start = question.createdAt,
                end = question.resolvedAt ?: Instant.now(),
            )
        }
        val first = ((yourForecasts.first().forecast ?: 0.0) * 100).roundToInt()
        val last = ((yourForecasts.last().forecast ?: 0.0) * 100).roundToInt()
        LedgerSection(title = "Your forecasts", aside = "$first% → $last%") {
            Column {
                ForecastSparkline(
                    points = points,
                    contentDescription = "Your forecasts: ${yourForecasts.size} updates, from $first% to $last%",
                )
                Spacer(modifier = Modifier.height(8.dp))
                yourForecasts.forEach { forecast ->
                    LedgerRule()
                    ForecastHistoryRow(
                        forecast = forecast,
                        optionText = null,
                        dateFormatter = dateFormatter,
                        nameOverride = "You",
                    )
                }
            }
        }
    }

    if (hasSharedHistory) {
        SectionGap()
        LedgerSection(title = "Forecast history") {
            Column {
                val optionTextById = remember(question.options) {
                    question.options.associate { it.id to it.text }
                }
                visibleHistory.forEachIndexed { i, forecast ->
                    if (i > 0) LedgerRule()
                    ForecastHistoryRow(
                        forecast = forecast,
                        optionText = forecast.optionId?.let { optionTextById[it] },
                        dateFormatter = dateFormatter,
                    )
                }
            }
        }
    }

    SectionGap()
    LedgerSection(title = "Comments") {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (detailState.isLoadingComments) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else if (detailState.comments.isEmpty()) {
                Text(
                    text = "No comments yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                detailState.comments.forEachIndexed { i, comment ->
                    if (i > 0) LedgerRule()
                    CommentItem(comment = comment, dateFormatter = dateFormatter)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Add comment
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = detailState.commentText,
                    onValueChange = onCommentTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add a comment...") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    enabled = !detailState.isAddingComment,
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onAddComment,
                    enabled = detailState.commentText.isNotBlank() && !detailState.isAddingComment,
                ) {
                    if (detailState.isAddingComment) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send comment")
                    }
                }
            }
        }
    }

    SectionGap()
    VisibilitySection(
        current = question.visibility,
        onVisibilityChange = onVisibilityChange,
    )
}

/** What you said and, once it's known, what happened. */
@Composable
private fun ForecastFigure(question: Question) {
    val hidden = question.isForecastHidden
    val pct = question.forecastPercent.takeUnless { hidden }
    val outcome = if (question.resolved && question.resolution != null) outcomeOf(question) else null
    if (!hidden && pct == null && outcome == null) return

    Spacer(modifier = Modifier.height(20.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (hidden) {
                Text(
                    text = "Forecast hidden until ${
                        question.forecastHiddenUntil!!
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .format(monthDayYearFormatter)
                    }",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (pct != null) {
                SectionLabel("Your forecast")
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.displayMedium,
                    color = forecastColor(pct / 100.0),
                )
            }
        }
        if (outcome != null) {
            OutcomeStamp(
                text = outcome.stamp,
                color = outcome.color,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .clearAndSetSemantics { contentDescription = "Resolved: ${outcome.stamp}" },
            )
        }
    }
    if (pct != null) {
        Spacer(modifier = Modifier.height(8.dp))
        // Once resolved the bar is history; the stamp is the headline.
        ProbabilityBar(
            fractions = listOf(pct / 100f to forecastColor(pct / 100.0)),
            modifier = Modifier.alpha(if (question.resolved) 0.45f else 1f),
        )
    }
}

private data class Outcome(val stamp: String, val color: Color)

@Composable
private fun outcomeOf(question: Question): Outcome =
    if (question.type == QuestionType.MULTIPLE_CHOICE) {
        val winner = question.winningOption
        when {
            question.resolution == Resolution.AMBIGUOUS -> Outcome("Ambiguous", ResolveAmbiguous)
            winner != null -> Outcome(winner.text, ResolveYes)
            else -> Outcome("Other", ResolveNo)
        }
    } else {
        when (question.resolution) {
            Resolution.YES -> Outcome("Yes", ResolveYes)
            Resolution.NO -> Outcome("No", ResolveNo)
            else -> Outcome("Ambiguous", ResolveAmbiguous)
        }
    }

/** Solid resolve button, filled with its end of the No↔Yes scale. */
@Composable
private fun ResolveButton(
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Edit, Share and Open sit on the left; Delete stands apart on the right. */
@Composable
private fun ActionRow(
    question: Question,
    onEnterEditMode: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val context = LocalContext.current
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    LedgerRule()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Offset so the icon glyphs, not their touch targets, line up with the margin.
        Row(modifier = Modifier.offset(x = (-12).dp)) {
            if (!question.resolved && question.type != QuestionType.QUANTITY) {
                IconButton(onClick = onEnterEditMode) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit question", tint = tint)
                }
            }
            IconButton(onClick = {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, "${question.title}\n${question.url}")
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share prediction"))
            }) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = tint)
            }
            IconButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(question.url)))
            }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in Fatebook", tint = tint)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onDeleteClick, modifier = Modifier.offset(x = 12.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete question",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
    LedgerRule()
}

/**
 * Option list for multiple-choice questions.
 *
 * Open for forecasting: [OptionForecastEditor], with one button that submits
 * every changed option; an exclusive question over 100% must be fixed first.
 * Ready to resolve: exclusive questions get one resolve button per option plus
 * Other/Ambiguous; non-exclusive questions get YES/NO buttons per option.
 */
@Composable
private fun MultipleChoiceSection(
    question: Question,
    detailState: DetailSheetState,
    actions: McActions,
) {
    LedgerSection(title = "Options") {
        Column {
            if (question.hasOptionForecastEditor) {
                val drafts = detailState.optionDrafts
                OptionForecastEditor(
                    options = question.options,
                    drafts = drafts,
                    exclusive = question.exclusiveAnswers,
                    onDraftChange = actions.onOptionDraftChange,
                    onBalance = actions.onBalanceOptionDrafts,
                )
                Spacer(modifier = Modifier.height(12.dp))
                val withinTotal = !question.exclusiveAnswers || OptionForecastMath.overBy(drafts) == 0
                SubmittingButton(
                    label = "Update Forecasts",
                    submitting = detailState.isUpdatingForecast,
                    enabled = withinTotal && OptionForecastMath.changes(question.options, drafts).isNotEmpty(),
                    onClick = actions.onUpdateOptionForecasts,
                )
            } else {
                OptionRows(question, detailState, actions)
            }
        }
    }

    val showResolveButtons = question.isReadyToResolve
    if (showResolveButtons && question.exclusiveAnswers) {
        SectionGap()
        LedgerSection(title = "Resolve to the correct answer") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options.forEach { option ->
                    ResolveButton(
                        label = option.text,
                        color = ResolveYes,
                        enabled = !detailState.isResolving,
                        onClick = { actions.onResolveExclusive(option.text) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LedgerAction(
                        text = "Other",
                        onClick = { actions.onResolveExclusive(McResolution.OTHER) },
                        modifier = Modifier.weight(1f),
                        enabled = !detailState.isResolving,
                    )
                    LedgerAction(
                        text = "Ambiguous",
                        onClick = { actions.onResolveExclusive(McResolution.AMBIGUOUS) },
                        modifier = Modifier.weight(1f),
                        enabled = !detailState.isResolving,
                    )
                }
            }
        }
    } else if (showResolveButtons && !question.exclusiveAnswers) {
        Spacer(modifier = Modifier.height(12.dp))
        LedgerAction(
            text = "Resolve all Ambiguous",
            onClick = { actions.onResolveExclusive(McResolution.AMBIGUOUS) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !detailState.isResolving,
        )
    }
}

@Composable
private fun OptionRows(
    question: Question,
    detailState: DetailSheetState,
    actions: McActions,
) {
    val showResolveButtons = question.isReadyToResolve

    question.options.forEachIndexed { i, option ->
        if (i > 0) LedgerRule()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = option.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                OptionTrailing(question, option, detailState, actions, showResolveButtons)
            }
            val forecast = option.latestForecast
            if (forecast != null && !question.isForecastHidden) {
                ProbabilityBar(
                    fractions = listOf(forecast.toFloat() to forecastColor(forecast)),
                    height = 3.dp,
                )
            }
        }
    }
}

/** An option row's right-hand side: outcome, per-option resolve buttons, or its figure. */
@Composable
private fun OptionTrailing(
    question: Question,
    option: QuestionOption,
    detailState: DetailSheetState,
    actions: McActions,
    showResolveButtons: Boolean,
) {
    when {
        option.resolution != null -> OptionOutcome(option.resolution)
        showResolveButtons && !question.exclusiveAnswers -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LedgerAction(
                    text = "Yes",
                    onClick = { actions.onResolveOption(option.id, true) },
                    color = ResolveYes,
                    enabled = !detailState.isResolving,
                )
                LedgerAction(
                    text = "No",
                    onClick = { actions.onResolveOption(option.id, false) },
                    color = ResolveNo,
                    enabled = !detailState.isResolving,
                )
            }
        }
        question.isForecastHidden -> {
            Text(
                text = "HIDDEN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> {
            Text(
                text = option.forecastPercent?.let { "$it%" } ?: "—",
                style = monoFigure(),
                color = option.latestForecast?.let { forecastColor(it) }
                    ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** How one option of a question resolved. */
@Composable
internal fun OptionOutcome(resolution: Resolution) {
    val (label, color) = when (resolution) {
        Resolution.YES -> "YES" to ResolveYes
        Resolution.NO -> "NO" to ResolveNo
        Resolution.AMBIGUOUS -> "N/A" to ResolveAmbiguous
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

/** Full-width submit button that swaps its label for a spinner while busy. */
@Composable
private fun SubmittingButton(
    label: String,
    submitting: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled && !submitting,
        shape = MaterialTheme.shapes.medium,
    ) {
        if (submitting) {
            // onSurface: this button is disabled while spinning, so its fill is pale.
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onSurface,
                strokeWidth = 2.dp,
            )
        } else {
            Text(label)
        }
    }
}

@Composable
private fun EditModeContent(
    detailState: DetailSheetState,
    onEditTitleChange: (String) -> Unit,
    onEditResolveByChange: (LocalDate) -> Unit,
    onEditNotesChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    Text(
        text = "Edit question",
        style = MaterialTheme.typography.headlineSmall,
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = detailState.editTitle,
        onValueChange = onEditTitleChange,
        label = { Text("Title") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !detailState.isSaving,
    )

    Spacer(modifier = Modifier.height(12.dp))

    detailState.editResolveBy?.let { date ->
        DatePickerField(
            selectedDate = date,
            onDateSelected = onEditResolveByChange,
            modifier = Modifier.fillMaxWidth(),
            label = "Resolve by",
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = detailState.editNotes,
        onValueChange = onEditNotesChange,
        label = { Text("Notes") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 6,
        enabled = !detailState.isSaving,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onCancelEdit,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            enabled = !detailState.isSaving,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Text("Cancel")
        }
        Button(
            onClick = onSaveEdit,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            enabled = !detailState.isSaving,
            shape = MaterialTheme.shapes.medium,
        ) {
            if (detailState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Save")
            }
        }
    }
}

@Composable
private fun ForecastHistoryRow(
    forecast: Forecast,
    optionText: String?,
    dateFormatter: DateTimeFormatter,
    nameOverride: String? = null,
) {
    val pct = ((forecast.forecast ?: 0.0) * 100).roundToInt()
    val name = nameOverride ?: forecast.userName ?: "Unknown"
    val dateText = forecast.createdAt
        .atZone(ZoneId.systemDefault())
        .format(dateFormatter)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (optionText != null) "$name · $optionText" else name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = dateText.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        // Right-aligned monospaced figures, so the column reads straight down.
        Text(
            text = "$pct%".padStart(4),
            style = monoFigure(),
            color = forecastColor(forecast.forecast ?: 0.0),
        )
    }
}

private fun Visibility.label(): String = when (this) {
    Visibility.PRIVATE -> "Private"
    Visibility.UNLISTED -> "Unlisted"
    Visibility.PUBLIC -> "Public"
}

private fun Visibility.description(): String = when (this) {
    Visibility.PRIVATE -> "Only you"
    Visibility.UNLISTED -> "Anyone with the link"
    Visibility.PUBLIC -> "Listed on fatebook.io"
}

/** Three-way segmented control, with a line saying what the current choice means. */
@Composable
private fun VisibilitySection(
    current: Visibility,
    onVisibilityChange: (Visibility) -> Unit,
) {
    LedgerSection(title = "Visibility") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val shape = MaterialTheme.shapes.small
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    .selectableGroup(),
            ) {
                Visibility.entries.forEach { option ->
                    val selected = option == current
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .then(
                                if (selected) {
                                    Modifier.background(MaterialTheme.colorScheme.onSurface, shape)
                                } else {
                                    Modifier
                                },
                            )
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = {
                                    if (!selected) onVisibilityChange(option)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option.label().uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            Text(
                text = current.description(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CommentItem(comment: Comment, dateFormatter: DateTimeFormatter) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = comment.comment,
            style = MaterialTheme.typography.bodyMedium,
        )
        val dateText = comment.createdAt
            .atZone(ZoneId.systemDefault())
            .format(dateFormatter)
        val metaText = if (comment.userName != null) {
            "${comment.userName} · $dateText"
        } else {
            dateText
        }
        Text(
            text = metaText.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Figures in rows: the mono label face at reading size, without label tracking. */
@Composable
internal fun monoFigure() = MaterialTheme.typography.labelMedium.copy(
    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
    letterSpacing = MaterialTheme.typography.bodyMedium.letterSpacing,
)

private val monthDayYearFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
