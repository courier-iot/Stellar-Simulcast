package com.stellar.simulcast.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Settings as SettingsIcon
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stellar.simulcast.data.Settings
import com.stellar.simulcast.data.Watchlist
import com.stellar.simulcast.work.Notifier

enum class Mode { ANIME, MANGA }

enum class MangaTab(val label: String, val icon: ImageVector) {
    LATEST("Latest", Icons.Rounded.NewReleases),
    BROWSE("Browse", Icons.Rounded.AutoAwesome),
    SEARCH("Search", Icons.Rounded.Search),
    LIBRARY("Library", Icons.Rounded.CollectionsBookmark),
}

enum class Tab(val label: String, val icon: ImageVector) {
    SCHEDULE("Schedule", Icons.Rounded.CalendarMonth),
    AIRED("Just aired", Icons.Rounded.NewReleases),
    BROWSE("Browse", Icons.Rounded.AutoAwesome),
    SEARCH("Search", Icons.Rounded.Search),
    WATCH("Watchlist", Icons.Rounded.Star),
}

@Composable
fun SimulcastRoot(openAnime: MutableState<Int?>, openManga: MutableState<String?>) {
    val vm: AppViewModel = viewModel()
    val mvm: MangaViewModel = viewModel()
    val uri = LocalUriHandler.current
    var mode by rememberSaveable { mutableStateOf(Mode.ANIME) }
    var mangaTab by rememberSaveable { mutableStateOf(MangaTab.LATEST) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    // Reader target saved as "mangaId|chapterId|page" so it survives rotation.
    var reading by rememberSaveable { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.SCHEDULE) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val ids by Watchlist.ids.collectAsStateWithLifecycle()
    val hidden by Watchlist.hidden.collectAsStateWithLifecycle()
    val progress by Watchlist.progress.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val toggle: (Int) -> Unit = { id ->
        val added = Watchlist.toggle(id)
        // First time something is starred, ask for notification permission so alerts can show.
        if (added && Watchlist.alerts.value && Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(ctx)) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val open: (Int) -> Unit = { openAnime.value = it }
    val openMangaPage: (String) -> Unit = { openManga.value = it }
    val read: ReadAction = { m, c, p -> reading = "$m|$c|$p" }
    val openUrl: (String) -> Unit = { runCatching { uri.openUri(it) } }
    val filters = Settings.filters
    val mangaPrefs = Settings.manga

    LaunchedEffect(filters) { vm.applyFilters(filters) }
    LaunchedEffect(mangaPrefs) { mvm.applyPrefs(mangaPrefs) }
    // A manga notification should land in manga mode.
    LaunchedEffect(openManga.value) { if (openManga.value != null) mode = Mode.MANGA }

    // "Larger text" scales every font in the app by 15%.
    val density = LocalDensity.current
    val scaled = if (Settings.display.largeText) Density(density.density, density.fontScale * 1.15f) else density

    CompositionLocalProvider(
        LocalWatch provides WatchActions(ids, hidden, progress, toggle),
        LocalDensity provides scaled,
    ) {
        Box(Modifier.fillMaxSize()) {
            Starfield()
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    Header(
                        mode = mode,
                        onMode = { mode = it },
                        filterCount = filters.count,
                        showFilter = mode == Mode.ANIME && tab != Tab.WATCH,
                        onFilters = { showFilters = true },
                        onStats = { showStats = true },
                        onSettings = { showSettings = true },
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = Space.Surface.copy(alpha = 0.94f)) {
                        val colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Space.Cyan,
                            selectedTextColor = Space.Cyan,
                            indicatorColor = Space.Cyan.copy(alpha = 0.14f),
                            unselectedIconColor = Space.Muted,
                            unselectedTextColor = Space.Muted,
                        )
                        if (mode == Mode.ANIME) {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t,
                                    onClick = { tab = t },
                                    icon = { Icon(t.icon, contentDescription = null) },
                                    label = { Text(t.label, fontSize = 10.sp, maxLines = 1) },
                                    colors = colors,
                                )
                            }
                        } else {
                            MangaTab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = mangaTab == t,
                                    onClick = { mangaTab = t },
                                    icon = { Icon(t.icon, contentDescription = null) },
                                    label = { Text(t.label, fontSize = 10.sp, maxLines = 1) },
                                    colors = colors,
                                )
                            }
                        }
                    }
                },
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    if (mode == Mode.ANIME) {
                        when (tab) {
                            Tab.SCHEDULE -> ScheduleScreen(vm, open)
                            Tab.AIRED -> JustAiredScreen(vm, open)
                            Tab.BROWSE -> BrowseScreen(vm, open)
                            Tab.SEARCH -> SearchScreen(vm, open)
                            Tab.WATCH -> WatchlistScreen(vm, open)
                        }
                    } else {
                        when (mangaTab) {
                            MangaTab.LATEST -> MangaLatestScreen(mvm, openMangaPage, read, openUrl)
                            MangaTab.BROWSE -> MangaBrowseScreen(mvm, openMangaPage)
                            MangaTab.SEARCH -> MangaSearchScreen(mvm, openMangaPage)
                            MangaTab.LIBRARY -> MangaLibraryScreen(mvm, openMangaPage, read)
                        }
                    }
                }
            }

            if (showSettings) {
                BackHandler { showSettings = false }
                SettingsScreen(vm, onBack = { showSettings = false }, open = open)
            }

            if (showStats) {
                BackHandler { showStats = false }
                StatsScreen(onBack = { showStats = false })
            }

            val mangaId = openManga.value
            if (mangaId != null) {
                BackHandler { openManga.value = null }
                MangaDetailScreen(mangaId, onBack = { openManga.value = null }, read = read, openAnime = open)
            }

            // An anime page can be opened from a manga page, so it sits above it.
            val detailId = openAnime.value
            if (detailId != null) {
                BackHandler { openAnime.value = null }
                DetailScreen(detailId, onBack = { openAnime.value = null }, open = open, openManga = openMangaPage)
            }

            val r = reading?.split("|")
            if (r != null && r.size == 3) {
                BackHandler { reading = null }
                ReaderScreen(r[0], r[1], r[2].toIntOrNull() ?: 0, onClose = { reading = null })
            }

            if (showFilters) FilterDialog(onDismiss = { showFilters = false })
        }
    }
}

@Composable
private fun Header(
    mode: Mode,
    onMode: (Mode) -> Unit,
    filterCount: Int,
    showFilter: Boolean,
    onFilters: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "STELLAR", color = Space.Cyan, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 4.sp,
            )
            Text(
                "SIMULCAST", color = Space.Magenta, fontFamily = FontFamily.Monospace,
                fontSize = 10.sp, letterSpacing = 5.sp,
            )
        }
        ModeSwitch(mode, onMode)
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
        IconButton(onClick = onStats) {
            Icon(Icons.Rounded.Insights, contentDescription = "Stats", tint = Space.Cyan)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.SettingsIcon, contentDescription = "Settings", tint = Space.Cyan)
        }
    }
}

@Composable
private fun ModeSwitch(mode: Mode, onMode: (Mode) -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(Space.Card).border(1.dp, Space.Cyan.copy(alpha = 0.4f), shape).padding(2.dp),
    ) {
        Mode.entries.forEach { m ->
            val on = m == mode
            Text(
                if (m == Mode.ANIME) "Anime" else "Manga",
                color = if (on) Space.Bg else Space.Muted,
                fontSize = 12.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(shape)
                    .background(if (on) Space.Cyan else Color.Transparent)
                    .clickable { onMode(m) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}
