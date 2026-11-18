package com.stellar.simulcast.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellar.simulcast.data.ADULT_RATINGS
import com.stellar.simulcast.data.CrashLog
import com.stellar.simulcast.data.DELAY_SERVICES
import com.stellar.simulcast.data.Downloads
import com.stellar.simulcast.data.MangaDex
import com.stellar.simulcast.data.PageFilter
import com.stellar.simulcast.data.UpdateInfo
import com.stellar.simulcast.data.Updater
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.stellar.simulcast.data.CONTENT_RATINGS
import com.stellar.simulcast.data.FORMATS
import com.stellar.simulcast.data.MANGA_LANGUAGES
import com.stellar.simulcast.data.MangaLibrary
import com.stellar.simulcast.data.Filters
import com.stellar.simulcast.data.GENRES
import com.stellar.simulcast.data.Load
import com.stellar.simulcast.data.SERVICES
import com.stellar.simulcast.data.Settings
import com.stellar.simulcast.data.TimeText
import com.stellar.simulcast.data.TitleLang
import com.stellar.simulcast.data.Watchlist
import com.stellar.simulcast.work.DigestWorker
import com.stellar.simulcast.work.EpisodeCheckWorker

// ---------- Filters dialog ----------

@Composable
fun FilterDialog(onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(Settings.filters) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.Surface,
        title = { Text("Filters", color = Space.Cyan, fontFamily = FontFamily.Monospace) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Label("Streaming on")
                ChipGrid(SERVICES, draft.services, perRow = 2) { draft = draft.copy(services = draft.services.toggle(it)) }
                Spacer(Modifier.height(14.dp))
                Label("Format")
                ChipGrid(FORMATS, draft.formats, label = { formatLabel(it) ?: it }) {
                    draft = draft.copy(formats = draft.formats.toggle(it))
                }
                Spacer(Modifier.height(14.dp))
                Label("Genre")
                ChipGrid(GENRES, draft.genres, perRow = 2) { draft = draft.copy(genres = draft.genres.toggle(it)) }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Applies to Schedule, Just aired, Browse and Search. Nothing picked in a group means any.",
                    color = Space.Muted, fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { Settings.updateFilters(draft); onDismiss() }) { Text("Apply", color = Space.Cyan) }
        },
        dismissButton = {
            TextButton(onClick = { Settings.updateFilters(Filters()); onDismiss() }) { Text("Clear all", color = Space.Magenta) }
        },
    )
}

private fun Set<String>.toggle(v: String): Set<String> = if (v in this) this - v else this + v

// ---------- Settings ----------

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, open: (Int) -> Unit) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val alerts by Watchlist.alerts.collectAsStateWithLifecycle()
    val hidden by Watchlist.hidden.collectAsStateWithLifecycle()
    val display = Settings.display
    val prefs = Settings.alertPrefs
    val lang = Settings.titleLang

    Box(
        Modifier
            .fillMaxSize()
            .background(Space.Bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Starfield()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Space.Text)
                }
                Text("SETTINGS", color = Space.Cyan, fontFamily = FontFamily.Monospace, fontSize = 18.sp, letterSpacing = 4.sp)
            }

            // ----- Look -----
            Section("Theme") {
                PALETTES.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        row.forEach { p ->
                            val selected = p.id == display.accent
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Space.Card)
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) p.primary else Space.Muted.copy(alpha = 0.3f),
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable { Settings.updateDisplay(display.copy(accent = p.id)) }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(16.dp).clip(CircleShape).background(p.primary))
                                    Box(Modifier.size(16.dp).clip(CircleShape).background(p.secondary))
                                }
                                Text(p.name, color = Space.Text, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }

            Section("Display") {
                ToggleRow("Pure black", "True black background for OLED screens (saves battery)", display.pureBlack) {
                    Settings.updateDisplay(display.copy(pureBlack = it))
                }
                Spacer(Modifier.height(6.dp))
                Text("Show titles in", color = Space.Text)
                Spacer(Modifier.height(6.dp))
                ChipGrid(
                    TitleLang.entries.map { it.name },
                    setOf(lang.name),
                    label = { if (it == "ENGLISH") "English" else "Japanese (romaji)" },
                    perRow = 2,
                ) { Settings.updateTitleLang(TitleLang.valueOf(it)) }
                Spacer(Modifier.height(10.dp))
                ToggleRow("24-hour clock", "Show 21:30 instead of 9:30 PM", display.use24h) {
                    Settings.updateDisplay(display.copy(use24h = it))
                }
                ToggleRow("Larger text", "Bump all text up a size", display.largeText) {
                    Settings.updateDisplay(display.copy(largeText = it))
                }
                ToggleRow("Compact lists", "Smaller rows so more fit on screen", display.compact) {
                    Settings.updateDisplay(display.copy(compact = it))
                }
                ToggleRow("Spoiler-safe", "Hide synopses and episode titles until you tap them", display.spoilerSafe) {
                    Settings.updateDisplay(display.copy(spoilerSafe = it))
                }
            }

            // ----- Streaming -----
            Section("Streaming") {
                Text("Stream delay", color = Space.Text, fontWeight = FontWeight.SemiBold)
                Text(
                    "AniList lists Japan air times. Most simulcasts reach Crunchyroll, Netflix and HIDIVE " +
                        "a little later, so add a delay to match when episodes actually show up for you. " +
                        "It shifts every time, countdown and alert in the app.",
                    color = Space.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp),
                )
                ChipGrid(
                    listOf("0", "30", "60", "120", "180", "360"),
                    setOf(display.streamDelayMin.toString()),
                    label = { m ->
                        val v = m.toInt()
                        when {
                            v == 0 -> "Off"
                            v < 60 -> "+${v}m"
                            else -> "+${v / 60}h"
                        }
                    },
                ) { Settings.updateDisplay(display.copy(streamDelayMin = it.toInt())) }
                Spacer(Modifier.height(10.dp))
                Text("Per service (optional)", color = Space.Text, fontWeight = FontWeight.SemiBold)
                Text("Shows on several services use the quickest one you set.", color = Space.Muted, fontSize = 12.sp)
                DELAY_SERVICES.forEach { svc ->
                    Spacer(Modifier.height(6.dp))
                    Text(svc, color = Space.Text, fontSize = 13.sp)
                    ChipGrid(
                        listOf("default", "0", "30", "60", "120", "180"),
                        setOf(display.serviceDelays[svc]?.toString() ?: "default"),
                        label = { m ->
                            when (m) {
                                "default" -> "Default"
                                "0" -> "None"
                                else -> m.toInt().let { v -> if (v < 60) "+${v}m" else "+${v / 60}h" }
                            }
                        },
                    ) { m ->
                        val map = display.serviceDelays.toMutableMap()
                        if (m == "default") map.remove(svc) else map[svc] = m.toInt()
                        Settings.updateDisplay(display.copy(serviceDelays = map))
                    }
                }
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    "English links only",
                    "On show pages, only list streams AniList marks as English. AniList doesn't track which country a service works in, so this is the closest match.",
                    display.englishLinksOnly,
                ) { Settings.updateDisplay(display.copy(englishLinksOnly = it)) }
            }

            // ----- Alerts -----
            Section("Alerts") {
                ToggleRow("Episode alerts", "Notify me when a starred show drops an episode", alerts) { Watchlist.setAlerts(it) }
                Spacer(Modifier.height(10.dp))
                Text("Check for new episodes every", color = Space.Text)
                Spacer(Modifier.height(6.dp))
                ChipGrid(
                    listOf("15", "30", "60", "180"),
                    setOf(prefs.intervalMin.toString()),
                    label = { if (it.toInt() < 60) "${it}m" else "${it.toInt() / 60}h" },
                    perRow = 4,
                ) {
                    val next = prefs.copy(intervalMin = it.toInt())
                    Settings.updateAlertPrefs(next)
                    EpisodeCheckWorker.schedule(ctx, next.intervalMin, replace = true)
                }
                Text(
                    "Android may run checks a little late to save battery.",
                    color = Space.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                )
                ToggleRow("Group alerts", "One notification when several episodes come out together", prefs.grouped) {
                    Settings.updateAlertPrefs(prefs.copy(grouped = it))
                }
                ToggleRow("Sequel alerts", "Tell me when a starred show gets a new season or an air date", prefs.sequels) {
                    Settings.updateAlertPrefs(prefs.copy(sequels = it))
                }
                ToggleRow("Daily digest", "One morning notification with what's out today", prefs.digest) {
                    val next = prefs.copy(digest = it)
                    Settings.updateAlertPrefs(next)
                    DigestWorker.schedule(ctx, next, replace = true)
                }
                if (prefs.digest) {
                    HourStepper("At", prefs.digestHour) {
                        val next = prefs.copy(digestHour = it)
                        Settings.updateAlertPrefs(next)
                        DigestWorker.schedule(ctx, next, replace = true)
                    }
                }
                ToggleRow("Quiet hours", "Hold alerts overnight and deliver them after", prefs.quiet) {
                    Settings.updateAlertPrefs(prefs.copy(quiet = it))
                }
                if (prefs.quiet) {
                    HourStepper("From", prefs.quietStart) { Settings.updateAlertPrefs(prefs.copy(quietStart = it)) }
                    HourStepper("Until", prefs.quietEnd) { Settings.updateAlertPrefs(prefs.copy(quietEnd = it)) }
                }
            }

            // ----- Manga -----
            MangaSettings()

            // ----- Move to another phone -----
            Section("Move to another phone") { TransferSection() }

            // ----- Hidden shows -----
            Section("Hidden shows") {
                if (hidden.isEmpty()) {
                    Text(
                        "Nothing hidden. Use Hide on a show's page to keep it out of Schedule, Just aired and Browse.",
                        color = Space.Muted, fontSize = 13.sp,
                    )
                } else {
                    HiddenList(vm, hidden, open)
                }
            }

            // ----- Downloads -----
            if (Settings.manga.downloads) {
                Section("Downloaded chapters") { DownloadsManager() }
            }

            // ----- Crash log -----
            Section("Crash log") { CrashLogSection() }

            // ----- About -----
            Section("About") {
                Text("STELLAR SIMULCAST", color = Space.Cyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Text("Version ${appVersion(ctx)}", color = Space.Muted)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Release times, show info, cast and streaming links come live from AniList. " +
                        "Stellar Simulcast isn't affiliated with AniList or any streaming service.",
                    color = Space.Text, fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Stored on this phone: only IDs and numbers (watchlist, hidden shows, episode progress, followed manga and reading spots) and these settings. No show or manga data is saved. Manga and chapters come live from MangaDex; translations are by the scanlation groups credited on each chapter.",
                    color = Space.Muted, fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { runCatching { uri.openUri("https://anilist.co") } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Visit AniList", color = Space.Cyan)
                }
                Spacer(Modifier.height(10.dp))
                ToggleRow("Check for updates", "Once a day, look for a newer release on GitHub", display.updateChecks) {
                    Settings.updateDisplay(display.copy(updateChecks = it))
                }
                UpdateCheckButton()
            }
        }
    }
}

@Composable
private fun MangaSettings() {
    val m = Settings.manga
    var askAdult by remember { mutableStateOf<String?>(null) }
    var askClear by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }

    Section("Manga") {
        ToggleRow(
            "Track my reading",
            "Remember the chapter and page you stopped on, and show new-chapter counts. Off means nothing about your reading is saved.",
            m.trackReading,
        ) { on ->
            if (on) Settings.updateManga(m.copy(trackReading = true)) else askClear = true
        }
        ToggleRow("New chapter alerts", "Notify me when a manga I follow gets a chapter in my languages", m.chapterAlerts) {
            Settings.updateManga(m.copy(chapterAlerts = it))
        }
        Spacer(Modifier.height(8.dp))
        Text("Chapter languages", color = Space.Text)
        Spacer(Modifier.height(6.dp))
        ChipGrid(MANGA_LANGUAGES.map { it.first }, m.languages, label = { code -> MANGA_LANGUAGES.first { it.first == code }.second }, perRow = 2) { code ->
            val next = if (code in m.languages) m.languages - code else m.languages + code
            if (next.isNotEmpty()) Settings.updateManga(m.copy(languages = next))
        }
        Spacer(Modifier.height(12.dp))
        Text("Content ratings shown", color = Space.Text)
        Text("MangaDex tags every series. The last two are adult content (18+).", color = Space.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        ChipGrid(CONTENT_RATINGS, m.ratings, label = { pretty(it) ?: it }, perRow = 2) { r ->
            when {
                r in m.ratings -> if (m.ratings.size > 1) Settings.updateManga(m.copy(ratings = m.ratings - r))
                r in ADULT_RATINGS -> askAdult = r
                else -> Settings.updateManga(m.copy(ratings = m.ratings + r))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Reader", color = Space.Text)
        ToggleRow("Scroll mode", "One long strip, best for webtoons. Off pages left and right.", m.webtoon) {
            Settings.updateManga(m.copy(webtoon = it))
        }
        ToggleRow("Right to left", "Page the way Japanese manga is read", m.rtl) { Settings.updateManga(m.copy(rtl = it)) }
        ToggleRow("Data saver", "Lower-quality pages to save mobile data", m.dataSaver) { Settings.updateManga(m.copy(dataSaver = it)) }
        ToggleRow("Two pages when sideways", "Show pages side by side in landscape (page mode)", m.spreads) { Settings.updateManga(m.copy(spreads = it)) }
        ToggleRow("Keep screen on", "Stop the screen dimming while you read", m.keepScreenOn) { Settings.updateManga(m.copy(keepScreenOn = it)) }
        ToggleRow("Volume buttons turn pages", "Volume down for next, up for previous, while reading", m.volumeKeys) {
            Settings.updateManga(m.copy(volumeKeys = it))
        }
        Spacer(Modifier.height(6.dp))
        Text("Page filter", color = Space.Text)
        Spacer(Modifier.height(6.dp))
        ChipGrid(PageFilter.entries.map { it.name }, setOf(m.pageFilter.name), label = { PageFilter.valueOf(it).label }) {
            Settings.updateManga(m.copy(pageFilter = PageFilter.valueOf(it)))
        }
        Spacer(Modifier.height(12.dp))
        Text("Tag filters", color = Space.Text)
        Text(
            if (m.includedTags.isEmpty() && m.excludedTags.isEmpty()) "None. Show or hide series by tag, like \"no isekai\"."
            else listOfNotNull(
                m.includedTags.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Only: ") { it.substringAfter(":") },
                m.excludedTags.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Hide: ") { it.substringAfter(":") },
            ).joinToString("\n"),
            color = Space.Muted, fontSize = 12.sp,
        )
        OutlinedButton(onClick = { showTags = true }, modifier = Modifier.fillMaxWidth()) { Text("Edit tag filters", color = Space.Cyan) }
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            "Chapter downloads",
            "Save chapters to read offline. They stay in the app's private storage until you delete them.",
            m.downloads,
        ) { Settings.updateManga(m.copy(downloads = it)) }
    }

    if (showTags) TagDialog { showTags = false }

    askAdult?.let { r ->
        AlertDialog(
            onDismissRequest = { askAdult = null },
            containerColor = Space.Surface,
            title = { Text("Adult content", color = Space.Magenta) },
            text = { Text("\"${pretty(r)}\" manga is for adults only. Confirm you're 18 or older to show it.", color = Space.Text) },
            confirmButton = {
                TextButton(onClick = {
                    Settings.updateManga(Settings.manga.copy(ratings = Settings.manga.ratings + r))
                    askAdult = null
                }) { Text("I'm 18 or older", color = Space.Magenta) }
            },
            dismissButton = { TextButton(onClick = { askAdult = null }) { Text("Cancel", color = Space.Muted) } },
        )
    }

    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            containerColor = Space.Surface,
            title = { Text("Stop tracking reading?", color = Space.Cyan) },
            text = { Text("Your saved reading spots can be kept for later or deleted now. Followed manga stay either way.", color = Space.Text) },
            confirmButton = {
                TextButton(onClick = {
                    MangaLibrary.clearAllMarks()
                    Settings.updateManga(Settings.manga.copy(trackReading = false))
                    askClear = false
                }) { Text("Delete history", color = Space.Magenta) }
            },
            dismissButton = {
                TextButton(onClick = {
                    Settings.updateManga(Settings.manga.copy(trackReading = false))
                    askClear = false
                }) { Text("Keep it", color = Space.Cyan) }
            },
        )
    }
}

@Composable
private fun HiddenList(vm: AppViewModel, hidden: Set<Int>, open: (Int) -> Unit) {
    val state by vm.hiddenShows.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadHidden(hidden) }
    when (val s = state) {
        Load.Loading -> CircularProgressIndicator(color = Space.Cyan, modifier = Modifier.size(24.dp))
        is Load.Error -> Text(s.message, color = Space.Muted, fontSize = 13.sp)
        is Load.Ready -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            s.data.filter { it.id in hidden }.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        a.name(Settings.titleLang), color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).clickable { open(a.id) },
                    )
                    TextButton(onClick = { Watchlist.toggleHidden(a.id) }) { Text("Unhide", color = Space.Cyan) }
                }
            }
        }
    }
}

private fun appVersion(ctx: Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
} catch (e: Exception) {
    "?"
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .neonPanel()
            .padding(14.dp)
    ) {
        Label(title)
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(), color = Space.Muted, fontFamily = FontFamily.Monospace,
        fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Space.Text, fontWeight = FontWeight.SemiBold)
            Text(body, color = Space.Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Space.Bg, checkedTrackColor = Space.Cyan),
        )
    }
}

@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Space.Text, modifier = Modifier.width(56.dp))
        IconButton(onClick = { onChange((hour + 23) % 24) }) { Icon(Icons.Rounded.Remove, "Earlier", tint = Space.Cyan) }
        Text(TimeText.hour(hour), color = Space.Cyan, fontFamily = FontFamily.Monospace, fontSize = 16.sp, modifier = Modifier.width(72.dp))
        IconButton(onClick = { onChange((hour + 1) % 24) }) { Icon(Icons.Rounded.Add, "Later", tint = Space.Cyan) }
    }
}

// ---------- tag filter dialog ----------

@Composable
private fun TagDialog(onDismiss: () -> Unit) {
    val tags by produceState<List<MangaDex.Tag>?>(null) { value = runCatching { MangaDex.tags() }.getOrDefault(emptyList()) }
    var included by remember { mutableStateOf(Settings.manga.includedTags) }
    var excluded by remember { mutableStateOf(Settings.manga.excludedTags) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.Surface,
        title = { Text("Tag filters", color = Space.Cyan, fontFamily = FontFamily.Monospace) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Tap once to require a tag, again to hide it, a third time to clear.", color = Space.Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                val list = tags
                when {
                    list == null -> CircularProgressIndicator(color = Space.Cyan, modifier = Modifier.size(24.dp))
                    list.isEmpty() -> Text("Couldn't load tags from MangaDex.", color = Space.Muted)
                    else -> list.groupBy { it.group }.forEach { (group, items) ->
                        Text(group.uppercase(), color = Space.Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        items.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                                row.forEach { t ->
                                    val key = "${t.id}:${t.name}"
                                    val state = when (key) {
                                        in included -> 1
                                        in excluded -> 2
                                        else -> 0
                                    }
                                    Text(
                                        when (state) { 1 -> "✓ ${t.name}"; 2 -> "✕ ${t.name}"; else -> t.name },
                                        color = when (state) { 0 -> Space.Text; else -> Space.Bg },
                                        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(when (state) { 1 -> Space.Cyan; 2 -> Space.Magenta; else -> Space.Card })
                                            .clickable {
                                                when (state) {
                                                    0 -> included = included + key
                                                    1 -> { included = included - key; excluded = excluded + key }
                                                    else -> excluded = excluded - key
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Settings.updateManga(Settings.manga.copy(includedTags = included, excludedTags = excluded))
                onDismiss()
            }) { Text("Apply", color = Space.Cyan) }
        },
        dismissButton = {
            TextButton(onClick = {
                Settings.updateManga(Settings.manga.copy(includedTags = emptySet(), excludedTags = emptySet()))
                onDismiss()
            }) { Text("Clear all", color = Space.Magenta) }
        },
    )
}

// ---------- downloads ----------

@Composable
private fun DownloadsManager() {
    val ctx = LocalContext.current
    val ready by Downloads.ready.collectAsStateWithLifecycle()
    val list = remember(ready) { Downloads.list(ctx) }
    val total = list.sumOf { it.bytes }
    Text("${list.size} chapters · ${"%.1f".format(total / 1_048_576.0)} MB", color = Space.Text)
    if (list.isEmpty()) {
        Text("Use the download button next to a chapter on a manga's page.", color = Space.Muted, fontSize = 12.sp)
        return
    }
    list.forEach { d ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.mangaTitle, color = Space.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${d.chapterLabel} · ${d.pages} pages", color = Space.Muted, fontSize = 11.sp, maxLines = 1)
            }
            TextButton(onClick = { Downloads.delete(ctx, d.chapterId) }) { Text("Delete", color = Space.Magenta) }
        }
    }
    OutlinedButton(onClick = { Downloads.deleteAll(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Delete all downloads", color = Space.Magenta) }
}

// ---------- crash log ----------

@Composable
private fun CrashLogSection() {
    val ctx = LocalContext.current
    var log by remember { mutableStateOf(CrashLog.read(ctx)) }
    var show by remember { mutableStateOf(false) }
    val count = log.split("\n=====\n").count { it.isNotBlank() }
    Text(
        if (count == 0) "No crashes recorded. If the app ever crashes, the details are saved here (on this phone only) so you can send them in a bug report."
        else "$count crash${if (count == 1) "" else "es"} recorded. Share them with whoever's fixing the app.",
        color = Space.Muted, fontSize = 13.sp,
    )
    if (count > 0) {
        OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) { Text("View", color = Space.Cyan) }
        OutlinedButton(onClick = {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, log)
            }
            ctx.startActivity(android.content.Intent.createChooser(send, "Share crash log"))
        }, modifier = Modifier.fillMaxWidth()) { Text("Share / copy", color = Space.Cyan) }
        OutlinedButton(onClick = { CrashLog.clear(ctx); log = "" }, modifier = Modifier.fillMaxWidth()) { Text("Clear", color = Space.Magenta) }
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            containerColor = Space.Surface,
            title = { Text("Crash log", color = Space.Cyan) },
            text = {
                Text(log, color = Space.Text, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState()))
            },
            confirmButton = { TextButton(onClick = { show = false }) { Text("Close", color = Space.Cyan) } },
        )
    }
}

// ---------- updates ----------

@Composable
private fun UpdateCheckButton() {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<UpdateInfo?>(null) }
    OutlinedButton(
        onClick = {
            if (!checking) {
                checking = true
                scope.launch {
                    val result = runCatching { Updater.check(ctx) }
                    checking = false
                    result.onSuccess { info ->
                        if (info == null) android.widget.Toast.makeText(ctx, "You're on the latest version", android.widget.Toast.LENGTH_SHORT).show()
                        else found = info
                    }.onFailure {
                        android.widget.Toast.makeText(ctx, "Couldn't reach GitHub", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (checking) "Checking…" else "Check now", color = Space.Cyan) }
    found?.let { u -> UpdateDialog(u, onDismiss = { found = null }, openUrl = { runCatching { uri.openUri(it) } }) }
}
