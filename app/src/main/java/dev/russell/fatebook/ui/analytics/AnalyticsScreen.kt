package dev.russell.fatebook.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AnalyticsScreenContent(state = state, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreenContent(
    state: AnalyticsUiState,
    onBack: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ForecastsCard(
                        totalForecasts = state.totalForecasts,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    BrierScoreCard(
                        brierScore = state.brierScore,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    StreakChip(
                        currentStreak = state.currentStreak,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }

                Text(
                    text = "Calibration",
                    style = MaterialTheme.typography.titleMedium,
                )
                CalibrationChart(buckets = state.calibrationBuckets)

                Text(
                    text = "Activity",
                    style = MaterialTheme.typography.titleMedium,
                )
                ActivityHeatmap(dailyActivity = state.dailyActivity)
            }
        }
    }
}

@Composable
private fun ForecastsCard(
    totalForecasts: Int,
    modifier: Modifier = Modifier,
) {
    StatCard(
        title = "Forecasts",
        value = totalForecasts.toString(),
        modifier = modifier,
    )
}

@Composable
private fun BrierScoreCard(
    brierScore: Double?,
    modifier: Modifier = Modifier,
) {
    var showTooltip by remember { mutableStateOf(false) }
    StatCard(
        title = "Brier",
        value = brierScore?.let { "%.2f".format(it) } ?: "--",
        modifier = modifier,
        trailingIcon = {
            Box {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = "What is Brier Score?",
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { showTooltip = true },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DropdownMenu(
                    expanded = showTooltip,
                    onDismissRequest = { showTooltip = false },
                ) {
                    Text(
                        text = "Lower is better. Perfect = 0.0, " +
                            "always-50% = 0.5 (matches fatebook.io).",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
    )
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                leadingIcon?.invoke()
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                trailingIcon?.invoke()
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StreakChip(
    currentStreak: Int,
    modifier: Modifier = Modifier,
) {
    StatCard(
        title = "Streak",
        value = currentStreak.toString(),
        modifier = modifier,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.LocalFireDepartment,
                contentDescription = "Streak",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    )
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

    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 10.sp,
        color = onSurfaceColor,
    )
    val tooltipStyle = TextStyle(
        fontSize = 11.sp,
        color = onSurfaceColor,
    )

    val calibrationDescription = remember(buckets) {
        if (buckets.isEmpty()) "Calibration chart with no data"
        else buildString {
            append("Calibration chart, predictions folded to 50-100%. ")
            buckets.forEach { b ->
                append("${b.rangeLabel}: ${(b.actualRate * 100).toInt()}% actual, ${b.count} predictions. ")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = weekdayLabelWidth + heatmapLabelGap),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
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
            val chartBottom = size.height - 24.dp.toPx()
            val chartTop = 8.dp.toPx()
            val chartRight = size.width - 8.dp.toPx()
            val chartWidth = chartRight - chartLeft
            val chartHeight = chartBottom - chartTop

            drawLine(
                color = onSurfaceColor,
                start = Offset(chartLeft, chartTop),
                end = Offset(chartLeft, chartBottom),
                strokeWidth = 1f,
            )
            drawLine(
                color = onSurfaceColor,
                start = Offset(chartLeft, chartBottom),
                end = Offset(chartRight, chartBottom),
                strokeWidth = 1f,
            )

            val xLabels = listOf("50%", "75%", "100%")
            xLabels.forEachIndexed { i, label ->
                val x = chartLeft + chartWidth * (i.toFloat() / (xLabels.size - 1))
                val textResult = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult = textResult,
                    topLeft = Offset(x - textResult.size.width / 2f, chartBottom + 4.dp.toPx()),
                )
            }

            val yLabels = listOf("0%", "50%", "100%")
            yLabels.forEachIndexed { i, label ->
                val y = chartBottom - chartHeight * (i.toFloat() / (yLabels.size - 1))
                val textResult = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult = textResult,
                    topLeft = Offset(chartLeft - textResult.size.width - 4.dp.toPx(), y - textResult.size.height / 2f),
                )
            }

            val slotWidth = chartWidth / calibrationSlotCount
            val barInset = slotWidth * 0.125f

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
                    color = primaryColor.copy(alpha = alpha),
                    topLeft = Offset(slotLeft + barInset, barTop),
                    size = Size(slotWidth - barInset * 2, chartBottom - barTop),
                )
                barTop
            }

            // Perfect calibration: actual == predicted, i.e. (50%, 50%) -> (100%, 100%).
            // Drawn after the bars so it stays visible over opaque ones.
            drawLine(
                color = outlineColor,
                start = Offset(chartLeft, chartBottom - chartHeight * 0.5f),
                end = Offset(chartRight, chartTop),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )

            selectedBucketIndex?.let { idx ->
                if (idx in buckets.indices) {
                    val bucket = buckets[idx]
                    val slot = calibrationSlot(bucket)
                    val slotLeft = chartLeft + slotWidth * slot
                    val barTop = barTops[idx]

                    drawRect(
                        color = onSurfaceColor,
                        topLeft = Offset(slotLeft + barInset, barTop),
                        size = Size(slotWidth - barInset * 2, chartBottom - barTop),
                        style = Stroke(width = 2f),
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
                        color = surfaceVariantColor,
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
}

/**
 * Maps a day's forecast count to a heatmap cell color using fixed thresholds
 * (stable intensity scale across users, GitHub-contribution-graph style).
 */
private fun heatmapCellColor(count: Int, maxCount: Int, base: Color, empty: Color): Color = when {
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

    val weeks: List<List<DayActivity>> = dailyActivity.chunked(7)
    val maxCount = dailyActivity.maxOf { it.count }.coerceAtLeast(1)
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    val cellSize = 24.dp
    val cellSpacing = 4.dp

    val description = remember(dailyActivity) {
        val total = dailyActivity.sumOf { it.count }
        val activeDays = dailyActivity.count { it.count > 0 }
        "Activity heatmap: $total forecasts across $activeDays active days in the last 11 weeks."
    }

    Column(
        modifier = Modifier
            .padding(end = weekdayLabelWidth + heatmapLabelGap)
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MonthLabelsRow(weeks = weeks, cellSize = cellSize, cellSpacing = cellSpacing)

        Row(horizontalArrangement = Arrangement.spacedBy(heatmapLabelGap)) {
            WeekdayLabelsColumn(cellSize = cellSize, cellSpacing = cellSpacing)

            Row(horizontalArrangement = Arrangement.spacedBy(cellSpacing)) {
                weeks.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(cellSpacing)) {
                        week.forEach { day ->
                            val color = heatmapCellColor(
                                count = day.count,
                                maxCount = maxCount,
                                base = primary,
                                empty = surfaceVariant,
                            )
                            val isSelected = selectedDate == day.date
                            val isFuture = day.date.isAfter(LocalDate.now())
                            Box(
                                modifier = Modifier
                                    .size(cellSize)
                                    .background(
                                        color = if (isFuture) Color.Transparent else color,
                                        shape = RoundedCornerShape(4.dp),
                                    )
                                    .then(
                                        if (isSelected) {
                                            Modifier.background(
                                                color = color,
                                                shape = RoundedCornerShape(4.dp),
                                            )
                                        } else Modifier,
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

        Spacer(modifier = Modifier.height(8.dp))

        val selected = selectedDate?.let { d -> dailyActivity.firstOrNull { it.date == d } }
        Box(modifier = Modifier.fillMaxWidth().height(24.dp)) {
            if (selected != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    val formatter = DateTimeFormatter.ofPattern("MMM d")
                    val label = selected.date.format(formatter)
                    val countText = if (selected.count == 1) "1 forecast" else "${selected.count} forecasts"
                    Text(
                        text = "$label: $countText",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

    }
}

@Composable
private fun MonthLabelsRow(
    weeks: List<List<DayActivity>>,
    cellSize: androidx.compose.ui.unit.Dp,
    cellSpacing: androidx.compose.ui.unit.Dp,
) {
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    val monthFormatter = DateTimeFormatter.ofPattern("MMM")

    // Only label a column when its first-day-of-week falls in a *new* month vs. the previous column.
    val labels: List<String?> = weeks.mapIndexed { i, week ->
        val firstDay = week.first().date
        if (i == 0) {
            firstDay.format(monthFormatter)
        } else {
            val prevFirst = weeks[i - 1].first().date
            if (firstDay.month != prevFirst.month) firstDay.format(monthFormatter) else null
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(heatmapLabelGap)) {
        Spacer(modifier = Modifier.width(weekdayLabelWidth))
        Row(horizontalArrangement = Arrangement.spacedBy(cellSpacing)) {
            labels.forEach { label ->
                Box(modifier = Modifier.width(cellSize)) {
                    if (label != null) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurface,
                        )
                    }
                }
            }
        }
    }
}

private val weekdayLabelWidth = 28.dp
private val heatmapLabelGap = 6.dp

@Composable
private fun WeekdayLabelsColumn(
    cellSize: androidx.compose.ui.unit.Dp,
    cellSpacing: androidx.compose.ui.unit.Dp,
) {
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    // GitHub-style sparse labels: Mon / Wed / Fri. Grid rows are Mon..Sun (indices 0..6).
    val labels = listOf("Mon", "", "Wed", "", "Fri", "", "")
    Column(verticalArrangement = Arrangement.spacedBy(cellSpacing)) {
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
                        color = onSurface,
                    )
                }
            }
        }
    }
}

