package com.stellar.simulcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stellar.simulcast.data.AniList
import com.stellar.simulcast.data.Anime
import com.stellar.simulcast.data.friendlyError
import kotlinx.coroutines.launch

/** Everything a studio has made, newest first. */
@Composable
fun StudioScreen(studioId: Int, onBack: () -> Unit, open: (Int) -> Unit) {
    var name by remember { mutableStateOf("Studio") }
    var shows by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val now = rememberNow()

    fun loadMore() {
        if (loading || !hasMore) return
        loading = true
        error = null
        scope.launch {
            try {
                val next = AniList.studio(studioId, page + 1)
                name = next.name
                shows = (shows + next.shows).distinctBy { it.id }
                page += 1
                hasMore = next.hasMore
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = friendlyError(e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(studioId) { loadMore() }

    Box(
        Modifier.fillMaxSize().background(Space.Bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Starfield()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Space.Text) }
                Column {
                    Text("STUDIO", color = Space.Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 2.sp)
                    Text(name, color = Space.Cyan, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            when {
                shows.isEmpty() && loading -> LoadingView()
                shows.isEmpty() && error != null -> ErrorView(error ?: "") { loadMore() }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(108.dp),
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shows, key = { it.id }) { a -> PosterTile(a, now) { open(a.id) } }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ListFooter(loading, error, hasMore, shows.size) { loadMore() }
                    }
                }
            }
        }
    }
}
