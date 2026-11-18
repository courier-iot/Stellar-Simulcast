package com.stellar.simulcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellar.simulcast.data.AniList
import com.stellar.simulcast.data.Anime
import com.stellar.simulcast.data.MangaLibrary
import com.stellar.simulcast.data.Watchlist
import java.time.LocalDate

/** Everything here is worked out from what's on this phone, plus live show info for genres. */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val ids by Watchlist.ids.collectAsStateWithLifecycle()
    val progress by Watchlist.progress.collectAsStateWithLifecycle()
    val follows by MangaLibrary.follows.collectAsStateWithLifecycle()
    val marks by MangaLibrary.marks.collectAsStateWithLifecycle()

    val shows by produceState<List<Anime>?>(null, ids, progress.keys) {
        value = runCatching { AniList.byIds((ids + progress.keys).toList()) }.getOrDefault(emptyList())
    }
    val days = remember(progress, marks) { Watchlist.activeDays.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet() }
    val today = LocalDate.now()
    val streak = remember(days) {
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        n
    }
    val thisMonth = days.count { it.year == today.year && it.month == today.month }

    Box(
        Modifier.fillMaxSize().background(Space.Bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Starfield()
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 32.dp).navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Space.Text) }
                Text("STATS", color = Space.Cyan, fontFamily = FontFamily.Monospace, fontSize = 18.sp, letterSpacing = 4.sp)
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Episodes logged", progress.values.sum().toString(), Modifier.weight(1f))
                StatTile("Shows starred", ids.size.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Manga followed", follows.size.toString(), Modifier.weight(1f))
                StatTile("Manga in progress", marks.size.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Day streak", streak.toString(), Modifier.weight(1f), accent = Space.Magenta)
                StatTile("Active days this month", thisMonth.toString(), Modifier.weight(1f), accent = Space.Magenta)
            }

            // Last 5 weeks, one square per day.
            Column(Modifier.fillMaxWidth().padding(12.dp).neonPanel().padding(14.dp)) {
                StatLabel("Last 5 weeks")
                (4 downTo 0).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                        (6 downTo 0).forEach { dayBack ->
                            val d = today.minusDays((week * 7 + dayBack).toLong())
                            Box(
                                Modifier.size(22.dp).clip(RoundedCornerShape(5.dp))
                                    .background(if (d in days) Space.Cyan else Color(0x22FFFFFF))
                            )
                        }
                    }
                }
                Text("A day lights up when you log an episode or read a chapter.", color = Space.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).neonPanel().padding(14.dp)) {
                StatLabel("Top genres")
                val list = shows
                when {
                    list == null -> Text("Loading…", color = Space.Muted)
                    list.isEmpty() -> Text("Star shows or log episodes to see your genres.", color = Space.Muted)
                    else -> {
                        // Shows you've watched more of count for more.
                        val weights = mutableMapOf<String, Int>()
                        list.forEach { a -> a.genres.forEach { g -> weights[g] = (weights[g] ?: 0) + 1 + (progress[a.id] ?: 0) } }
                        val top = weights.entries.sortedByDescending { it.value }.take(6)
                        val max = top.firstOrNull()?.value?.toFloat() ?: 1f
                        top.forEach { (g, w) ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                                Text(g, color = Space.Text, fontSize = 13.sp, modifier = Modifier.width(110.dp))
                                Box(
                                    Modifier.height(10.dp).weight(w / max).clip(RoundedCornerShape(5.dp)).background(Space.Cyan)
                                )
                                if (w < max) Spacer(Modifier.weight(1f - w / max + 0.0001f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, accent: Color = Space.Cyan) {
    Column(modifier.neonPanel(accent).padding(14.dp)) {
        Text(value, color = accent, fontFamily = FontFamily.Monospace, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Space.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun StatLabel(text: String) {
    Text(text.uppercase(), color = Space.Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 8.dp))
}
