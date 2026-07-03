package dev.russell.fatebook.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.russell.fatebook.MainActivity
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.notification.NotificationHelper

/**
 * Home-screen widget: ready-to-resolve count (tap -> feed with the Ready to
 * Resolve filter) and a "+" quick-create button. Reads the Room cache via a
 * Hilt EntryPoint, so it works offline and without the app process running.
 */
class FatebookWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun questionRepository(): QuestionRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = EntryPointAccessors
            .fromApplication(context, WidgetEntryPoint::class.java)
            .questionRepository()
        val readyCount = runCatching { repository.countReadyToResolve() }.getOrDefault(0)

        provideContent {
            GlanceTheme {
                WidgetContent(context, readyCount)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context, readyCount: Int) {
        val openResolveIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(NotificationHelper.EXTRA_OPEN_RESOLVE_FILTER, true)
        }
        val openCreateIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(NotificationHelper.EXTRA_OPEN_CREATE, true)
        }

        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(openResolveIntent)),
            ) {
                Text(
                    text = "$readyCount",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = "ready to resolve",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                )
            }
            Spacer(modifier = GlanceModifier.size(8.dp))
            Column(
                modifier = GlanceModifier
                    .size(44.dp)
                    .background(GlanceTheme.colors.primary)
                    .cornerRadius(22.dp)
                    .clickable(actionStartActivity(openCreateIntent)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "+",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}
