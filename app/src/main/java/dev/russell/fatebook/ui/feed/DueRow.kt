package dev.russell.fatebook.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.QuestionType
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.ui.components.LedgerAction
import dev.russell.fatebook.ui.components.LedgerMargin
import dev.russell.fatebook.ui.components.QuestionCard
import dev.russell.fatebook.ui.theme.Ledger
import java.time.LocalDate

/**
 * A question that has come due. Binary questions: swipe right for YES, left for
 * NO. Multiple choice can't resolve in one gesture (which option?) so its swipe
 * only snoozes. Quantity is read-only through the public API and gets neither.
 * The buttons repeat each gesture, keeping them discoverable and accessible, and
 * both routes pass through the undo window in [FeedViewModel.quickAction].
 */
@Composable
fun DueRow(
    question: Question,
    today: LocalDate,
    onClick: (Question) -> Unit,
    onQuickAction: (Question, QuickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (question.type == QuestionType.QUANTITY) {
        QuestionCard(question, onClick, modifier, today)
        return
    }
    val binary = question.type == QuestionType.BINARY
    val haptics = LocalHapticFeedback.current
    val startToEnd = if (binary) QuickAction.Resolve(Resolution.YES) else null
    val endToStart = if (binary) QuickAction.Resolve(Resolution.NO) else QuickAction.SnoozeWeek

    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            val action = when (target) {
                SwipeToDismissBoxValue.StartToEnd -> startToEnd
                SwipeToDismissBoxValue.EndToStart -> endToStart
                SwipeToDismissBoxValue.Settled -> null
            }
            if (action != null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onQuickAction(question, action)
            }
            // Always snap back: the row is hidden by state, and has to return intact on undo.
            false
        },
    )

    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier,
        enableDismissFromStartToEnd = startToEnd != null,
        backgroundContent = {
            when (swipeState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd ->
                    SwipeBackdrop("Yes", Ledger.colors.yes, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> if (binary) {
                    SwipeBackdrop("No", Ledger.colors.no, Alignment.CenterEnd)
                } else {
                    SwipeBackdrop("+1 week", Ledger.colors.neutral, Alignment.CenterEnd)
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
    ) {
        QuestionCard(
            question = question,
            onClick = onClick,
            today = today,
            footer = { DueActions(question, binary, onClick, onQuickAction) },
        )
    }
}

@Composable
private fun SwipeBackdrop(label: String, color: Color, alignment: Alignment) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(horizontal = LedgerMargin),
        contentAlignment = alignment,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun DueActions(
    question: Question,
    binary: Boolean,
    onClick: (Question) -> Unit,
    onQuickAction: (Question, QuickAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (binary) {
            LedgerAction(
                "Yes",
                { onQuickAction(question, QuickAction.Resolve(Resolution.YES)) },
                color = Ledger.colors.yes,
            )
            LedgerAction(
                "No",
                { onQuickAction(question, QuickAction.Resolve(Resolution.NO)) },
                color = Ledger.colors.no,
            )
            LedgerAction(
                "N/A",
                { onQuickAction(question, QuickAction.Resolve(Resolution.AMBIGUOUS)) },
                color = Ledger.colors.neutral,
            )
        } else {
            LedgerAction("Resolve", { onClick(question) })
        }
        Spacer(Modifier.weight(1f))
        LedgerAction(
            "+1 wk",
            { onQuickAction(question, QuickAction.SnoozeWeek) },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Sits above the list during the undo window. */
@Composable
fun UndoStrip(
    pending: PendingQuickAction,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val verb = when (val action = pending.action) {
        is QuickAction.Resolve -> when (action.resolution) {
            Resolution.YES -> "Resolved YES"
            Resolution.NO -> "Resolved NO"
            Resolution.AMBIGUOUS -> "Resolved N/A"
        }
        QuickAction.SnoozeWeek -> "Pushed 1 week"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface)
            .padding(start = LedgerMargin, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Undo") {
                        onUndo()
                        true
                    },
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = verb.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        LedgerAction("Undo", onUndo, color = MaterialTheme.colorScheme.surface)
    }
}
