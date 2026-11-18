package com.stellar.simulcast.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.stellar.simulcast.MainActivity
import com.stellar.simulcast.data.MangaDex
import com.stellar.simulcast.data.MangaLibrary
import com.stellar.simulcast.data.Settings
import com.stellar.simulcast.ui.ago
import com.stellar.simulcast.ui.paletteFor

private data class WidgetRow(val title: String, val label: String, val at: Long)

/** Home screen widget: the newest chapters found for manga you follow. */
class MangaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Settings.init(context)
        MangaLibrary.init(context)
        val entries = MangaLibrary.recentNew.mapNotNull { line ->
            val p = line.split("|")
            if (p.size < 4) null else Triple(p[0], p[2], p[3].toLongOrNull() ?: 0L)
        }.take(5)
        val titles = if (entries.isEmpty()) emptyMap()
        else runCatching { MangaDex.byIds(entries.map { it.first }.toSet()).associate { it.id to it.title } }.getOrDefault(emptyMap())
        val rows = entries.map { (mid, label, at) -> WidgetRow(titles[mid] ?: "Manga", label, at) }
        val palette = paletteFor(Settings.display.accent)
        val following = MangaLibrary.follows.value.isNotEmpty()
        val now = System.currentTimeMillis() / 1000

        provideContent { Body(rows, following, now, palette.primary, palette.secondary) }
    }

    companion object {
        suspend fun refresh(ctx: Context) = MangaWidget().updateAll(ctx)
    }
}

class MangaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MangaWidget()
}

@Composable
private fun Body(rows: List<WidgetRow>, following: Boolean, now: Long, primary: Color, secondary: Color) {
    Column(
        GlanceModifier.fillMaxSize().background(Color(0xF0070A1C)).cornerRadius(18.dp).padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Text("NEW CHAPTERS", style = TextStyle(color = ColorProvider(primary), fontSize = 12.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(6.dp))
        when {
            !following -> Note("Follow manga in Stellar Simulcast to see new chapters here.")
            rows.isEmpty() -> Note("No new chapters yet. They show up here when alerts find them.")
            else -> rows.forEach { r ->
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(r.title, maxLines = 1, style = TextStyle(color = ColorProvider(Color(0xFFE6F1FF)), fontSize = 13.sp),
                        modifier = GlanceModifier.defaultWeight())
                    Text("  ${r.label} · ${ago(now - r.at)}", maxLines = 1, style = TextStyle(color = ColorProvider(secondary), fontSize = 12.sp))
                }
            }
        }
    }
}

@Composable
private fun Note(message: String) {
    Text(message, style = TextStyle(color = ColorProvider(Color(0xFF8A93B8)), fontSize = 12.sp))
}
