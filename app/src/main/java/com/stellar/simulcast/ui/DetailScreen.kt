package com.stellar.simulcast.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.stellar.simulcast.data.AniList
import com.stellar.simulcast.data.AnimeDetail
import com.stellar.simulcast.data.Load
import com.stellar.simulcast.data.attempt

@Composable
fun DetailScreen(id: Int, onBack: () -> Unit) {
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
            is Load.Ready -> DetailBody(s.data, now) { url -> runCatching { uri.openUri(url) } }
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
private fun DetailBody(d: AnimeDetail, now: Long, openUrl: (String) -> Unit) {
    val a = d.anime
    val watch = LocalWatch.current
    val starred = a.id in watch.ids

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
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

        // Titles
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(a.title, color = Space.Text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                a.romaji?.let { Text(it, color = Space.Muted, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(10.dp))
                val chips = listOfNotNull(
                    formatLabel(a.format),
                    statusLabel(a.status),
                    a.episodes?.let { "$it eps" },
                    d.duration?.let { "$it min" },
                    a.score?.let { "★ $it%" },
                    if (d.season != null && d.seasonYear != null) "${pretty(d.season)} ${d.seasonYear}" else null,
                    d.studios.firstOrNull(),
                    pretty(d.source),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { InfoChip(it) }
                }
            }
        }

        // Next episode
        item {
            val at = a.nextAiringAt
            val ep = a.nextEpisode
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).neonPanel().padding(14.dp)
            ) {
                if (at != null && ep != null) {
                    Text("NEXT EPISODE", color = Space.Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 2.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("EP $ep", color = Space.Cyan, fontFamily = FontFamily.Monospace, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Text(countdown(at - now), color = Space.Magenta, fontFamily = FontFamily.Monospace, fontSize = 16.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Text(dateTime(at), color = Space.Text)
                    val later = d.upcoming.filter { it.first != ep }.take(3)
                    if (later.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        later.forEach { (e, t) ->
                            Text("EP $e · ${dateTime(t)}", color = Space.Muted, fontSize = 13.sp)
                        }
                    }
                } else {
                    Text("NO EPISODE SCHEDULED", color = Space.Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 2.sp)
                    Text(
                        when (a.status) {
                            "FINISHED" -> "This show has finished airing."
                            "NOT_YET_RELEASED" -> "No air date announced yet."
                            else -> "AniList has no upcoming episode listed."
                        },
                        color = Space.Text,
                    )
                }
            }
        }

        // Watchlist button
        item {
            val colors = if (starred) ButtonDefaults.buttonColors(containerColor = Space.Magenta, contentColor = Space.Bg)
            else ButtonDefaults.buttonColors(containerColor = Space.Cyan, contentColor = Space.Bg)
            Button(
                onClick = { watch.toggle(a.id) },
                colors = colors,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Icon(if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (starred) "On your watchlist" else "Add to watchlist", fontWeight = FontWeight.Bold)
            }
        }

        // Where to watch
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                SectionLabel("Watch online")
                if (d.links.isEmpty()) {
                    Text("No official streams listed on AniList yet.", color = Space.Muted)
                } else {
                    d.links.forEach { link ->
                        OutlinedButton(
                            onClick = { openUrl(link.url) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Space.Cyan)
                            Spacer(Modifier.width(8.dp))
                            Text(link.site, color = Space.Text, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = Space.Muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                a.siteUrl?.let { url ->
                    OutlinedButton(onClick = { openUrl(url) }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text("Open on AniList", color = Space.Muted)
                    }
                }
            }
        }

        // Synopsis
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                if (a.genres.isNotEmpty()) {
                    SectionLabel("Genres")
                    Text(a.genres.joinToString(" · "), color = Space.Cyan)
                    Spacer(Modifier.height(14.dp))
                }
                SectionLabel("Synopsis")
                Text(d.description, color = Space.Text, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.navigationBarsPadding())
            }
        }
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
