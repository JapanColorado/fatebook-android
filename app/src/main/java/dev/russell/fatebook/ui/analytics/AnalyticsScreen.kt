package dev.russell.fatebook.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Size
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
import kotlin.math.sqrt

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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BrierScoreCard(
                    brierScore = state.brierScore,
                    totalForecasts = state.totalForecasts,
                )

                CalibrationChart(buckets = state.calibrationBuckets)

                StreakCard(currentStreak = state.currentStreak)

                ActivityChart(weeklyActivity = state.weeklyActivity)
            }
        }
    }
}

@Composable
private fun BrierScoreCard(
    brierScore: Double?,
    totalForecasts: Int,
) {
    var showTooltip by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = brierScore?.let { "%.2f".format(it) } ?: "--",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Brier Score",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "What is Brier Score?",
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { showTooltip = true },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DropdownMenu(
                        expanded = showTooltip,
                        onDismissRequest = { showTooltip = false },
                    ) {
                        Text(
                            text = "Lower is better. Perfect = 0.0",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Based on $totalForecasts forecasts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalibrationChart(buckets: List<CalibrationBucket>) {
    Text(
        text = "Calibration",
        style = MaterialTheme.typography.titleMedium,
    )

    var selectedBucketIndex by remember { mutableStateOf<Int?>(null) }
    val dotPositions = remember { mutableListOf<Offset>() }

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
            append("Calibration chart. ")
            buckets.forEach { b ->
                append("${b.rangeLabel}: ${(b.actualRate * 100).toInt()}% actual, ${b.count} predictions. ")
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .semantics { contentDescription = calibrationDescription }
            .pointerInput(buckets) {
                val threshold = 24.dp.toPx()
                detectTapGestures { offset ->
                    val nearest = dotPositions.withIndex().minByOrNull { (_, pos) ->
                        sqrt(
                            (pos.x - offset.x) * (pos.x - offset.x) +
                                (pos.y - offset.y) * (pos.y - offset.y),
                        )
                    }
                    if (nearest != null) {
                        val dist = sqrt(
                            (nearest.value.x - offset.x) * (nearest.value.x - offset.x) +
                                (nearest.value.y - offset.y) * (nearest.value.y - offset.y),
                        )
                        selectedBucketIndex = if (dist <= threshold) nearest.index else null
                    } else {
                        selectedBucketIndex = null
                    }
                }
            },
    ) {
        val chartLeft = 36.dp.toPx()
        val chartBottom = size.height - 24.dp.toPx()
        val chartTop = 8.dp.toPx()
        val chartRight = size.width - 8.dp.toPx()
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        // Draw dashed diagonal (perfect calibration line)
        drawLine(
            color = outlineColor,
            start = Offset(chartLeft, chartBottom),
            end = Offset(chartRight, chartTop),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )

        // Draw axes
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

        // Axis labels
        val xLabels = listOf("0%", "50%", "100%")
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

        // Draw data points
        if (buckets.isNotEmpty()) {
            val points = buckets.map { bucket ->
                val x = chartLeft + chartWidth * bucket.predictedRate
                val y = chartBottom - chartHeight * bucket.actualRate
                Offset(x, y)
            }

            dotPositions.clear()
            dotPositions.addAll(points)

            // Draw dots
            points.forEachIndexed { i, point ->
                val radius = 4.dp.toPx() + 2.dp.toPx() * (buckets[i].count.coerceAtMost(10) / 10f)
                drawCircle(
                    color = primaryColor,
                    radius = radius,
                    center = point,
                )
            }

            // Draw selected dot highlight + tooltip
            selectedBucketIndex?.let { idx ->
                if (idx in buckets.indices) {
                    val bucket = buckets[idx]
                    val point = points[idx]

                    drawCircle(
                        color = onSurfaceColor,
                        radius = 8.dp.toPx(),
                        center = point,
                        style = Stroke(width = 2f),
                    )

                    val tooltipText = "${bucket.rangeLabel}: ${(bucket.actualRate * 100).toInt()}% actual (n=${bucket.count})"
                    val textResult = textMeasurer.measure(tooltipText, tooltipStyle)
                    val tooltipX = (point.x - textResult.size.width / 2f)
                        .coerceIn(chartLeft, chartRight - textResult.size.width)
                    val tooltipY = point.y - 12.dp.toPx() - textResult.size.height

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

@Composable
private fun StreakCard(currentStreak: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.LocalFireDepartment,
                contentDescription = "Streak",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    text = currentStreak.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (currentStreak == 1) "Day Streak" else "$currentStreak consecutive days",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActivityChart(weeklyActivity: List<WeekActivity>) {
    Text(
        text = "Activity (Last 12 Weeks)",
        style = MaterialTheme.typography.titleMedium,
    )

    var selectedBarIndex by remember { mutableStateOf<Int?>(null) }

    val activityDescription = remember(weeklyActivity) {
        if (weeklyActivity.isEmpty()) "Activity chart with no data"
        else buildString {
            append("Weekly activity chart. ")
            weeklyActivity.forEach { w ->
                append("${w.weekLabel}: ${w.count} predictions. ")
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 8.sp,
        color = onSurfaceColor,
    )
    val tooltipStyle = TextStyle(
        fontSize = 10.sp,
        color = onSurfaceColor,
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .semantics { contentDescription = activityDescription }
            .pointerInput(weeklyActivity) {
                if (weeklyActivity.isEmpty()) return@pointerInput
                val chartLeftPx = 28.dp.toPx()
                val chartRightPx = size.width - 8.dp.toPx()
                val chartWidthPx = chartRightPx - chartLeftPx
                val slotWidth = chartWidthPx / weeklyActivity.size
                val gapWidthPx = slotWidth * 0.3f

                detectTapGestures { offset ->
                    val tappedIndex = weeklyActivity.indices.firstOrNull { i ->
                        val barLeft = chartLeftPx + slotWidth * i + gapWidthPx / 2f
                        val barRight = barLeft + slotWidth * 0.7f
                        offset.x >= barLeft && offset.x <= barRight
                    }
                    selectedBarIndex = if (tappedIndex != null && weeklyActivity[tappedIndex].count > 0) {
                        tappedIndex
                    } else {
                        null
                    }
                }
            },
    ) {
        if (weeklyActivity.isEmpty()) return@Canvas

        val chartLeft = 28.dp.toPx()
        val chartBottom = size.height - 32.dp.toPx()
        val chartTop = 8.dp.toPx()
        val chartRight = size.width - 8.dp.toPx()
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        val maxCount = (weeklyActivity.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
        val barWidth = chartWidth / weeklyActivity.size * 0.7f
        val gapWidth = chartWidth / weeklyActivity.size * 0.3f

        // Draw baseline
        drawLine(
            color = onSurfaceColor,
            start = Offset(chartLeft, chartBottom),
            end = Offset(chartRight, chartBottom),
            strokeWidth = 1f,
        )

        weeklyActivity.forEachIndexed { i, week ->
            val barLeft = chartLeft + (chartWidth / weeklyActivity.size) * i + gapWidth / 2f
            val barHeight = (week.count.toFloat() / maxCount) * chartHeight
            val barTop = chartBottom - barHeight

            if (week.count > 0) {
                drawRect(
                    color = primaryColor,
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barWidth, barHeight),
                )
            }

            // X-axis label (show every 2nd to avoid crowding)
            if (i % 2 == 0 || weeklyActivity.size <= 6) {
                val textResult = textMeasurer.measure(week.weekLabel, labelStyle)
                drawText(
                    textLayoutResult = textResult,
                    topLeft = Offset(
                        barLeft + barWidth / 2 - textResult.size.width / 2f,
                        chartBottom + 4.dp.toPx(),
                    ),
                )
            }
        }

        // Y-axis labels
        val yLabels = listOf("0", maxCount.toString())
        yLabels.forEachIndexed { i, label ->
            val y = chartBottom - chartHeight * (i.toFloat() / (yLabels.size - 1))
            val textResult = textMeasurer.measure(label, labelStyle)
            drawText(
                textLayoutResult = textResult,
                topLeft = Offset(chartLeft - textResult.size.width - 4.dp.toPx(), y - textResult.size.height / 2f),
            )
        }

        // Draw selected bar highlight + tooltip
        selectedBarIndex?.let { idx ->
            if (idx in weeklyActivity.indices) {
                val week = weeklyActivity[idx]
                val barLeft = chartLeft + (chartWidth / weeklyActivity.size) * idx + gapWidth / 2f
                val barHeight = (week.count.toFloat() / maxCount) * chartHeight
                val barTop = chartBottom - barHeight

                drawRect(
                    color = onSurfaceColor,
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barWidth, barHeight),
                    style = Stroke(width = 2f),
                )

                val tooltipText = "${week.count} predictions – ${week.weekLabel}"
                val textResult = textMeasurer.measure(tooltipText, tooltipStyle)
                val tooltipX = (barLeft + barWidth / 2 - textResult.size.width / 2f)
                    .coerceIn(chartLeft, chartRight - textResult.size.width)
                val tooltipY = barTop - textResult.size.height - 6.dp.toPx()

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
