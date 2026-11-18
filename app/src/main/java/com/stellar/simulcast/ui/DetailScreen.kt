package com.stellar.simulcast.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.stellar.simulcast.data.AniList
import com.stellar.simulcast.data.AnimeDetail
import com.stellar.simulcast.data.Anime
import com.stellar.simulcast.data.ListStatus
import com.stellar.simulcast.data.Lists
import androidx.compose.foundation.layout.widthIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellar.simulcast.data.EpisodeInfo
import com.stellar.simulcast.data.Load
import com.stellar.simulcast.data.MangaDex
import com.stellar.simulcast.data.Related
import com.stellar.simulcast.data.Settings
import com.stellar.simulcast.data.TimeText
import com.stellar.simulcast.data.Watchlist
import com.stellar.simulcast.data.attempt

@Composable
fun DetailScreen(id: Int, onBack: () -> Unit, open: (Int) -> Unit, openManga: (String) -> Unit, openStudio: (Int) -> Unit) {
    var reload by remember { mutableIntStateOf(0) }
    val state by produceState<Load<AnimeDetail>>(Load.Loading, id, reload) {
        value = Load.Loading
        value = attempt { AniList.detail(id) }
    }
    val uri = LocalUriHandler.current
    val now = rememberNow()

    Box(
        Modifier
            .fillMaxSize()
            .background(Space.Bg)
            // Swallow taps so they don't reach the list underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Starfield()
        when (val s = state) {
            Load.Loading -> LoadingView()
            is Load.Error -> ErrorView(s.message) { reload++ }
            is Load.Ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                DetailBody(s.data, now, open, openManga, openStudio) { url -> runCatching { uri.openUri(url) } }
            }
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color(0x99000000)),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Space.Text)
        }
    }
}

@Composable
private fun DetailBody(d: AnimeDetail, now: Long, open: (Int) -> Unit, openManga: (String) -> Unit, openStudio: (Int) -> Unit, openUrl: (String) -> Unit) {
    val a = d.anime
    val ctx = LocalContext.current
    val watch = LocalWatch.current
    val lang = Settings.titleLang
    val starred = a.id in watch.ids
    val hidden = a.id in watch.hidden
    val watched = watch.progress[a.id] ?: 0
    var showQr by remember { mutableStateOf(false) }
    var allEpisodes by remember { mutableStateOf(false) }

    if (showQr) QrDialog(a) { showQr = false }

    // Manga on AniList -> the same series on MangaDex, so it opens in the in-app reader.
    val scope = rememberCoroutineScope()
    var finding by remember { mutableStateOf(false) }
    val sourceManga = d.related.firstOrNull { it.relation.startsWith("Source") && it.relation.endsWith("(manga)") }
        ?: d.related.firstOrNull { it.relation.endsWith("(manga)") }
    val readManga: (Related) -> Unit = { r ->
        if (!finding) {
            finding = true
            Toast.makeText(ctx, "Looking for it on MangaDex…", Toast.LENGTH_SHORT).show()
            scope.launch {
                val titles = listOfNotNull(r.anime.romaji, r.anime.english)
                val found = runCatching { MangaDex.findByAniList(r.anime.id, titles) }.getOrNull()
                finding = false
                if (found != null) openManga(found.id)
                else {
                    Toast.makeText(ctx, "Not on MangaDex, opening AniList instead", Toast.LENGTH_SHORT).show()
                    openUrl(showUrl(r.anime))
                }
            }
        }
    }

    val novel = a.isNovel || a.type == "MANGA"
    LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        // Banner + poster
        item {
            Box(Modifier.fillMaxWidth().height(270.dp)) {
                AsyncImage(
                    model = d.banner ?: a.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(210.dp),
                )
                Box(
                    Modifier.fillMaxWidth().height(210.dp)
                        .background(Brush.verticalGradient(listOf(Color(0x4005060F), Color(0xF205060F))))
                )
                Poster(
                    a.cover,
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp)
                        .size(width = 108.dp, height = 154.dp)
                        .border(1.dp, Space.Cyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                )
            }
        }

        // Titles + info chips
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(a.name(lang), color = Space.Text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                a.altName(lang)?.let { Text(it, color = Space.Muted, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(10.dp))
                val trend = d.scoreChange?.takeIf { it != 0 }?.let { if (it > 0) " ▲$it" else " ▼${-it}" } ?: ""
                val chips = listOfNotNull(
                    formatLabel(a.format),
                    statusLabel(a.status),
                    a.episodes?.let { "$it eps" },
                    d.duration?.let { "$it min" },
                    a.score?.let { "★ $it%$trend" },
                    if (d.season != null && d.seasonYear != null) "${pretty(d.season)} ${d.seasonYear}" else null,
                    d.source?.let { "Based on: ${pretty(it)}" },
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { InfoChip(it) }
                    val studio = d.studios.firstOrNull()
                    if (studio != null && d.studioId != null) {
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(Space.Cyan.copy(alpha = 0.15f))
                                .border(1.dp, Space.Cyan.copy(alpha = 0.6f), RoundedCornerShape(50))
                                .clickable { openStudio(d.studioId) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) { Text("$studio ›", color = Space.Cyan, fontSize = 12.sp, maxLines = 1) }
                    }
                }
                if (trend.isNotEmpty()) {
                    Text(
                        "Score ${if ((d.scoreChange ?: 0) > 0) "up" else "down"} ${kotlin.math.abs(d.scoreChange ?: 0)} points over the last month",
                        color = Space.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        // Action buttons
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionTile(
                    if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    if (starred) "Starred" else "Star", if (starred) Space.Magenta else Space.Cyan, Modifier.weight(1f),
                ) { watch.toggle(a.id) }
                ActionTile(Icons.Rounded.SmartDisplay, "Trailer", Space.Cyan, Modifier.weight(1f), enabled = d.trailerUrl != null && !novel) {
                    d.trailerUrl?.let(openUrl)
                }
                ActionTile(Icons.Rounded.Share, "Share", Space.Cyan, Modifier.weight(1f)) { shareShow(ctx, a) }
                ActionTile(Icons.Rounded.QrCode2, "QR", Space.Cyan, Modifier.weight(1f)) { showQr = true }
                ActionTile(
                    if (hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    if (hidden) "Unhide" else "Hide", Space.Muted, Modifier.weight(1f),
                ) {
                    Watchlist.toggleHidden(a.id)
                    Toast.makeText(
                        ctx,
                        if (hidden) "Back in your lists" else "Hidden from Schedule, Just aired and Browse",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

        if (sourceManga != null) {
            item {
                OutlinedButton(
                    onClick = { readManga(sourceManga) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, tint = Space.Magenta)
                    Spacer(Modifier.width(8.dp))
                    Text("Read the manga", color = Space.Text, modifier = Modifier.weight(1f))
                    Text(sourceManga.anime.name(lang), color = Space.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                }
            }
        }

        // Next episode, breaks, finish estimate, calendar
        item {
            if (novel) NovelPanel(d) else NextEpisodePanel(d, now)
        }

        // Your list + score
        item { ListPanel(a) }

        // Progress
        item { ProgressPanel(d, watched, unit = if (novel) "volumes" else "episodes") }

        // Where to watch
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                SectionLabel("Watch online")
                val links = if (Settings.display.englishLinksOnly) {
                    d.links.filter { it.language == null || it.language.equals("English", ignoreCase = true) }
                } else d.links
                if (links.isEmpty()) {
                    Text(
                        if (d.links.isNotEmpty()) "No English links listed. Turn off \"English links only\" in Settings to see all."
                        else "No official streams listed on AniList yet.",
                        color = Space.Muted,
                    )
                } else {
                    links.forEach { link ->
                        OutlinedButton(
                            onClick = { openUrl(link.url) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Space.Cyan)
                            Spacer(Modifier.width(8.dp))
                            Text(link.site, color = Space.Text, modifier = Modifier.weight(1f))
                            if (isFreeService(link.site)) {
                                Text("FREE", color = Space.Bg, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Space.Magenta).padding(horizontal = 5.dp, vertical = 1.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            link.language?.let { Text(it, color = Space.Muted, fontSize = 12.sp) }
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = Space.Muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                OutlinedButton(onClick = { openUrl(showUrl(a)) }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("Open on AniList", color = Space.Muted)
                }
            }
        }

        // Episode list
        if (d.episodeList.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { SectionLabel("Episodes") }
                    if (d.episodeList.size > 6) {
                        Text(
                            if (allEpisodes) "Show less" else "Show all ${d.episodeList.size}",
                            color = Space.Cyan, fontSize = 13.sp,
                            modifier = Modifier.clickable { allEpisodes = !allEpisodes }.padding(bottom = 8.dp),
                        )
                    }
                }
            }
            val shown = if (allEpisodes) d.episodeList else episodeWindow(d.episodeList, watched)
            items(shown, key = { "ep${it.number}" }) { ep ->
                EpisodeRow(ep, a, watched, now) { n ->
                    Watchlist.setProgress(a.id, if (n == watched) n - 1 else n)
                }
            }
        }

        // Recommendations
        if (d.recommendations.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 14.dp)) {
                    Box(Modifier.padding(horizontal = 16.dp)) { SectionLabel("If you liked this") }
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(d.recommendations, key = { "rec" + it.id }) { r ->
                            Column(Modifier.width(104.dp).clickable { open(r.id) }) {
                                Poster(r.cover, Modifier.fillMaxWidth().aspectRatio(0.7f))
                                Text(r.name(lang), color = Space.Text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        // Cast
        if (d.cast.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 14.dp)) {
                    Box(Modifier.padding(horizontal = 16.dp)) { SectionLabel("Characters & voice cast") }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(d.cast) { c ->
                            Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                AsyncImage(
                                    model = c.characterImage, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(72.dp).clip(CircleShape).background(Space.Surface)
                                        .border(1.dp, (if (c.role == "MAIN") Space.Cyan else Space.Muted).copy(alpha = 0.6f), CircleShape),
                                )
                                Text(c.character, color = Space.Text, fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                                c.voiceActor?.let {
                                    Text(it, color = Space.Muted, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Related shows + source material
        if (d.related.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 14.dp)) {
                    Box(Modifier.padding(horizontal = 16.dp)) { SectionLabel("Related") }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(d.related, key = { it.anime.id }) { r ->
                            val isManga = r.relation.endsWith("(manga)")
                            Column(
                                Modifier.width(104.dp).clickable {
                                    if (isManga) readManga(r) else open(r.anime.id)
                                }
                            ) {
                                Poster(r.anime.cover, Modifier.fillMaxWidth().aspectRatio(0.7f))
                                Text(r.relation.uppercase(), color = Space.Magenta, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                                Text(r.anime.name(lang), color = Space.Text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // Genres + synopsis
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                if (a.genres.isNotEmpty()) {
                    SectionLabel("Genres")
                    Text(a.genres.joinToString(" · "), color = Space.Cyan)
                    Spacer(Modifier.height(14.dp))
                }
                SectionLabel("Synopsis")
                SpoilerText(d.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

/** Services with free, legal, ad-supported anime. Only shown when AniList lists the show there. */
private val FREE_SERVICES = listOf("youtube", "tubi", "pluto", "retrocrunch", "bilibili")

private fun isFreeService(site: String): Boolean = FREE_SERVICES.any { site.lowercase().contains(it) }

/** The few episodes around where you are, so the list stays short. */
private fun episodeWindow(list: List<EpisodeInfo>, watched: Int): List<EpisodeInfo> {
    val idx = list.indexOfFirst { it.number > watched }.let { if (it < 0) list.size - 1 else it }
    val start = (idx - 2).coerceAtLeast(0)
    return list.subList(start, (start + 6).coerceAtMost(list.size))
}

@Composable
private fun NextEpisodePanel(d: AnimeDetail, now: Long) {
    val a = d.anime
    val ctx = LocalContext.current
    val at = a.nextAiringAt
    val ep = a.nextEpisode
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).neonPanel().padding(14.dp)) {
        if (at != null && ep != null) {
            val streamAt = TimeText.streamAt(at, a)
            Text(
                if (Settings.display.streamDelayMin > 0) "NEXT EPISODE · STREAMING" else "NEXT EPISODE",
                color = Space.Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 2.sp,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text("EP $ep", color = Space.Cyan, fontFamily = FontFamily.Monospace, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(countdown(streamAt - now), color = Space.Magenta, fontFamily = FontFamily.Monospace, fontSize = 16.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
            Text(TimeText.dateTime(streamAt), color = Space.Text)

            if (a.onBreak(now)) {
                Notice("Taking a break this week. Episode $ep is back on ${TimeText.date(streamAt)}.")
            } else {
                upcomingGap(d.episodeList, now)?.let { after ->
                    Notice("Heads up: no episode the week after EP $after.")
                }
            }
            finishEstimate(d)?.let { end ->
                val left = (a.episodes ?: 0) - (a.airedCount ?: 0)
                Text(
                    "$left episodes left · ends around ${TimeText.date(TimeText.streamAt(end, a))}",
                    color = Space.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
            val later = d.episodeList.filter { (it.airingAt ?: 0) > at }.take(3)
            if (later.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                later.forEach { e ->
                    Text("EP ${e.number} · ${TimeText.dateTime(TimeText.streamAt(e.airingAt ?: 0, a))}", color = Space.Muted, fontSize = 13.sp)
                }
            }
            OutlinedButton(
                onClick = {
                    if (!addToCalendar(ctx, a, d.duration)) {
                        Toast.makeText(ctx, "No calendar app found", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                Icon(Icons.Rounded.EventAvailable, contentDescription = null, tint = Space.Cyan)
                Spacer(Modifier.width(8.dp))
                val repeats = a.episodes?.let { it - ep + 1 }?.takeIf { it > 1 }
                Text(if (repeats != null) "Add to calendar (weekly, $repeats eps)" else "Add to calendar", color = Space.Text)
            }
        } else {
            Text("NO EPISODE SCHEDULED", color = Space.Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 2.sp)
            Text(
                when (a.status) {
                    "FINISHED" -> "This show has finished airing${a.episodes?.let { ", all $it episodes are out" } ?: ""}."
                    "NOT_YET_RELEASED" -> "No air date announced yet."
                    "HIATUS" -> "This show is on hiatus."
                    else -> "AniList has no upcoming episode listed."
                },
                color = Space.Text,
            )
        }
    }
}

@Composable
private fun NovelPanel(d: AnimeDetail) {
    val a = d.anime
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).neonPanel().padding(14.dp)) {
        Text(if (a.isNovel) "LIGHT NOVEL" else "MANGA", color = Space.Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 2.sp)
        Text(
            listOfNotNull(statusLabel(a.status), a.volumes?.let { "$it volumes" }, a.chapters?.let { "$it chapters" }).joinToString(" · "),
            color = Space.Text,
        )
        Text("Tracking only: log the volumes you've read below. Buy or read it from the official links on AniList.",
            color = Space.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Plan to watch / Watching / … and your own 1–10 score. Saved on this phone only. */
@Composable
private fun ListPanel(a: Anime) {
    val statuses by Lists.animeStatus.collectAsStateWithLifecycle()
    val scores by Lists.animeScore.collectAsStateWithLifecycle()
    val status = statuses[a.id]
    val score = scores[a.id]
    val novel = a.isNovel || a.type == "MANGA"
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).neonPanel().padding(14.dp)) {
        Text("YOUR LIST", color = Space.Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        ChipGrid(
            ListStatus.entries.map { it.name },
            setOfNotNull(status?.name),
            label = { n -> ListStatus.valueOf(n).let { if (novel) it.manga else it.anime } },
            perRow = 3,
        ) { n ->
            val picked = ListStatus.valueOf(n)
            Lists.setAnimeStatus(a.id, if (picked == status) null else picked)
        }
        Spacer(Modifier.height(10.dp))
        Text(if (score != null) "Your score: $score / 10" else "Your score", color = Space.Text)
        Spacer(Modifier.height(6.dp))
        ChipGrid((1..10).map { it.toString() }, setOfNotNull(score?.toString()), perRow = 5) { n ->
            val v = n.toInt()
            Lists.setAnimeScore(a.id, if (v == score) null else v)
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text, color = Space.Magenta, fontSize = 13.sp,
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Space.Magenta.copy(alpha = 0.10f))
            .padding(8.dp),
    )
}

/** First upcoming episode followed by a gap of more than 8 days. */
private fun upcomingGap(list: List<EpisodeInfo>, now: Long): Int? {
    val upcoming = list.filter { (it.airingAt ?: 0) > now }
    return upcoming.zipWithNext().firstOrNull { (x, y) ->
        val ax = x.airingAt ?: return@firstOrNull false
        val ay = y.airingAt ?: return@firstOrNull false
        ay - ax > 8 * 86400
    }?.first?.number
}

/** Air time of the final episode: exact if scheduled, otherwise estimated weekly. */
private fun finishEstimate(d: AnimeDetail): Long? {
    val a = d.anime
    val total = a.episodes ?: return null
    if (a.status != "RELEASING") return null
    d.episodeList.firstOrNull { it.number == total }?.airingAt?.let { return it }
    val last = d.episodeList.filter { it.airingAt != null }.maxByOrNull { it.number } ?: return null
    val lastAt = last.airingAt ?: return null
    return if (last.number >= total) lastAt else lastAt + (total - last.number) * 7L * 86400
}

@Composable
private fun ProgressPanel(d: AnimeDetail, watched: Int, unit: String) {
    val a = d.anime
    val aired = a.airedCount
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).neonPanel(Space.Magenta).padding(14.dp)) {
        Text("YOUR PROGRESS", color = Space.Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 2.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Watchlist.setProgress(a.id, watched - 1) }, enabled = watched > 0) {
                Icon(Icons.Rounded.Remove, contentDescription = "One less", tint = Space.Cyan)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$watched" + ((if (unit == "volumes") a.volumes else a.episodes)?.let { " / $it" } ?: "") + " $unit",
                    color = Space.Text, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                )
                val badge = progressBadge(a, if (watched > 0) watched else null)
                Text(
                    badge ?: "Mark episodes as you watch",
                    color = if (badge?.endsWith("behind") == true) Space.Magenta else Space.Muted, fontSize = 12.sp,
                )
            }
            IconButton(
                onClick = { Watchlist.setProgress(a.id, watched + 1) },
                enabled = (if (unit == "volumes") a.volumes else a.episodes)?.let { watched < it } ?: true,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "One more", tint = Space.Cyan)
            }
        }
        if (aired != null && watched < aired) {
            Text(
                "Mark caught up (EP $aired)",
                color = Space.Cyan, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable { Watchlist.setProgress(a.id, aired) }.padding(6.dp),
            )
        }
        Text(
            "Saved on this phone only.",
            color = Space.Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun EpisodeRow(ep: EpisodeInfo, anime: Anime, watched: Int, now: Long, onToggle: (Int) -> Unit) {
    val isWatched = ep.number <= watched
    val at = ep.airingAt?.let { TimeText.streamAt(it, anime) }
    val upcoming = at != null && at > now
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !upcoming) { onToggle(ep.number) }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isWatched) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (isWatched) "Watched" else "Not watched",
            tint = if (isWatched) Space.Cyan else Space.Muted.copy(alpha = if (upcoming) 0.3f else 1f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text("EP ${ep.number}", color = Space.Cyan, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.width(52.dp))
        Column(Modifier.weight(1f)) {
            val title = ep.title
            if (title != null) {
                SpoilerText(title, style = MaterialTheme.typography.bodyMedium)
            }
            if (at != null) {
                Text(
                    if (upcoming) "${TimeText.dateTime(at)} · ${countdown(at - now)}" else TimeText.date(at),
                    color = Space.Muted, fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .neonPanel(tint)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else Space.Muted.copy(alpha = 0.4f))
        Text(label, color = if (enabled) Space.Text else Space.Muted.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), color = Space.Muted, fontFamily = FontFamily.Monospace,
        fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun InfoChip(text: String) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(Space.Card)
            .border(1.dp, Space.Cyan.copy(alpha = 0.35f), shape)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = Space.Text, fontSize = 12.sp, maxLines = 1)
    }
}
