package dev.russell.fatebook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.russell.fatebook.ui.theme.Ledger

/** Standard horizontal page margin; ledger rows run edge to edge inside it. */
val LedgerMargin: Dp = 20.dp

/** Hairline between rows. */
@Composable
fun LedgerRule(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = Ledger.colors.rule)
}

/** The thick-and-thin double rule under a masthead, as on a newspaper front page. */
@Composable
fun MastheadRule(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier
            .fillMaxWidth()
            .height(5.dp),
    ) {
        val thick = 2.dp.toPx()
        drawRect(color, size = Size(size.width, thick))
        drawRect(
            color,
            topLeft = Offset(0f, size.height - 1.dp.toPx()),
            size = Size(size.width, 1.dp.toPx()),
        )
    }
}

/** Uppercase almanac label that introduces a section, optionally with a right-aligned aside. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    aside: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color)
        if (aside != null) {
            Text(
                aside.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A section of a detail page: a heavy top rule, an almanac label, then content.
 * The rule (not a card) is what groups things throughout the app.
 */
@Composable
fun LedgerSection(
    title: String,
    modifier: Modifier = Modifier,
    aside: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface)
        SectionLabel(
            text = title,
            aside = aside,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        content()
    }
}

/**
 * Horizontal probability bar with a tick at 50%. [fractions] draws stacked
 * segments for multiple choice; a single pair is a plain bar.
 */
@Composable
fun ProbabilityBar(
    fractions: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        drawRect(track)
        var x = 0f
        fractions.forEach { (fraction, color) ->
            val w = size.width * fraction.coerceIn(0f, 1f)
            drawRect(color, topLeft = Offset(x, 0f), size = Size(w, size.height))
            x += w
        }
        val tickWidth = 1.dp.toPx()
        drawRect(
            tick,
            topLeft = Offset(size.width / 2f - tickWidth / 2f, 0f),
            size = Size(tickWidth, size.height),
        )
    }
}

/** A rubber stamp recording an outcome. Tilted just enough to look hand-applied. */
@Composable
fun OutcomeStamp(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.rotate(-4f),
        color = Color.Transparent,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.5.dp, color),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 120.dp)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * Small mono text button with an outline: the app's secondary action. [color]
 * tints label and outline, e.g. the yes/no ends of the scale.
 */
@Composable
fun LedgerAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val shown = if (enabled) color else color.copy(alpha = 0.38f)
    Surface(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        color = if (selected) shown else Color.Transparent,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, if (selected) shown else shown.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.surface else shown,
                maxLines = 1,
            )
        }
    }
}

/** Fixed vertical gap between page sections. */
@Composable
fun SectionGap() {
    Spacer(Modifier.height(24.dp))
}
