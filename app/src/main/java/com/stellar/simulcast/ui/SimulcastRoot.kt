package com.stellar.simulcast.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Settings as SettingsIcon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import com.stellar.simulcast.data.Settings
import com.stellar.simulcast.data.Watchlist
import com.stellar.simulcast.work.Notifier

enum class Tab(val label: String, val icon: ImageVector) {
    SCHEDULE("Schedule", Icons.Rounded.CalendarMonth),
    AIRED("Just aired", Icons.Rounded.NewReleases),
    SEASON("Season", Icons.Rounded.AutoAwesome),
    SEARCH("Search", Icons.Rounded.Search),
    WATCH("Watchlist", Icons.Rounded.Star),
}

@Composable
fun SimulcastRoot(openAnime: MutableState<Int?>) {
    val vm: AppViewModel = viewModel()
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.SCHEDULE) }
    val ids by Watchlist.ids.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val toggle: (Int) -> Unit = { id ->
        val added = Watchlist.toggle(id)
        // First time something is starred, ask for notification permission so alerts can show.
        if (added && Watchlist.alerts.value && Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(ctx)) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val open: (Int) -> Unit = { openAnime.value = it }
    val lang by Settings.titleLang.collectAsStateWithLifecycle()
    val filters by Settings.filters.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(filters) { vm.applyFilters(filters) }

    CompositionLocalProvider(
        LocalWatch provides WatchActions(ids, toggle),
        LocalTitleLang provides lang,
    ) {
        Box(Modifier.fillMaxSize()) {
            Starfield()
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    Header(
                        filterCount = filters.count,
                        showFilter = tab != Tab.WATCH,
                        onFilters = { showFilters = true },
                        onSettings = { showSettings = true },
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = Space.Surface.copy(alpha = 0.94f)) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(t.label, fontSize = 10.sp, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Space.Cyan,
                                    selectedTextColor = Space.Cyan,
                                    indicatorColor = Space.Cyan.copy(alpha = 0.14f),
                                    unselectedIconColor = Space.Muted,
                                    unselectedTextColor = Space.Muted,
                                ),
                            )
                        }
                    }
                },
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    when (tab) {
                        Tab.SCHEDULE -> ScheduleScreen(vm, open)
                        Tab.AIRED -> JustAiredScreen(vm, open)
                        Tab.SEASON -> SeasonScreen(vm, open)
                        Tab.SEARCH -> SearchScreen(vm, open)
                        Tab.WATCH -> WatchlistScreen(vm, open)
                    }
                }
            }

            if (showSettings) {
                BackHandler { showSettings = false }
                SettingsScreen(onBack = { showSettings = false })
            }

            val detailId = openAnime.value
            if (detailId != null) {
                BackHandler { openAnime.value = null }
                DetailScreen(detailId, onBack = { openAnime.value = null })
            }

            if (showFilters) FilterDialog(onDismiss = { showFilters = false })
        }
    }
}

@Composable
private fun Header(filterCount: Int, showFilter: Boolean, onFilters: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "STELLAR", color = Space.Cyan, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 6.sp,
            )
            Text(
                "SIMULCAST", color = Space.Magenta, fontFamily = FontFamily.Monospace,
                fontSize = 11.sp, letterSpacing = 8.sp,
            )
        }
        if (showFilter) {
            IconButton(onClick = onFilters) {
                Box {
                    Icon(
                        Icons.Rounded.FilterList, contentDescription = "Filters",
                        tint = if (filterCount > 0) Space.Magenta else Space.Cyan,
                    )
                    if (filterCount > 0) {
                        Text(
                            filterCount.toString(), color = Space.Bg, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp)
                                .clip(CircleShape)
                                .background(Space.Magenta)
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.SettingsIcon, contentDescription = "Settings", tint = Space.Cyan)
        }
    }
}
