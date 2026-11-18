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
import com.stellar.simulcast.data.AniList
import com.stellar.simulcast.data.Anime
import com.stellar.simulcast.data.Settings
import com.stellar.simulcast.data.TimeText
import com.stellar.simulcast.data.TitleLang
import com.stellar.simulcast.data.Watchlist
import com.stellar.simulcast.ui.countdown
import com.stellar.simulcast.ui.paletteFor

/** Home screen widget: the next few episodes from your watchlist, fetched live. */
class NextUpWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Watchlist.init(context)
        Settings.init(context)
        val ids = Watchlist.ids.value
        val shows: List<Anime>? =
            if (ids.isEmpty()) emptyList()
            else runCatching { AniList.byIds(ids.toList()) }.getOrNull()
        val upcoming = shows
            ?.filter { it.nextAiringAt != null }
            ?.sortedBy { it.nextAiringAt }
            ?.take(5)
        val palette = paletteFor(Settings.display.accent)
        val lang = Settings.titleLang
        val now = System.currentTimeMillis() / 1000

        provideContent {
            WidgetBody(upcoming, ids.isEmpty(), lang, now, palette.primary, palette.secondary)
        }
    }

    companion object {
        suspend fun refresh(ctx: Context) = NextUpWidget().updateAll(ctx)
    }
}

class NextUpWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextUpWidget()
}

private val muted = Color(0xFF8A93B8)
private val text = Color(0xFFE6F1FF)

@Composable
private fun WidgetBody(
    upcoming: List<Anime>?,
    nothingStarred: Boolean,
    lang: TitleLang,
    now: Long,
    primary: Color,
    secondary: Color,
) {
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(Color(0xF0070A1C))
            .cornerRadius(18.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Text(
            "NEXT UP",
            style = TextStyle(color = ColorProvider(primary), fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.height(6.dp))
        when {
            nothingStarred -> Note("Star shows in Stellar Simulcast to see them here.")
            upcoming == null -> Note("Couldn't reach AniList. Tap to open the app.")
            upcoming.isEmpty() -> Note("None of your starred shows have an episode scheduled.")
            else -> upcoming.forEach { a ->
                val at = TimeText.streamAt(a.nextAiringAt ?: 0L)
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(
                        a.name(lang),
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(text), fontSize = 13.sp),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Text(
                        "  EP ${a.nextEpisode ?: "?"} · ${countdown(at - now)}",
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(secondary), fontSize = 12.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Note(message: String) {
    Text(message, style = TextStyle(color = ColorProvider(muted), fontSize = 12.sp))
}
