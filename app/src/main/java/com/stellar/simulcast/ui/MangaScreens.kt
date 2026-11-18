package com.stellar.simulcast.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellar.simulcast.data.Chapter
import com.stellar.simulcast.data.Load
import com.stellar.simulcast.data.Manga
import com.stellar.simulcast.data.MangaBrowse
import com.stellar.simulcast.data.MangaLibrary
import com.stellar.simulcast.data.Settings
import com.stellar.simulcast.data.ListStatus
import com.stellar.simulcast.data.Lists
import com.stellar.simulcast.data.NovelBrowse
import com.stellar.simulcast.data.Watchlist
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** Opens the reader: manga ID, chapter ID, page. */
typealias ReadAction = (String, String, Int) -> Unit

// ---------- shared bits ----------

@Composable
fun FollowButton(id: String) {
    val follows by MangaLibrary.follows.collectAsStateWithLifecycle()
    val ask = LocalAskNotify.current
    val on = id in follows
    IconButton(onClick = { if (MangaLibrary.toggleFollow(id)) ask() }) {
        Icon(
            if (on) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
            contentDescription = if (on) "Unfollow" else "Follow",
            tint = if (on) Space.Magenta else Space.Muted,
        )
    }
}

fun mangaMeta(m: Manga): String = listOfNotNull(
    pretty(m.status),
    m.year?.toString(),
    m.author,
    m.contentRating?.takeIf { it != "safe" }?.let { pretty(it) },
).joinToString(" · ")

fun chapterLine(c: Chapter): String = listOfNotNull(
    c.volume?.let { "Vol. $it" },
    c.label,
    c.title,
).joinToString(" · ")

@Composable
fun MangaRow(m: Manga, headline: String, headlineColor: androidx.compose.ui.graphics.Color = Space.Cyan, onClick: () -> Unit) {
    val compact = Settings.display.compact
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = if (compact) 3.dp else 5.dp)
            .neonPanel(headlineColor)
            .clickable(onClick = onClick)
            .padding(if (compact) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(m.cover, if (compact) Modifier.size(38.dp, 54.dp) else Modifier.size(58.dp, 82.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(headline, color = headlineColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(m.title, color = Space.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            val statuses by Lists.mangaStatus.collectAsStateWithLifecycle()
            val scores by Lists.mangaScore.collectAsStateWithLifecycle()
            val mine = listOfNotNull(statuses[m.id]?.manga, scores[m.id]?.let { "you ★$it" })
            val meta = (mine + if (compact) emptyList() else listOf(mangaMeta(m))).filter { it.isNotEmpty() }.joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, color = Space.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        FollowButton(m.id)
    }
}

@Composable
private fun MangaTile(m: Manga, onClick: () -> Unit) {
    val follows by MangaLibrary.follows.collectAsStateWithLifecycle()
    Column(Modifier.padding(5.dp).clickable(onClick = onClick)) {
        Box {
            Poster(m.cover, Modifier.fillMaxWidth().aspectRatio(0.7f))
            if (m.id in follows) {
                Icon(Icons.Rounded.Bookmark, null, tint = Space.Magenta, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp))
            }
        }
        Text(m.title, color = Space.Text, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Shown on every manga list: MangaDex asks apps to credit them. */
@Composable
private fun Credit() {
    Text(
        "Manga data and chapters from MangaDex · translations by their scanlation groups",
        color = Space.Muted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

// ---------- Latest ----------

@Composable
fun MangaLatestScreen(vm: MangaViewModel, openManga: (String) -> Unit, read: ReadAction, openUrl: (String) -> Unit) {
    val state by vm.latest.collectAsStateWithLifecycle()
    val follows by MangaLibrary.follows.collectAsStateWithLifecycle()
    val now = rememberNow()
    LaunchedEffect(Unit) { if (vm.latest.value.items.isEmpty()) vm.loadMoreLatest() }

    Column(Modifier.fillMaxSize()) {
        ToolbarRow("New chapters", onRefresh = { vm.refreshLatest() })
        when {
            state.items.isEmpty() && state.loading -> LoadingView()
            state.items.isEmpty() && state.error != null -> ErrorView(state.error ?: "") { vm.loadMoreLatest() }
            state.items.isEmpty() && !state.hasMore -> EmptyView("Nothing new", "No recent chapters in your languages.")
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
                item { Credit() }
                items(state.items, key = { it.chapter.id }) { lc ->
                    val c = lc.chapter
                    MangaRow(
                        lc.manga,
                        headline = listOfNotNull(c.label, c.group, ago(now - c.publishAt)).joinToString(" · "),
                        headlineColor = if (lc.manga.id in follows) Space.Magenta else Space.Cyan,
                    ) {
                        when {
                            c.externalUrl != null -> openUrl(c.externalUrl)
                            else -> read(lc.manga.id, c.id, 0)
                        }
                    }
                }
                item {
                    ListFooter(state.loading, state.error, state.hasMore, state.items.size) { vm.loadMoreLatest() }
                }
            }
        }
    }
}

// ---------- Browse ----------

@Composable
fun MangaBrowseScreen(vm: MangaViewModel, openManga: (String) -> Unit, openAnime: (Int) -> Unit) {
    val mode by vm.browseMode.collectAsStateWithLifecycle()
    val state by vm.browse.collectAsStateWithLifecycle()
    val novelMode by vm.novelMode.collectAsStateWithLifecycle()
    val novels by vm.novels.collectAsStateWithLifecycle()
    val now = rememberNow()
    LaunchedEffect(Unit) { if (vm.browse.value.items.isEmpty()) vm.loadMoreBrowse() }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(MangaBrowse.entries.toList()) { m ->
                    NeonChip(m.label, novelMode == null && m == mode) { vm.setNovelMode(null); vm.setBrowseMode(m) }
                }
                items(NovelBrowse.entries.toList()) { n ->
                    NeonChip(n.label, novelMode == n) { vm.setNovelMode(n) }
                }
            }
            IconButton(
                onClick = {
                    if (novelMode != null) novels.items.randomOrNull()?.let { openAnime(it.id) }
                    else state.items.randomOrNull()?.let { openManga(it.id) }
                },
                enabled = if (novelMode != null) novels.items.isNotEmpty() else state.items.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.Casino, contentDescription = "Pick something for me", tint = Space.Magenta)
            }
        }
        if (novelMode != null) {
            when {
                novels.items.isEmpty() && novels.loading -> LoadingView()
                novels.items.isEmpty() && novels.error != null -> ErrorView(novels.error ?: "") { vm.loadMoreNovels() }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(108.dp),
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(novels.items, key = { "n" + it.id }) { a -> PosterTile(a, now) { openAnime(a.id) } }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            ListFooter(novels.loading, novels.error, novels.hasMore, novels.items.size) { vm.loadMoreNovels() }
                            Text("Light novel info from AniList. Tracking only: read them from official publishers.",
                                color = Space.Muted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        }
                    }
                }
            }
            return@Column
        }
        when {
            state.items.isEmpty() && state.loading -> LoadingView()
            state.items.isEmpty() && state.error != null -> ErrorView(state.error ?: "") { vm.loadMoreBrowse() }
            state.items.isEmpty() && !state.hasMore -> EmptyView("Nothing here", "Try another list or check your content settings.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(108.dp),
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.items, key = { it.id }) { m -> MangaTile(m) { openManga(m.id) } }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        ListFooter(state.loading, state.error, state.hasMore, state.items.size) { vm.loadMoreBrowse() }
                        Credit()
                    }
                }
            }
        }
    }
}

// ---------- Search ----------

@Composable
fun MangaSearchScreen(vm: MangaViewModel, openManga: (String) -> Unit, openAnime: (Int) -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val state by vm.search.collectAsStateWithLifecycle()
    val novelsOn by vm.searchNovels.collectAsStateWithLifecycle()
    val novelState by vm.novelSearch.collectAsStateWithLifecycle()
    val now = rememberNow()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.setQuery(it) },
            singleLine = true,
            placeholder = { Text(if (novelsOn) "Search light novels" else "Search manga", color = Space.Muted) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Space.Cyan) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Rounded.Close, "Clear", tint = Space.Muted) }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Space.Cyan,
                unfocusedBorderColor = Space.Muted.copy(alpha = 0.4f),
                focusedContainerColor = Space.Card,
                unfocusedContainerColor = Space.Card,
                cursorColor = Space.Cyan,
                focusedTextColor = Space.Text,
                unfocusedTextColor = Space.Text,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonChip("Manga", !novelsOn) { vm.setSearchNovels(false) }
            NeonChip("Light novels", novelsOn) { vm.setSearchNovels(true) }
        }
        if (novelsOn) {
            when {
                query.isBlank() -> EmptyView("Find a light novel", "Search AniList's light novel listings.")
                novelState.items.isEmpty() && novelState.loading -> LoadingView()
                novelState.items.isEmpty() && novelState.error != null -> ErrorView(novelState.error ?: "") { vm.setQuery(query) }
                novelState.items.isEmpty() -> EmptyView("No matches", "Try another spelling.")
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(novelState.items, key = { "n" + it.id }) { a ->
                        AnimeRow(a, headline = "Light novel · " + statusLabel(a.status), onClick = { openAnime(a.id) })
                    }
                    item { ListFooter(novelState.loading, novelState.error, novelState.hasMore, novelState.items.size) { vm.loadMoreNovelSearch() } }
                }
            }
            return@Column
        }
        when {
            query.isBlank() -> EmptyView("Find a manga", "Search MangaDex by English or Japanese title.")
            state.items.isEmpty() && state.loading -> LoadingView()
            state.items.isEmpty() && state.error != null -> ErrorView(state.error ?: "") { vm.setQuery(query) }
            state.items.isEmpty() -> EmptyView("No matches", "Try another spelling, or check your content and language settings.")
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
                items(state.items, key = { it.id }) { m ->
                    MangaRow(m, headline = pretty(m.status) ?: "Manga") { openManga(m.id) }
                }
                item { ListFooter(state.loading, state.error, state.hasMore, state.items.size) { vm.loadMoreSearch() } }
            }
        }
    }
}

// ---------- Library ----------

@Composable
fun MangaLibraryScreen(vm: MangaViewModel, openManga: (String) -> Unit, read: ReadAction, openAnime: (Int) -> Unit) {
    val follows by MangaLibrary.follows.collectAsStateWithLifecycle()
    val marks by MangaLibrary.marks.collectAsStateWithLifecycle()
    val statuses by Lists.mangaStatus.collectAsStateWithLifecycle()
    val animeStatuses by Lists.animeStatus.collectAsStateWithLifecycle()
    val starred by Watchlist.ids.collectAsStateWithLifecycle()
    val novelState by vm.novelLibrary.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    // Light novels you've starred or put on a list (AniList IDs; only novels come back).
    val novelIds = starred + animeStatuses.keys
    LaunchedEffect(novelIds) { vm.loadNovelLibrary(novelIds) }
    val state by vm.library.collectAsStateWithLifecycle()
    val counts by vm.newCounts.collectAsStateWithLifecycle()
    val tracking = Settings.manga.trackReading

    // Library shows followed manga plus anything you've started reading.
    val ids = follows + marks.keys + statuses.keys
    LaunchedEffect(ids) {
        val loaded = (vm.library.value as? Load.Ready)?.data?.map { it.id }?.toSet()
        if (loaded == null || !loaded.containsAll(ids)) vm.loadLibrary(ids)
    }

    Column(Modifier.fillMaxSize()) {
        ToolbarRow("Your library", onRefresh = { vm.loadLibrary(ids); vm.loadNovelLibrary(novelIds) })
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { NeonChip("All", filter == null) { filter = null } }
            items(ListStatus.entries.toList()) { st -> NeonChip(st.manga, filter == st.name) { filter = st.name } }
        }
        val novelList = (novelState as? Load.Ready)?.data.orEmpty()
            .filter { filter == null || animeStatuses[it.id]?.name == filter }
        if (ids.isEmpty() && novelList.isEmpty()) {
            EmptyView("Library is empty", "Tap the bookmark on any manga to follow it. Followed manga get new-chapter alerts.")
            return@Column
        }
        when (val s = state) {
            Load.Loading -> LoadingView()
            is Load.Error -> ErrorView(s.message) { vm.loadLibrary(ids) }
            is Load.Ready -> {
                val byId = s.data.associateBy { it.id }
                val continuing = marks.entries.sortedByDescending { it.value.at }.mapNotNull { e -> byId[e.key]?.let { it to e.value } }
                LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
                    if (tracking && continuing.isNotEmpty()) {
                        item {
                            Text(
                                "CONTINUE READING", color = Space.Muted, fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(continuing, key = { it.first.id }) { (m, mark) ->
                                    Column(Modifier.width(110.dp).clickable { read(m.id, mark.chapterId, mark.page) }) {
                                        Box {
                                            Poster(m.cover, Modifier.fillMaxWidth().aspectRatio(0.7f))
                                            Icon(
                                                Icons.AutoMirrored.Rounded.MenuBook, null, tint = Space.Cyan,
                                                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(20.dp),
                                            )
                                        }
                                        Text(m.title, color = Space.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                        Text(
                                            "Ch. ${mark.chapter ?: "?"} · p.${mark.page + 1}",
                                            color = Space.Cyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            if (filter == null) "FOLLOWING" else "MANGA", color = Space.Muted, fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                        )
                    }
                    val followed = s.data.filter {
                        if (filter == null) it.id in follows else statuses[it.id]?.name == filter
                    }
                    if (followed.isEmpty()) {
                        item {
                            Text(if (filter == null) "Not following anything yet." else "Nothing on this list yet.",
                                color = Space.Muted, modifier = Modifier.padding(16.dp))
                        }
                    }
                    items(followed, key = { "f" + it.id }) { m ->
                        val mark = marks[m.id]
                        val newCount = counts[m.id]
                        val headline = when {
                            !tracking || mark == null -> pretty(m.status) ?: "Following"
                            newCount != null && newCount > 0 -> "$newCount new · on Ch. ${mark.chapter ?: "?"}"
                            newCount == 0 -> "Caught up · Ch. ${mark.chapter ?: "?"}"
                            else -> "On Ch. ${mark.chapter ?: "?"}"
                        }
                        MangaRow(m, headline, headlineColor = if ((newCount ?: 0) > 0) Space.Magenta else Space.Cyan) { openManga(m.id) }
                    }
                    if (novelList.isNotEmpty()) {
                        item {
                            Text(
                                "LIGHT NOVELS", color = Space.Muted, fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                            )
                        }
                        items(novelList, key = { "nl" + it.id }) { a ->
                            AnimeRow(a, headline = "Light novel · " + statusLabel(a.status), onClick = { openAnime(a.id) })
                        }
                    }
                    item { Credit() }
                }
            }
        }
    }
}
