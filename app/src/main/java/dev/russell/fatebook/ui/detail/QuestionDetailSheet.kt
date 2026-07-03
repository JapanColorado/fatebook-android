package dev.russell.fatebook.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.domain.model.Comment
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.QuestionType
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.ui.components.DatePickerField
import dev.russell.fatebook.ui.components.ProbabilitySlider
import dev.russell.fatebook.ui.components.TagChipRow
import dev.russell.fatebook.ui.feed.DetailSheetState
import dev.russell.fatebook.ui.theme.ResolveAmbiguous
import dev.russell.fatebook.ui.theme.ResolveNo
import dev.russell.fatebook.ui.theme.ResolveYes
import dev.russell.fatebook.ui.theme.forecastColor
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionDetailSheet(
    question: Question,
    detailState: DetailSheetState,
    onForecastSliderChange: (Float) -> Unit,
    onUpdateForecast: () -> Unit,
    onResolve: (Resolution) -> Unit,
    onToggleOptionExpanded: (String) -> Unit = {},
    onOptionSliderChange: (Float) -> Unit = {},
    onUpdateOptionForecast: () -> Unit = {},
    onResolveMcExclusive: (String) -> Unit = {},
    onResolveMcOption: (optionId: String, resolvedYes: Boolean) -> Unit = { _, _ -> },
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
    onToggleSharedPublicly: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
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
                    dateFormatter = dateFormatter,
                    onForecastSliderChange = onForecastSliderChange,
                    onUpdateForecast = onUpdateForecast,
                    onResolve = onResolve,
                    onToggleOptionExpanded = onToggleOptionExpanded,
                    onOptionSliderChange = onOptionSliderChange,
                    onUpdateOptionForecast = onUpdateOptionForecast,
                    onResolveMcExclusive = onResolveMcExclusive,
                    onResolveMcOption = onResolveMcOption,
                    onEnterEditMode = onEnterEditMode,
                    onDeleteClick = onDeleteClick,
                    onToggleSharedPublicly = onToggleSharedPublicly,
                    onCommentTextChange = onCommentTextChange,
                    onAddComment = onAddComment,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
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

@Composable
private fun ColumnScope.ReadModeContent(
    question: Question,
    detailState: DetailSheetState,
    dateFormatter: DateTimeFormatter,
    onForecastSliderChange: (Float) -> Unit,
    onUpdateForecast: () -> Unit,
    onResolve: (Resolution) -> Unit,
    onToggleOptionExpanded: (String) -> Unit,
    onOptionSliderChange: (Float) -> Unit,
    onUpdateOptionForecast: () -> Unit,
    onResolveMcExclusive: (String) -> Unit,
    onResolveMcOption: (optionId: String, resolvedYes: Boolean) -> Unit,
    onEnterEditMode: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggleSharedPublicly: () -> Unit,
    onCommentTextChange: (String) -> Unit,
    onAddComment: () -> Unit,
) {
    val context = LocalContext.current

    // Title
    Text(
        text = question.title,
        style = MaterialTheme.typography.titleLarge,
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Resolve-by date
    Text(
        text = "${question.resolvesLabel}: ${question.resolveByDate.format(dateFormatter)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Tags (read-only; the public API only supports tags at creation)
    if (question.tags.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        TagChipRow(tags = question.tags)
    }

    // Notes
    if (!question.notes.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = question.notes,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // Forecast
    if (question.isForecastHidden) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Forecast hidden until ${
                question.forecastHiddenUntil!!
                    .atZone(java.time.ZoneOffset.UTC)
                    .toLocalDate()
                    .format(dateFormatter)
            }",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        question.forecastPercent?.let { pct ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your forecast: $pct%",
                color = forecastColor(pct / 100.0),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        }
    }

    // Resolution (if resolved)
    if (question.resolved && question.resolution != null) {
        Spacer(modifier = Modifier.height(8.dp))
        val (label, color) = if (question.type == QuestionType.MULTIPLE_CHOICE) {
            val winner = question.options.firstOrNull { it.resolution == Resolution.YES }
            when {
                question.resolution == Resolution.AMBIGUOUS ->
                    "Resolved: Ambiguous" to ResolveAmbiguous
                winner != null -> "Resolved: ${winner.text}" to ResolveYes
                else -> "Resolved: Other" to ResolveNo
            }
        } else {
            when (question.resolution.apiValue) {
                "YES" -> "Resolved: YES" to ResolveYes
                "NO" -> "Resolved: NO" to ResolveNo
                else -> "Resolved: Ambiguous" to ResolveAmbiguous
            }
        }
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
    }

    if (question.type == QuestionType.MULTIPLE_CHOICE) {
        Spacer(modifier = Modifier.height(16.dp))
        MultipleChoiceSection(
            question = question,
            detailState = detailState,
            onToggleOptionExpanded = onToggleOptionExpanded,
            onOptionSliderChange = onOptionSliderChange,
            onUpdateOptionForecast = onUpdateOptionForecast,
            onResolveMcExclusive = onResolveMcExclusive,
            onResolveMcOption = onResolveMcOption,
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
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { onResolve(Resolution.YES) },
                modifier = Modifier.weight(1f),
                enabled = !detailState.isResolving,
                colors = ButtonDefaults.buttonColors(containerColor = ResolveYes),
            ) {
                Text("YES", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { onResolve(Resolution.NO) },
                modifier = Modifier.weight(1f),
                enabled = !detailState.isResolving,
                colors = ButtonDefaults.buttonColors(containerColor = ResolveNo),
            ) {
                Text("NO", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { onResolve(Resolution.AMBIGUOUS) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !detailState.isResolving,
        ) {
            Text("Ambiguous")
        }

        if (detailState.isResolving) {
            Spacer(modifier = Modifier.height(12.dp))
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.CenterHorizontally),
            )
        }
    } else if (!question.resolved && !question.isForecastHidden) {
        Spacer(modifier = Modifier.height(16.dp))

        ProbabilitySlider(
            value = detailState.forecastSliderValue,
            onValueChange = onForecastSliderChange,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onUpdateForecast,
            modifier = Modifier.fillMaxWidth(),
            enabled = !detailState.isUpdatingForecast,
        ) {
            if (detailState.isUpdatingForecast) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Update Forecast")
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(12.dp))

    // Action row: Edit, Delete, Share, Visibility
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        if (!question.resolved && question.type != QuestionType.QUANTITY) {
            IconButton(onClick = onEnterEditMode) {
                Icon(Icons.Default.Edit, contentDescription = "Edit question")
            }
        }
        IconButton(onClick = onDeleteClick) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete question",
                tint = MaterialTheme.colorScheme.error,
            )
        }
        IconButton(onClick = {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, "${question.title}\n${question.url}")
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share prediction"))
        }) {
            Icon(Icons.Default.Share, contentDescription = "Share")
        }
        IconButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(question.url)))
        }) {
            Icon(Icons.Default.OpenInNew, contentDescription = "Open in Fatebook")
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(onClick = onToggleSharedPublicly) {
                Icon(
                    if (question.sharedPublicly) Icons.Default.Visibility
                    else Icons.Default.VisibilityOff,
                    contentDescription = if (question.sharedPublicly) "Make private" else "Make public",
                )
            }
            Text(
                text = if (question.sharedPublicly) "Public" else "Private",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(12.dp))

    // Comments section
    Text(
        text = "Comments",
        style = MaterialTheme.typography.titleSmall,
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (detailState.isLoadingComments) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(20.dp)
                .align(Alignment.CenterHorizontally),
            strokeWidth = 2.dp,
        )
    } else if (detailState.comments.isEmpty()) {
        Text(
            text = "No comments yet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        detailState.comments.forEach { comment ->
            CommentItem(comment = comment, dateFormatter = dateFormatter)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

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
            singleLine = true,
            enabled = !detailState.isAddingComment,
        )
        Spacer(modifier = Modifier.width(8.dp))
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

/**
 * Option list for multiple-choice questions.
 *
 * Active question: each unresolved option row is tappable and expands a
 * probability slider for forecasting that option (one at a time).
 * Ready to resolve: exclusive questions get one resolve button per option plus
 * Other/Ambiguous; non-exclusive questions get YES/NO buttons per option.
 */
@Composable
private fun MultipleChoiceSection(
    question: Question,
    detailState: DetailSheetState,
    onToggleOptionExpanded: (String) -> Unit,
    onOptionSliderChange: (Float) -> Unit,
    onUpdateOptionForecast: () -> Unit,
    onResolveMcExclusive: (String) -> Unit,
    onResolveMcOption: (optionId: String, resolvedYes: Boolean) -> Unit,
) {
    val canForecast = !question.resolved && !question.isForecastHidden
    val showResolveButtons = question.isReadyToResolve

    Text(
        text = "Options",
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(modifier = Modifier.height(4.dp))

    question.options.forEach { option ->
        val expanded = detailState.expandedOptionId == option.id
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (canForecast && !showResolveButtons && option.resolution == null) {
                        Modifier.clickable { onToggleOptionExpanded(option.id) }
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = option.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            when {
                option.resolution != null -> {
                    val (label, color) = when (option.resolution) {
                        Resolution.YES -> "YES" to ResolveYes
                        Resolution.NO -> "NO" to ResolveNo
                        Resolution.AMBIGUOUS -> "N/A" to ResolveAmbiguous
                    }
                    Text(
                        text = label,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
                showResolveButtons && !question.exclusiveAnswers -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { onResolveMcOption(option.id, true) },
                            enabled = !detailState.isResolving,
                        ) {
                            Text("YES", color = ResolveYes, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { onResolveMcOption(option.id, false) },
                            enabled = !detailState.isResolving,
                        ) {
                            Text("NO", color = ResolveNo, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                question.isForecastHidden -> {
                    Text(
                        text = "Hidden",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Text(
                        text = option.forecastPercent?.let { "$it%" } ?: "—",
                        color = option.latestForecast?.let { forecastColor(it) }
                            ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }
        }

        if (expanded && canForecast && !showResolveButtons) {
            ProbabilitySlider(
                value = detailState.optionSliderValue,
                onValueChange = onOptionSliderChange,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onUpdateOptionForecast,
                modifier = Modifier.fillMaxWidth(),
                enabled = !detailState.isUpdatingForecast,
            ) {
                if (detailState.isUpdatingForecast) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Update Forecast")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showResolveButtons && question.exclusiveAnswers) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Resolve to the correct answer:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        question.options.forEach { option ->
            Button(
                onClick = { onResolveMcExclusive(option.text) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !detailState.isResolving,
                colors = ButtonDefaults.buttonColors(containerColor = ResolveYes),
            ) {
                Text(option.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = { onResolveMcExclusive(QuestionRepository.MC_RESOLUTION_OTHER) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !detailState.isResolving,
        ) {
            Text("Other")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onResolveMcExclusive(QuestionRepository.MC_RESOLUTION_AMBIGUOUS) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !detailState.isResolving,
        ) {
            Text("Ambiguous")
        }
    } else if (showResolveButtons && !question.exclusiveAnswers) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onResolveMcExclusive(QuestionRepository.MC_RESOLUTION_AMBIGUOUS) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !detailState.isResolving,
        ) {
            Text("Resolve all Ambiguous")
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
        text = "Edit Question",
        style = MaterialTheme.typography.titleLarge,
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onCancelEdit,
            modifier = Modifier.weight(1f),
            enabled = !detailState.isSaving,
        ) {
            Text("Cancel")
        }
        Button(
            onClick = onSaveEdit,
            modifier = Modifier.weight(1f),
            enabled = !detailState.isSaving,
        ) {
            if (detailState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Save")
            }
        }
    }
}

@Composable
private fun CommentItem(comment: Comment, dateFormatter: DateTimeFormatter) {
    Column {
        Text(
            text = comment.comment,
            style = MaterialTheme.typography.bodyMedium,
        )
        val dateText = comment.createdAt
            .atZone(ZoneId.systemDefault())
            .format(dateFormatter)
        val metaText = if (comment.userName != null) {
            "${comment.userName} | $dateText"
        } else {
            dateText
        }
        Text(
            text = metaText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
