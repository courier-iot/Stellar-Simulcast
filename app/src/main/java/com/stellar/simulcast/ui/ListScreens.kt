package com.stellar.simulcast.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellar.simulcast.data.Load
import com.stellar.simulcast.data.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

// ---------- Schedule: this week, day by day ----------

@Composable
fun ScheduleScreen(vm: AppViewModel, open: (Int) -> Unit) {
    LaunchedEffect(Unit) { vm.loadSchedule() }
    val state by vm.schedule.collectAsStateWithLifecycle()
    val watch = LocalWatch.current
    var onlyWatch by rememberSaveable { mutableStateOf(false) }
    val filters by Settings.filters.collectAsStateWithLifecycle()
    var dayIndex by rememberSaveable { mutableStateOf(0) }
    val now = rememberNow()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    Column(Modifier.fillMaxSize()) {
        ToolbarRow("Airing this week", onRefresh = { vm.loadSchedule(force = true) }) {
            NeonChip("★ Only mine", onlyWatch) { onlyWatch = !onlyWatch }
        }
        when (val s = state) {
            Load.Loading -> LoadingView()
            is Load.Error -> ErrorView(s.message) { vm.loadSchedule(force = true) }
            is Load.Ready -> {
                val eps = s.data.filter { (!onlyWatch || it.anime.id in watch.ids) && filters.matches(it.anime) }
                val byDay = eps.groupBy { Instant.ofEpochSecond(it.airingAt).atZone(zone).toLocalDate() }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(7) { i ->
                        val d = today.plusDays(i.toLong())
                        DayChip(
                            name = if (i == 0) "Today" else d.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.getDefault()),
                            date = d.dayOfMonth.toString(),
                            count = byDay[d]?.size ?: 0,
                            selected = i == dayIndex,
                        ) { dayIndex = i }
                    }
                }
                val list = byDay[today.plusDays(dayIndex.toLong())].orEmpty()
                if (list.isEmpty()) {
                    EmptyView(
                        "Nothing on this day",
                        when {
                            onlyWatch -> "None of your starred shows air on this day."
                            filters.active -> "Nothing matches your filters on this day."
                            else -> "No episodes are scheduled."
                        },
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(list, key = { it.id }) { ep ->
                            val left = ep.airingAt - now
                            AnimeRow(
                                ep.anime,
                                headline = "${clock(ep.airingAt)} · EP ${ep.episode} · " +
                                    if (left > 0) countdown(left) else "aired",
                                headlineColor = if (left > 0) Space.Cyan else Space.Magenta,
                                onClick = { open(ep.anime.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayChip(name: String, date: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .width(62.dp)
            .clip(shape)
            .background(if (selected) Space.Cyan else Space.Card)
            .border(1.dp, (if (selected) Space.Cyan else Space.Muted).copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val fg = if (selected) Space.Bg else Space.Text
        Text(name, color = fg, fontSize = 11.sp, maxLines = 1)
        Text(date, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text("$count eps", color = if (selected) Space.Bg else Space.Muted, fontSize = 10.sp)
    }
}

// ---------- Just aired: last 48 hours ----------

@Composable
fun JustAiredScreen(vm: AppViewModel, open: (Int) -> Unit) {
    LaunchedEffect(Unit) { vm.loadRecent() }
    val state by vm.recent.collectAsStateWithLifecycle()
    val watch = LocalWatch.current
    var onlyWatch by rememberSaveable { mutableStateOf(false) }
    val filters by Settings.filters.collectAsStateWithLifecycle()
    val now = rememberNow()

    Column(Modifier.fillMaxSize()) {
        ToolbarRow("Last 48 hours", onRefresh = { vm.loadRecent(force = true) }) {
            NeonChip("★ Only mine", onlyWatch) { onlyWatch = !onlyWatch }
        }
        when (val s = state) {
            Load.Loading -> LoadingView()
            is Load.Error -> ErrorView(s.message) { vm.loadRecent(force = true) }
            is Load.Ready -> {
                val eps = s.data.filter { (!onlyWatch || it.anime.id in watch.ids) && filters.matches(it.anime) }
                if (eps.isEmpty()) {
                    EmptyView("All quiet", if (filters.active) "Nothing matches your filters in the last two days." else "No new episodes in the last two days.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(eps, key = { it.id }) { ep ->
                            val total = ep.anime.episodes
                            val epText = if (total != null) "EP ${ep.episode}/$total" else "EP ${ep.episode}"
                            AnimeRow(
                                ep.anime,
                                headline = "$epText · ${ago(now - ep.airingAt)}",
                                headlineColor = Space.Magenta,
                                onClick = { open(ep.anime.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- Watchlist ----------

@Composable
fun WatchlistScreen(vm: AppViewModel, open: (Int) -> Unit) {
    val watch = LocalWatch.current
    val state by vm.watchlist.collectAsStateWithLifecycle()
    val now = rememberNow()

    LaunchedEffect(watch.ids) {
        val current = vm.watchlist.value
        val loaded = (current as? Load.Ready)?.data?.map { it.id }?.toSet()
        if (loaded == null || !loaded.containsAll(watch.ids)) vm.loadWatchlist(watch.ids)
    }

    Column(Modifier.fillMaxSize()) {
        AlertsPanel()
        if (watch.ids.isEmpty()) {
            EmptyView("Nothing starred yet", "Tap the ☆ on any show to keep it here and get a ping when new episodes drop.")
        } else {
            when (val s = state) {
                Load.Loading -> LoadingView()
                is Load.Error -> ErrorView(s.message) { vm.loadWatchlist(watch.ids) }
                is Load.Ready -> {
                    val list = s.data.filter { it.id in watch.ids }
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(list, key = { it.id }) { a ->
                            AnimeRow(
                                a,
                                headline = airingHeadline(a, now),
                                headlineColor = if (a.nextAiringAt != null) Space.Cyan else Space.Muted,
                                onClick = { open(a.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
