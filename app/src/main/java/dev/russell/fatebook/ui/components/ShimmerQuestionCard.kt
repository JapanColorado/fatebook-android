package dev.russell.fatebook.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The animated value is exposed as a lambda and read only inside [drawBehind],
 * so the 60fps shimmer invalidates the draw phase alone — no composable
 * recomposes while the animation runs.
 */
@Composable
private fun rememberShimmerTranslate(): () -> Float {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )
    return remember { { translateAnim.value } }
}

@Composable
private fun shimmerColors(): List<Color> = listOf(
    MaterialTheme.colorScheme.surfaceContainerHigh,
    MaterialTheme.colorScheme.surfaceContainerLow,
    MaterialTheme.colorScheme.surfaceContainerHigh,
)

private fun Modifier.shimmer(
    colors: List<Color>,
    translate: () -> Float,
): Modifier = drawBehind {
    val t = translate()
    drawRect(
        Brush.linearGradient(
            colors = colors,
            start = Offset(t - 200f, 0f),
            end = Offset(t, 0f),
        )
    )
}

/** Standalone shimmer row with its own animation (previews/screenshots). */
@Composable
fun ShimmerQuestionCard(modifier: Modifier = Modifier) {
    ShimmerQuestionCard(
        colors = shimmerColors(),
        translate = rememberShimmerTranslate(),
        modifier = modifier,
    )
}

@Composable
private fun ShimmerQuestionCard(
    colors: List<Color>,
    translate: () -> Float,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.extraSmall

    // Same geometry as QuestionCard, so content lands where the skeleton was.
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LedgerMargin, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Title placeholder
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .clip(shape)
                        .shimmer(colors, translate),
                )
                // Figure placeholder
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(28.dp)
                        .clip(shape)
                        .shimmer(colors, translate),
                )
            }
            // Probability bar placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .shimmer(colors, translate),
            )
            // Metadata line placeholders
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(12.dp)
                        .clip(shape)
                        .shimmer(colors, translate),
                )
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(12.dp)
                        .clip(shape)
                        .shimmer(colors, translate),
                )
            }
        }
        LedgerRule()
    }
}

@Composable
fun ShimmerQuestionCardList(
    modifier: Modifier = Modifier,
    count: Int = 6,
) {
    // One shared transition for the whole list instead of one per row.
    val translate = rememberShimmerTranslate()
    val colors = shimmerColors()
    Column(modifier = modifier) {
        repeat(count) {
            ShimmerQuestionCard(colors = colors, translate = translate)
        }
    }
}
