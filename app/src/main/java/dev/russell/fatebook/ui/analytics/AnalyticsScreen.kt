package dev.russell.fatebook.ui.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.russell.fatebook.ui.components.LedgerMargin
import dev.russell.fatebook.ui.components.LedgerRule
import dev.russell.fatebook.ui.components.LedgerSection
import dev.russell.fatebook.ui.components.MastheadRule
import dev.russell.fatebook.ui.components.SectionLabel
import dev.russell.fatebook.ui.theme.Ledger
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AnalyticsScreenContent(
        state = state,
        onSyncFullHistory = viewModel::syncFullHistory,
    )
}

@Composable
fun AnalyticsScreenContent(
    state: AnalyticsUiState,
    onSyncFullHistory: () -> Unit = {},
) {
    Scaffold { padding ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Masthead()
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                Masthead()
                Column(
                    modifier = Modifier.padding(
                        start = LedgerMargin,
                        end = LedgerMargin,
                        top = 16.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    Column {
                        BrierHero(brierScore = state.brierScore)
                        Spacer(Modifier.height(20.dp))
                        StatLine(
                            totalForecasts = state.totalForecasts,
                            currentStreak = state.currentStreak,
                        )
                    }

                    val hasCalibration = state.calibrationBuckets.isNotEmpty()
                    LedgerSection(
                        title = "Calibration",
                        aside = if (hasCalibration) "Tap a bar" else null,
                    ) {
                        CalibrationChart(buckets = state.calibrationBuckets)
                        Text(
                            // "Solid", not "dark": bars are ink, which is light in dark mode.
                            text = if (hasCalibration) {
                                "Bars show how often you were right; solid bars rest on more " +
                                    "forecasts, faint ones on few. On the dashed line, you're " +
                                    "perfectly calibrated."
                            } else {
                                "Fills in as your predictions resolve."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    if (state.monthlyBrier.isNotEmpty()) {
                        LedgerSection(title = "Score over time", aside = "Lower is better") {
                            ScoreOverTimeChart(monthly = state.monthlyBrier)
                        }
                    }

                    LedgerSection(title = "Activity", aside = "11 weeks") {
                        ActivityHeatmap(dailyActivity = state.dailyActivity)
                    }

                    if (state.tagBreakdown.isNotEmpty()) {
                        LedgerSection(title = "Brier by tag", aside = "Best first") {
                            TagBreakdownList(entries = state.tagBreakdown)
                        }
                    }

                    HistorySyncRow(
                        syncState = state.historySync,
                        onSyncClick = onSyncFullHistory,
                    )
                }
            }
        }
    }
}

@Composable
private fun Masthead() {
    Column(
        modifier = Modifier
            .padding(horizontal = LedgerMargin)
            .padding(top = 8.dp),
    ) {
        // Same height as the feed's icon row, so the rule doesn't jump between tabs.
        Box(
            modifier = Modifier.height(48.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "Track record",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        MastheadRule(Modifier.padding(top = 2.dp))
    }
}

/** The one number this screen exists for, with what it means in words. */
@Composable
private fun BrierHero(brierScore: Double?) {
    var showHelp by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "BRIER SCORE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                IconButton(onClick = { showHelp = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "What is Brier Score?",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = showHelp,
                    onDismissRequest = { showHelp = false },
                ) {
                    Text(
                        text = "Lower is better. Perfect = 0.0, " +
                            "always-50% = 0.5 (matches fatebook.io).",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Text(
            text = brierScore?.let { "%.2f".format(it) } ?: "--",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = brierGloss(brierScore),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Plain-language reading of a score, anchored on the one reference point a user
 * can picture: a forecaster who says 50% to everything scores 0.5.
 */
private fun brierGloss(score: Double?): String = when {
    score == null -> "Resolve a prediction to earn a score"
    abs(score - 0.5) < 0.02 -> "Level with always saying 50%"
    score > 0.5 -> "Behind always saying 50%"
    score < 0.15 -> "Far ahead of always saying 50%"
    else -> "Ahead of always saying 50%"
}

/** Secondary figures, ruled off like the stat line in a newspaper box score. */
@Composable
private fun StatLine(totalForecasts: Int, currentStreak: Int) {
    Column {
        LedgerRule()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            RuledFigure(
                label = "Forecasts",
                value = totalForecasts.toString(),
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                thickness = 1.dp,
                color = Ledger.colors.rule,
            )
            RuledFigure(
                label = "Streak",
                value = currentStreak.toString(),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
        LedgerRule()
    }
}

@Composable
private fun RuledFigure(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            leadingIcon?.invoke()
            Text(
                text = label.uppercase(Locale.US),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Status row for the full-history pull. Analytics only sees what's in the
 * Room cache, so until this completes the stats cover recent pages only.
 */
@Composable
private fun HistorySyncRow(
    syncState: HistorySyncState,
    onSyncClick: () -> Unit,
) {
    when {
        syncState.isSyncing -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Syncing full history… ${syncState.syncedCount} questions loaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        syncState.isComplete -> {
            Text(
                text = "Full history synced — stats cover all your questions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                syncState.error?.let { error ->
                    Text(
                        text = "History sync failed: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedButton(
                    onClick = onSyncClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text("Sync full history")
                }
            }
        }
    }
}

@Composable
private fun TagBreakdownList(entries: List<TagBrierEntry>) {
    Column {
        entries.forEachIndexed { index, entry ->
            if (index > 0) LedgerRule()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "#${entry.tag}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${entry.questionCount} " +
                        if (entry.questionCount == 1) "QUESTION" else "QUESTIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "%.3f".format(entry.brier),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private val monthLabelFormatter = DateTimeFormatter.ofPattern("MMM", Locale.US)
private val monthDayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/**
 * Brier score by resolution month: polyline + points, dashed reference line at
 * 0.5 (an always-50% forecaster's score). Lower is better.
 */
@Composable
private fun ScoreOverTimeChart(monthly: List<MonthlyBrier>) {
    val inkColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = mutedColor)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = inkColor)

    val chartDescription = remember(monthly) {
        buildString {
            append("Brier score by month, lower is better. ")
            monthly.forEach { m ->
                append("${m.month.format(monthLabelFormatter)} ${m.month.year}: ")
                append("%.2f".format(m.score))
                append(" over ${m.count} ")
                append(if (m.count == 1) "question. " else "questions. ")
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.7f)
            .semantics { contentDescription = chartDescription },
    ) {
        val leftPad = 28.dp.toPx()
        val rightPad = 8.dp.toPx()
        val bottomPad = 20.dp.toPx()
        // Room above the highest point for its value label.
        val topPad = 20.dp.toPx()
        val chartWidth = size.width - leftPad - rightPad
        val chartHeight = size.height - bottomPad - topPad

        // Y range: from 0 up to at least the 0.5 baseline so it's always visible.
        val yMax = (monthly.maxOf { it.score } * 1.15).coerceAtLeast(0.55)
        fun yFor(score: Double): Float =
            topPad + (chartHeight * (1 - (score / yMax))).toFloat()
        fun xFor(index: Int): Float =
            if (monthly.size == 1) leftPad + chartWidth / 2
            else leftPad + chartWidth * index / (monthly.size - 1).toFloat()

        // Baseline only: the reference line and labelled points do the y-axis's job.
        drawLine(
            outlineColor,
            Offset(leftPad, topPad + chartHeight),
            Offset(size.width, topPad + chartHeight),
            strokeWidth = 1.dp.toPx(),
        )
        val zeroLabel = textMeasurer.measure("0", labelStyle)
        drawText(
            zeroLabel,
            topLeft = Offset(
                leftPad - zeroLabel.size.width - 6.dp.toPx(),
                topPad + chartHeight - zeroLabel.size.height / 2,
            ),
        )

        // Dashed 0.5 reference (always-50% forecaster)
        val referenceY = yFor(0.5)
        drawLine(
            color = outlineColor,
            start = Offset(leftPad, referenceY),
            end = Offset(size.width, referenceY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )
        val halfLabel = textMeasurer.measure("0.5", labelStyle)
        drawText(
            halfLabel,
            topLeft = Offset(
                leftPad - halfLabel.size.width - 6.dp.toPx(),
                referenceY - halfLabel.size.height / 2,
            ),
        )

        // Polyline + points
        for (i in 1 until monthly.size) {
            drawLine(
                color = inkColor,
                start = Offset(xFor(i - 1), yFor(monthly[i - 1].score)),
                end = Offset(xFor(i), yFor(monthly[i].score)),
                strokeWidth = 2.dp.toPx(),
            )
        }
        monthly.forEachIndexed { i, m ->
            val center = Offset(xFor(i), yFor(m.score))
            drawCircle(surfaceColor, radius = 5.dp.toPx(), center = center)
            drawCircle(inkColor, radius = 3.5.dp.toPx(), center = center)
        }

        // Named after the line so it stays legible where the line crosses it.
        val referenceName = textMeasurer.measure("ALWAYS 50%", labelStyle)
        val referenceNameLeft = size.width - referenceName.size.width
        val referenceNameTop = referenceY - referenceName.size.height - 2.dp.toPx()
        drawRect(
            color = surfaceColor,
            topLeft = Offset(referenceNameLeft - 2.dp.toPx(), referenceNameTop),
            size = Size(referenceName.size.width + 2.dp.toPx(), referenceName.size.height.toFloat()),
        )
        drawText(referenceName, topLeft = Offset(referenceNameLeft, referenceNameTop))

        // Direct label on the latest month only: the number you came to check.
        val latest = monthly.last()
        val latestLabel = textMeasurer.measure("%.2f".format(latest.score), valueStyle)
        val latestX = xFor(monthly.lastIndex)
        val latestY = yFor(latest.score)
        val latestLeft = (latestX - latestLabel.size.width / 2f)
            .coerceIn(leftPad, size.width - latestLabel.size.width)
        val latestTop = latestY - latestLabel.size.height - 6.dp.toPx()
        drawRect(
            color = surfaceColor,
            topLeft = Offset(latestLeft - 2.dp.toPx(), latestTop),
            size = Size(latestLabel.size.width + 4.dp.toPx(), latestLabel.size.height.toFloat()),
        )
        drawText(latestLabel, topLeft = Offset(latestLeft, latestTop))

        // X labels: first, middle, last month (avoids crowding)
        val labelIndices = when {
            monthly.size <= 3 -> monthly.indices.toList()
            else -> listOf(0, monthly.size / 2, monthly.size - 1)
        }
        for (i in labelIndices) {
            val label = textMeasurer.measure(
                monthly[i].month.format(monthLabelFormatter).uppercase(Locale.US),
                labelStyle,
            )
            drawText(
                label,
                topLeft = Offset(
                    (xFor(i) - label.size.width / 2)
                        .coerceIn(0f, size.width - label.size.width),
                    topPad + chartHeight + 6.dp.toPx(),
                ),
            )
        }
    }
}

/** Number of 5%-wide bar slots spanning the folded 50-100% x-axis. */
private const val calibrationSlotCount = 10

/** Slot index (0-9) for a bucket, derived from its center (0.525 -> 0, 0.975 -> 9). */
private fun calibrationSlot(bucket: CalibrationBucket): Int =
    ((bucket.predictedRate * 100 - 50) / 5).toInt().coerceIn(0, calibrationSlotCount - 1)

@Composable
private fun CalibrationChart(buckets: List<CalibrationBucket>) {
    var selectedBucketIndex by remember { mutableStateOf<Int?>(null) }
    // Full-height column bounds per bucket, rebuilt on each draw for tap hit-testing.
    val barColumns = remember { mutableListOf<Pair<Rect, Int>>() }

    val inkColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val gridColor = Ledger.colors.rule
    val tooltipColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = mutedColor)
    val tooltipStyle = MaterialTheme.typography.labelSmall.copy(color = inkColor)

    val calibrationDescription = remember(buckets) {
        if (buckets.isEmpty()) "Calibration chart with no data"
        else buildString {
            append("Calibration chart, predictions folded to 50-100%. ")
            buckets.forEach { b ->
                append("${b.rangeLabel}: ${(b.actualRate * 100).toInt()}% actual, ${b.count} predictions. ")
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .semantics { contentDescription = calibrationDescription }
            .pointerInput(buckets) {
                detectTapGestures { offset ->
                    selectedBucketIndex = barColumns
                        .firstOrNull { (rect, _) -> rect.contains(offset) }
                        ?.second
                }
            },
    ) {
        val chartLeft = 36.dp.toPx()
        val chartBottom = size.height - 22.dp.toPx()
        val chartTop = 8.dp.toPx()
        val chartRight = size.width - 2.dp.toPx()
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        val yLabels = listOf("0%", "50%", "100%")
        yLabels.forEachIndexed { i, label ->
            val y = chartBottom - chartHeight * (i.toFloat() / (yLabels.size - 1))
            if (i > 0) {
                drawLine(
                    color = gridColor,
                    start = Offset(chartLeft, y),
                    end = Offset(chartRight, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            val textResult = textMeasurer.measure(label, labelStyle)
            drawText(
                textLayoutResult = textResult,
                topLeft = Offset(chartLeft - textResult.size.width - 6.dp.toPx(), y - textResult.size.height / 2f),
            )
        }

        val xLabels = listOf("50%", "75%", "100%")
        xLabels.forEachIndexed { i, label ->
            val x = chartLeft + chartWidth * (i.toFloat() / (xLabels.size - 1))
            val textResult = textMeasurer.measure(label, labelStyle)
            drawText(
                textLayoutResult = textResult,
                topLeft = Offset(
                    (x - textResult.size.width / 2f).coerceAtMost(size.width - textResult.size.width),
                    chartBottom + 6.dp.toPx(),
                ),
            )
        }

        val slotWidth = chartWidth / calibrationSlotCount
        val barInset = 2.dp.toPx()

        barColumns.clear()
        val barTops = buckets.mapIndexed { i, bucket ->
            val slot = calibrationSlot(bucket)
            val slotLeft = chartLeft + slotWidth * slot
            barColumns.add(
                Rect(slotLeft, chartTop, slotLeft + slotWidth, chartBottom) to i,
            )

            // A minimum sliver keeps 0%-actual buckets visible and tappable.
            val barHeight = (chartHeight * bucket.actualRate).coerceAtLeast(2.dp.toPx())
            val barTop = chartBottom - barHeight
            val alpha = 0.3f + 0.7f * (bucket.count.coerceAtMost(10) / 10f)
            drawRect(
                color = inkColor.copy(alpha = alpha),
                topLeft = Offset(slotLeft + barInset, barTop),
                size = Size(slotWidth - barInset * 2, chartBottom - barTop),
            )
            barTop
        }

        drawLine(
            color = inkColor,
            start = Offset(chartLeft, chartBottom),
            end = Offset(chartRight, chartBottom),
            strokeWidth = 1.dp.toPx(),
        )

        // Perfect calibration: actual == predicted, i.e. (50%, 50%) -> (100%, 100%).
        // Drawn after the bars so it stays visible over opaque ones.
        drawLine(
            color = outlineColor,
            start = Offset(chartLeft, chartBottom - chartHeight * 0.5f),
            end = Offset(chartRight, chartTop),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )

        selectedBucketIndex?.let { idx ->
            if (idx in buckets.indices) {
                val bucket = buckets[idx]
                val slot = calibrationSlot(bucket)
                val slotLeft = chartLeft + slotWidth * slot
                val barTop = barTops[idx]

                drawRect(
                    color = inkColor,
                    topLeft = Offset(slotLeft + barInset, barTop),
                    size = Size(slotWidth - barInset * 2, chartBottom - barTop),
                    style = Stroke(width = 1.5.dp.toPx()),
                )

                val tooltipText =
                    "${bucket.rangeLabel}: ${(bucket.actualRate * 100).toInt()}% actual (n=${bucket.count})"
                val textResult = textMeasurer.measure(tooltipText, tooltipStyle)
                val barCenterX = slotLeft + slotWidth / 2f
                val tooltipX = (barCenterX - textResult.size.width / 2f)
                    .coerceIn(chartLeft, chartRight - textResult.size.width)
                val tooltipY = (barTop - 8.dp.toPx() - textResult.size.height)
                    .coerceAtLeast(chartTop)

                drawRect(
                    color = tooltipColor,
                    topLeft = Offset(tooltipX - 4.dp.toPx(), tooltipY - 2.dp.toPx()),
                    size = Size(
                        textResult.size.width + 8.dp.toPx(),
                        textResult.size.height + 4.dp.toPx(),
                    ),
                )
                drawText(
                    textLayoutResult = textResult,
                    topLeft = Offset(tooltipX, tooltipY),
                )
            }
        }
    }
}

/**
 * Maps a day's forecast count to a heatmap cell color using fixed thresholds
 * (stable intensity scale across users, GitHub-contribution-graph style).
 */
private fun heatmapCellColor(count: Int, base: Color, empty: Color): Color = when {
    count <= 0 -> empty
    count <= 2 -> base.copy(alpha = 0.30f)
    count <= 3 -> base.copy(alpha = 0.50f)
    count <= 5 -> base.copy(alpha = 0.65f)
    count <= 9 -> base.copy(alpha = 0.85f)
    else -> base
}

@Composable
private fun ActivityHeatmap(dailyActivity: List<DayActivity>) {
    if (dailyActivity.isEmpty()) {
        Text(
            text = "No activity data yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val weeks: List<List<DayActivity>> = remember(dailyActivity) { dailyActivity.chunked(7) }
    val ink = MaterialTheme.colorScheme.onSurface
    val emptyCell = MaterialTheme.colorScheme.surfaceContainerHigh
    val cellShape = MaterialTheme.shapes.extraSmall
    // One clock read for the grid, not one per cell.
    val today = remember { LocalDate.now() }

    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    val description = remember(dailyActivity) {
        val total = dailyActivity.sumOf { it.count }
        val activeDays = dailyActivity.count { it.count > 0 }
        "Activity heatmap: $total forecasts across $activeDays active days in the last 11 weeks."
    }

    // Cells are sized from the width, so the grid fills the measure on any screen
    // and the month and weekday labels stay on the same pitch as the cells.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cellSize = (
            maxWidth - weekdayLabelWidth - heatmapLabelGap -
                heatmapCellSpacing * (weeks.size - 1)
            ) / weeks.size

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MonthLabelsRow(weeks = weeks, cellSize = cellSize)

            Row(horizontalArrangement = Arrangement.spacedBy(heatmapLabelGap)) {
                WeekdayLabelsColumn(cellSize = cellSize)

                Row(horizontalArrangement = Arrangement.spacedBy(heatmapCellSpacing)) {
                    weeks.forEach { week ->
                        Column(verticalArrangement = Arrangement.spacedBy(heatmapCellSpacing)) {
                            week.forEach { day ->
                                val isSelected = selectedDate == day.date
                                val isFuture = day.date.isAfter(today)
                                Box(
                                    modifier = Modifier
                                        .size(cellSize)
                                        .background(
                                            color = if (isFuture) {
                                                Color.Transparent
                                            } else {
                                                heatmapCellColor(day.count, ink, emptyCell)
                                            },
                                            shape = cellShape,
                                        )
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(1.5.dp, ink, cellShape)
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .clickable(enabled = !isFuture) {
                                            selectedDate = if (isSelected) null else day.date
                                        },
                                )
                            }
                        }
                    }
                }
            }

            // Fixed height: selecting a day shouldn't shift everything beneath the grid.
            val selected = selectedDate?.let { d -> dailyActivity.firstOrNull { it.date == d } }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (selected != null) {
                    val label = selected.date.format(monthDayFormatter).uppercase(Locale.US)
                    val countText =
                        if (selected.count == 1) "1 FORECAST" else "${selected.count} FORECASTS"
                    SectionLabel(text = "$label · $countText")
                }
            }
        }
    }
}

@Composable
private fun MonthLabelsRow(weeks: List<List<DayActivity>>, cellSize: Dp) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    // Only label a column when its first-day-of-week falls in a *new* month vs. the previous column.
    val labels: List<String?> = remember(weeks) {
        weeks.mapIndexed { i, week ->
            val firstDay = week.first().date
            if (i == 0 || firstDay.month != weeks[i - 1].first().date.month) {
                firstDay.format(monthLabelFormatter).uppercase(Locale.US)
            } else {
                null
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(heatmapLabelGap)) {
        Spacer(modifier = Modifier.width(weekdayLabelWidth))
        Row(horizontalArrangement = Arrangement.spacedBy(heatmapCellSpacing)) {
            labels.forEach { label ->
                Box(modifier = Modifier.width(cellSize)) {
                    if (label != null) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

private val weekdayLabelWidth: Dp = 28.dp
private val heatmapLabelGap: Dp = 6.dp
private val heatmapCellSpacing: Dp = 3.dp

@Composable
private fun WeekdayLabelsColumn(cellSize: Dp) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // GitHub-style sparse labels: Mon / Wed / Fri. Grid rows are Mon..Sun (indices 0..6).
    val labels = listOf("MON", "", "WED", "", "FRI", "", "")
    Column(verticalArrangement = Arrangement.spacedBy(heatmapCellSpacing)) {
        labels.forEach { label ->
            Box(
                modifier = Modifier
                    .width(weekdayLabelWidth)
                    .height(cellSize),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
