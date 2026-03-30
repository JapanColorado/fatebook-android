package dev.russell.fatebook.ui.detail

import android.content.Intent
import android.net.Uri
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
import dev.russell.fatebook.domain.model.Comment
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.ui.components.DatePickerField
import dev.russell.fatebook.ui.components.ProbabilitySlider
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
        text = "Resolves: ${
            question.resolveBy
                .atZone(ZoneId.systemDefault())
                .format(dateFormatter)
        }",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

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
                    .atZone(ZoneId.systemDefault())
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
        val (label, color) = when (question.resolution.apiValue) {
            "YES" -> "Resolved: YES" to ResolveYes
            "NO" -> "Resolved: NO" to ResolveNo
            else -> "Resolved: Ambiguous" to ResolveAmbiguous
        }
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
    }

    // Update forecast (active, non-hidden questions only)
    if (!question.resolved && !question.isForecastHidden) {
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
        if (!question.resolved) {
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
        IconButton(onClick = onToggleSharedPublicly) {
            Icon(
                if (question.sharedPublicly) Icons.Default.Visibility
                else Icons.Default.VisibilityOff,
                contentDescription = if (question.sharedPublicly) "Make private" else "Make public",
            )
        }
    }

    // Visibility label
    Text(
        text = if (question.sharedPublicly) "Shared publicly" else "Private",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )

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
            CommentItem(comment = comment)
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

    Spacer(modifier = Modifier.height(16.dp))

    // Open in browser
    OutlinedButton(
        onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(question.url)))
        },
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) {
        Text("Open in Fatebook")
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
private fun CommentItem(comment: Comment) {
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    Column {
        Text(
            text = comment.comment,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = comment.createdAt
                .atZone(ZoneId.systemDefault())
                .format(dateFormatter),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
