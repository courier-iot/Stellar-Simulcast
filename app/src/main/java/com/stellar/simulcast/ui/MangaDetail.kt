package com.stellar.simulcast.ui

import android.content.Intent
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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapVert
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stellar.simulcast.data.AniList
import com.stellar.simulcast.data.Anime
import com.stellar.simulcast.data.Chapter
import com.stellar.simulcast.data.Load
import com.stellar.simulcast.data.Manga
import com.stellar.simulcast.data.MangaDex
import com.stellar.simulcast.data.MangaLibrary
import com.stellar.simulcast.data.Settings
import com.stellar.simulcast.data.attempt

private data class MangaPage(val manga: Manga, val chapters: List<Chapter>)

@Composable
fun MangaDetailScreen(id: String, onBack: () -> Unit, read: ReadAction, openAnime: (Int) -> Unit) {
    var reload by remember { mutableIntStateOf(0) }
    val state by produceState<Load<MangaPage>>(Load.Loading, id, reload) {
        value = Load.Loading
        value = attempt { MangaPage(MangaDex.manga(id), MangaDex.chapters(id)) }
    }
    val uri = LocalUriHandler.current

    Box(
        Modifier
            .fillMaxSize()
            .background(Space.Bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Starfield()
        when (val s = state) {
            Load.Loading -> LoadingView()
            is Load.Error -> ErrorView(s.message) { reload++ }
            is Load.Ready -> MangaBody(s.data, read, openAnime) { url -> runCatching { uri.openUri(url) } }
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(8.dp).clip(CircleShape).background(Color(0x99000000)),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Space.Text)
        }
    }
}

@Composable
private fun MangaBody(page: MangaPage, read: ReadAction, openAnime: (Int) -> Unit, openUrl: (String) -> Unit) {
    val m = page.manga
    val ctx = LocalContext.current
    val follows by MangaLibrary.follows.collectAsStateWithLifecycle()
    val marks by MangaLibrary.marks.collectAsStateWithLifecycle()
    val mark = if (Settings.manga.trackReading) marks[m.id] else null
    var newestFirst by remember { mutableStateOf(true) }
    val readable = page.chapters.filter { it.externalUrl == null && it.pages > 0 }

    // Anime adaptations, via the AniList ID MangaDex has on file.
    val anime by produceState<List<Anime>>(emptyList(), m.aniListId) {
        val al = m.aniListId ?: return@produceState
        value = runCatching { AniList.animeForManga(al) }.getOrDefault(emptyList())
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(250.dp)) {
                AsyncImage(
                    model = m.cover, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                )
                Box(Modifier.fillMaxWidth().height(190.dp).background(Brush.verticalGradient(listOf(Color(0x8005060F), Color(0xF505060F)))))
                Poster(
                    m.cover,
                    Modifier.align(Alignment.BottomStart).padding(start = 16.dp).size(108.dp, 154.dp)
                        .border(1.dp, Space.Cyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                )
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(m.title, color = Space.Text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                m.altTitle?.let { Text(it, color = Space.Muted, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(10.dp))
                val chips = listOfNotNull(
                    pretty(m.status), m.year?.toString(), m.author,
                    m.contentRating?.let { pretty(it) },
                    "${readable.distinctBy { it.number }.size} chapters",
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { MangaChip(it) }
                }
                if (m.tags.isNotEmpty()) {
                    Text(m.tags.take(8).joinToString(" · "), color = Space.Cyan, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        // Actions
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val following = m.id in follows
                MangaAction(if (following) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, if (following) "Following" else "Follow",
                    if (following) Space.Magenta else Space.Cyan, Modifier.weight(1f)) { MangaLibrary.toggleFollow(m.id) }
                val resume = mark?.let { mk -> readable.firstOrNull { it.id == mk.chapterId } }
                val start = resume ?: readable.firstOrNull()
                MangaAction(Icons.AutoMirrored.Rounded.MenuBook, if (resume != null) "Continue" else "Start", Space.Cyan, Modifier.weight(1f),
                    enabled = start != null) {
                    start?.let { read(m.id, it.id, if (resume != null) mark?.page ?: 0 else 0) }
                }
                MangaAction(Icons.Rounded.Share, "Share", Space.Cyan, Modifier.weight(1f)) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${m.title}\nhttps://mangadex.org/title/${m.id}")
                    }
                    ctx.startActivity(Intent.createChooser(send, "Share ${m.title}"))
                }
            }
            if (mark != null) {
                Text(
                    "You're on Ch. ${mark.chapter ?: "?"}, page ${mark.page + 1}",
                    color = Space.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        // Watch the anime
        if (anime.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 10.dp)) {
                    MangaLabel("Watch the anime", Modifier.padding(horizontal = 16.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(anime, key = { it.id }) { a ->
                            Column(Modifier.width(104.dp).clickable { openAnime(a.id) }) {
                                Poster(a.cover, Modifier.fillMaxWidth().aspectRatio(0.7f))
                                Text(a.name(Settings.titleLang), color = Space.Text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        // Official / database links
        if (m.links.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    MangaLabel("Official & other links")
                    m.links.forEach { (label, url) ->
                        OutlinedButton(onClick = { openUrl(url) }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(label, color = Space.Text, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = Space.Muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Description
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                MangaLabel("About")
                SpoilerText(m.description, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Chapters
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { MangaLabel("Chapters") }
                IconButton(onClick = { newestFirst = !newestFirst }) {
                    Icon(Icons.Rounded.SwapVert, contentDescription = if (newestFirst) "Oldest first" else "Newest first", tint = Space.Cyan)
                }
            }
        }
        val list = if (newestFirst) page.chapters.asReversed() else page.chapters
        if (list.isEmpty()) {
            item {
                Text(
                    "No chapters in your languages. Licensed series often have none on MangaDex. Check the official links above.",
                    color = Space.Muted, modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        items(list, key = { it.id }) { c ->
            val done = mark != null && c.num >= 0 && c.num < mark.num
            val current = mark?.chapterId == c.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { if (c.externalUrl != null) openUrl(c.externalUrl) else read(m.id, c.id, if (current) mark?.page ?: 0 else 0) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        chapterLine(c), color = if (done) Space.Muted else Space.Text,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(c.group ?: "No group", c.language.uppercase(), if (c.externalUrl != null) "Official site" else null)
                            .joinToString(" · "),
                        color = Space.Muted, fontSize = 12.sp, maxLines = 1,
                    )
                }
                when {
                    c.externalUrl != null -> Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = Space.Muted, modifier = Modifier.size(18.dp))
                    current -> Text("p.${(mark?.page ?: 0) + 1}", color = Space.Cyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    done -> Icon(Icons.Rounded.CheckCircle, null, tint = Space.Cyan.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
            }
        }
        item {
            Text(
                "Chapters stream from MangaDex. Translations are by the scanlation groups credited on each chapter.",
                color = Space.Muted, fontSize = 11.sp, modifier = Modifier.padding(16.dp).navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun MangaAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier.neonPanel(tint).clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (enabled) tint else Space.Muted.copy(alpha = 0.4f))
        Text(label, color = if (enabled) Space.Text else Space.Muted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun MangaLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = Space.Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 2.sp, modifier = modifier.padding(bottom = 6.dp))
}

@Composable
private fun MangaChip(text: String) {
    val shape = RoundedCornerShape(50)
    Box(Modifier.clip(shape).background(Space.Card).border(1.dp, Space.Cyan.copy(alpha = 0.35f), shape).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text, color = Space.Text, fontSize = 12.sp, maxLines = 1)
    }
}
