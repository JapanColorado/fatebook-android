package dev.russell.fatebook.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
                    totalResolved = state.totalResolved,
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
    totalResolved: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = brierScore?.let { "%.2f".format(it) } ?: "--",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Brier Score",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Lower is better. Perfect = 0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Based on $totalResolved resolved predictions",
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

    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 10.sp,
        color = onSurfaceColor,
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
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

        // Draw data points and connecting lines
        if (buckets.isNotEmpty()) {
            val points = buckets.map { bucket ->
                val x = chartLeft + chartWidth * bucket.predictedRate
                val y = chartBottom - chartHeight * bucket.actualRate
                Offset(x, y)
            }

            // Connect dots with lines
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = primaryColor,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 2f,
                )
            }

            // Draw dots
            points.forEach { point ->
                drawCircle(
                    color = primaryColor,
                    radius = 5.dp.toPx(),
                    center = point,
                )
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
                contentDescription = null,
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

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 8.sp,
        color = onSurfaceColor,
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
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
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
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
    }
}
