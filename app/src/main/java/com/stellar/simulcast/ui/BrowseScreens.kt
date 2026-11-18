package com.stellar.simulcast.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellar.simulcast.data.SeasonKey
import com.stellar.simulcast.data.Settings
import com.stellar.simulcast.data.Watchlist
import com.stellar.simulcast.work.Notifier

// ---------- Season browser ----------

@Composable
fun SeasonScreen(vm: AppViewModel, open: (Int) -> Unit) {
    val key by vm.seasonKey.collectAsStateWithLifecycle()
    val state by vm.season.collectAsStateWithLifecycle()
    val filters by Settings.filters.collectAsStateWithLifecycle()
    val now = rememberNow()
    LaunchedEffect(Unit) { if (vm.season.value.items.isEmpty()) vm.loadMoreSeason() }

    val current = SeasonKey.current()
    val choices = listOf(current.prev(), current, current.next())

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(choices) { k ->
                val tag = when (k) {
                    current -> " · now"
                    current.next() -> " · next"
                    else -> ""
                }
                NeonChip(k.label + tag, k == key) { vm.setSeason(k) }
            }
        }
        if (state.items.isEmpty() && state.loading) {
            LoadingView()
        } else if (state.items.isEmpty() && state.error != null) {
            ErrorView(state.error ?: "") { vm.loadMoreSeason() }
        } else if (state.items.isEmpty() && !state.hasMore) {
            EmptyView("No shows listed yet", "AniList hasn't announced anything for ${key.label} yet.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.items.filter { filters.matches(it) }, key = { it.id }) { a ->
                    PosterTile(a, now) { open(a.id) }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ListFooter(state.loading, state.error, state.hasMore, state.items.size) { vm.loadMoreSeason() }
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
    val filters by Settings.filters.collectAsStateWithLifecycle()
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
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )
        when {
            query.isBlank() -> EmptyView("Scan the airwaves", "Find any show to see when its next episode lands and where to watch it.")
            state.items.isEmpty() && state.loading -> LoadingView()
            state.items.isEmpty() && state.error != null -> ErrorView(state.error ?: "") { vm.setQuery(query) }
            state.items.isEmpty() -> EmptyView("No matches", if (filters.active) "Try clearing some filters." else "Try the Japanese or English title.")
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
                items(state.items.filter { filters.matches(it) }, key = { it.id }) { a ->
                    AnimeRow(
                        a,
                        headline = airingHeadline(a, now),
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

// ---------- Alerts switch on the Watchlist tab ----------

@Composable
fun AlertsPanel() {
    val ctx = LocalContext.current
    val alerts by Watchlist.alerts.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val blocked = alerts && !Notifier.canNotify(ctx)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .neonPanel(Space.Magenta)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = Space.Magenta)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Episode alerts", color = Space.Text, fontWeight = FontWeight.SemiBold)
            Text(
                if (blocked) "Notifications are off for this app in Android settings."
                else "Get a ping when a starred show drops a new episode.",
                color = if (blocked) Space.Magenta else Space.Muted,
                fontSize = 12.sp,
            )
        }
        Switch(
            checked = alerts,
            onCheckedChange = { on ->
                Watchlist.setAlerts(on)
                if (on && Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(ctx)) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Space.Bg,
                checkedTrackColor = Space.Cyan,
            ),
        )
    }
}
