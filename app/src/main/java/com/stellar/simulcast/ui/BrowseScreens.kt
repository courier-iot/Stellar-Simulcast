package com.stellar.simulcast.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellar.simulcast.data.BrowseMode
import com.stellar.simulcast.data.SeasonKey
import com.stellar.simulcast.data.Settings

// ---------- Browse: trending, binge-ready, seasons ----------

@Composable
fun BrowseScreen(vm: AppViewModel, open: (Int) -> Unit) {
    val mode by vm.browseMode.collectAsStateWithLifecycle()
    val state by vm.browse.collectAsStateWithLifecycle()
    val watch = LocalWatch.current
    val filters = Settings.filters
    val now = rememberNow()
    LaunchedEffect(Unit) { if (vm.browse.value.items.isEmpty()) vm.loadMoreBrowse() }

    val current = SeasonKey.current()
    val modes: List<BrowseMode> = listOf(
        BrowseMode.Trending,
        BrowseMode.BingeReady,
        BrowseMode.OfSeason(current),
        BrowseMode.OfSeason(current.next()),
        BrowseMode.OfSeason(current.prev()),
    )
    val visible = state.items.filter { it.id !in watch.hidden && filters.matches(it) }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(modes) { m ->
                    val tag = when (m) {
                        BrowseMode.OfSeason(current) -> " · now"
                        BrowseMode.OfSeason(current.next()) -> " · next"
                        else -> ""
                    }
                    NeonChip(m.label + tag, m == mode) { vm.setBrowseMode(m) }
                }
            }
            IconButton(
                onClick = { visible.randomOrNull()?.let { open(it.id) } },
                enabled = visible.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.Casino, contentDescription = "Pick something for me", tint = Space.Magenta)
            }
        }
        when {
            state.items.isEmpty() && state.loading -> LoadingView()
            state.items.isEmpty() && state.error != null -> ErrorView(state.error ?: "") { vm.loadMoreBrowse() }
            state.items.isEmpty() && !state.hasMore -> EmptyView("Nothing here yet", "AniList has nothing listed for ${mode.label}.")
            visible.isEmpty() && !state.hasMore && !state.loading ->
                EmptyView("No matches", "Nothing here fits your filters or everything is hidden.")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(visible, key = { it.id }) { a ->
                    PosterTile(a, now) { open(a.id) }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ListFooter(state.loading, state.error, state.hasMore, state.items.size) { vm.loadMoreBrowse() }
                }
            }
        }
    }
}

// ---------- Search ----------

@Composable
fun SearchScreen(vm: AppViewModel, open: (Int) -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val state by vm.search.collectAsStateWithLifecycle()
    val watch = LocalWatch.current
    val filters = Settings.filters
    val now = rememberNow()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.setQuery(it) },
            singleLine = true,
            placeholder = { Text("Search any anime", color = Space.Muted) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Space.Cyan) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = Space.Muted)
                    }
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
        // Search still shows hidden shows, so you can find and unhide them.
        val results = state.items.filter { filters.matches(it) }
        when {
            query.isBlank() -> EmptyView("Scan the airwaves", "Find any show to see when its next episode lands and where to watch it.")
            state.items.isEmpty() && state.loading -> LoadingView()
            state.items.isEmpty() && state.error != null -> ErrorView(state.error ?: "") { vm.setQuery(query) }
            results.isEmpty() && !state.loading -> EmptyView(
                "No matches",
                if (filters.active) "Try clearing some filters." else "Try the Japanese or English title.",
            )
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
                items(results, key = { it.id }) { a ->
                    AnimeRow(
                        a,
                        headline = (if (a.id in watch.hidden) "Hidden · " else "") + airingHeadline(a, now),
                        headlineColor = if (a.nextAiringAt != null) Space.Cyan else Space.Muted,
                        onClick = { open(a.id) },
                    )
                }
                item {
                    ListFooter(state.loading, state.error, state.hasMore, state.items.size) { vm.loadMoreSearch() }
                }
            }
        }
    }
}
