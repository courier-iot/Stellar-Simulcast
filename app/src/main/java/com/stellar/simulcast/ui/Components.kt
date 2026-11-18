package com.stellar.simulcast.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.stellar.simulcast.data.Anime
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random

// ---------- watchlist access for any screen ----------

data class WatchActions(val ids: Set<Int>, val toggle: (Int) -> Unit)

val LocalWatch = compositionLocalOf { WatchActions(emptySet()) {} }

// ---------- time helpers ----------

@Composable
fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis() / 1000) {
        while (true) {
            delay(20_000)
            value = System.currentTimeMillis() / 1000
        }
    }
    return now
}

fun countdown(seconds: Long): String {
    if (seconds <= 0) return "out now"
    val d = seconds / 86400
    val h = (seconds % 86400) / 3600
    val m = (seconds % 3600) / 60
    return when {
        d > 0 -> "in ${d}d ${h}h"
        h > 0 -> "in ${h}h ${m}m"
        else -> "in ${maxOf(m, 1L)}m"
    }
}

fun ago(seconds: Long): String {
    val s = maxOf(seconds, 0L)
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60}m ago"
        s < 86400 -> "${s / 3600}h ago"
        else -> "${s / 86400}d ago"
    }
}

private val clockFmt = DateTimeFormatter.ofPattern("h:mm a")
private val dateTimeFmt = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")

fun clock(epoch: Long): String = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).format(clockFmt)
fun dateTime(epoch: Long): String = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).format(dateTimeFmt)

fun formatLabel(f: String?): String? = when (f) {
    null -> null
    "TV" -> "TV"
    "TV_SHORT" -> "TV Short"
    "OVA" -> "OVA"
    "ONA" -> "ONA"
    else -> pretty(f)
}

fun pretty(s: String?): String? = s?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() }

fun statusLabel(s: String?): String = when (s) {
    "RELEASING" -> "Airing"
    "FINISHED" -> "Finished"
    "NOT_YET_RELEASED" -> "Not yet aired"
    "CANCELLED" -> "Cancelled"
    "HIATUS" -> "On hiatus"
    else -> "Unknown"
}

fun airingHeadline(a: Anime, now: Long): String {
    val at = a.nextAiringAt
    val ep = a.nextEpisode
    return if (at != null && ep != null) "EP $ep · ${countdown(at - now)}" else statusLabel(a.status)
}

fun metaLine(a: Anime): String = listOfNotNull(
    formatLabel(a.format),
    a.episodes?.let { "$it eps" },
    a.score?.let { "★ $it%" },
).joinToString(" · ")

// ---------- visuals ----------

@Composable
fun Starfield(modifier: Modifier = Modifier) {
    val stars = remember {
        val r = Random(7)
        List(150) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0A0B1E), Space.Bg, Color(0xFF08030F))))
        val glowA = Offset(size.width * 0.95f, size.height * 0.1f)
        drawCircle(
            Brush.radialGradient(listOf(Space.Magenta.copy(alpha = 0.12f), Color.Transparent), glowA, size.width * 0.8f),
            radius = size.width * 0.8f, center = glowA,
        )
        val glowB = Offset(0f, size.height * 0.85f)
        drawCircle(
            Brush.radialGradient(listOf(Space.Cyan.copy(alpha = 0.08f), Color.Transparent), glowB, size.width * 0.9f),
            radius = size.width * 0.9f, center = glowB,
        )
        stars.forEach { (x, y, s) ->
            drawCircle(
                Color.White.copy(alpha = 0.12f + s * 0.5f),
                radius = 0.6f + s * 1.6f,
                center = Offset(x * size.width, y * size.height),
            )
        }
    }
}

private val PanelShape = RoundedCornerShape(14.dp)

fun Modifier.neonPanel(accent: Color = Space.Cyan): Modifier = this
    .clip(PanelShape)
    .background(Space.Card)
    .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.5f), Space.Magenta.copy(alpha = 0.22f))), PanelShape)

@Composable
fun Poster(url: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(Space.Surface),
    )
}

@Composable
fun WatchButton(id: Int) {
    val watch = LocalWatch.current
    val on = id in watch.ids
    IconButton(onClick = { watch.toggle(id) }) {
        Icon(
            if (on) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = if (on) "Remove from watchlist" else "Add to watchlist",
            tint = if (on) Space.Magenta else Space.Muted,
        )
    }
}

@Composable
fun AnimeRow(
    anime: Anime,
    headline: String,
    onClick: () -> Unit,
    headlineColor: Color = Space.Cyan,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .neonPanel(headlineColor)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(anime.cover, Modifier.size(width = 58.dp, height = 82.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                headline, color = headlineColor, fontFamily = FontFamily.Monospace,
                fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            )
            Text(
                anime.title, color = Space.Text, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val meta = metaLine(anime)
            if (meta.isNotEmpty()) {
                Text(meta, color = Space.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
        WatchButton(anime.id)
    }
}

@Composable
fun PosterTile(anime: Anime, now: Long, onClick: () -> Unit) {
    val watch = LocalWatch.current
    Column(Modifier.padding(5.dp).clickable(onClick = onClick)) {
        Box {
            Poster(anime.cover, Modifier.fillMaxWidth().aspectRatio(0.7f))
            if (anime.id in watch.ids) {
                Icon(
                    Icons.Rounded.Star, contentDescription = null, tint = Space.Magenta,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp),
                )
            }
            val at = anime.nextAiringAt
            val ep = anime.nextEpisode
            if (at != null && ep != null) {
                Text(
                    "EP $ep ${countdown(at - now)}",
                    color = Space.Cyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE605060F))))
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                )
            }
        }
        Text(
            anime.title, color = Space.Text, style = MaterialTheme.typography.labelMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun NeonChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        color = if (selected) Space.Bg else Space.Text,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Space.Cyan else Space.Card)
            .border(1.dp, (if (selected) Space.Cyan else Space.Muted).copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
fun ToolbarRow(title: String, onRefresh: (() -> Unit)?, content: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title.uppercase(), color = Space.Muted, fontFamily = FontFamily.Monospace,
            fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f),
        )
        content()
        if (onRefresh != null) {
            IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Refresh", tint = Space.Cyan) }
        }
    }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Space.Cyan)
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SIGNAL LOST", color = Space.Magenta, fontFamily = FontFamily.Monospace, fontSize = 18.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Space.Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) { Text("Retry", color = Space.Cyan) }
    }
}

@Composable
fun EmptyView(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Space.Cyan, fontFamily = FontFamily.Monospace, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, color = Space.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun ListFooter(loading: Boolean, error: String?, hasMore: Boolean, itemCount: Int, onMore: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        when {
            loading -> CircularProgressIndicator(color = Space.Cyan, modifier = Modifier.size(28.dp))
            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, color = Space.Muted, textAlign = TextAlign.Center)
                OutlinedButton(onClick = onMore) { Text("Retry", color = Space.Cyan) }
            }
            hasMore -> {
                androidx.compose.runtime.LaunchedEffect(itemCount) { onMore() }
                CircularProgressIndicator(color = Space.Cyan, modifier = Modifier.size(28.dp))
            }
        }
    }
}
