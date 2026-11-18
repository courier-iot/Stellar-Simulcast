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
import com.stellar.simulcast.data.FORMATS
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
            TextButton(onClick = { Settings.setFilters(draft); onDismiss() }) { Text("Apply", color = Space.Cyan) }
        },
        dismissButton = {
            TextButton(onClick = { Settings.setFilters(Filters()); onDismiss() }) { Text("Clear all", color = Space.Magenta) }
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
                                    .clickable { Settings.setDisplay(display.copy(accent = p.id)) }
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
                Text("Show titles in", color = Space.Text)
                Spacer(Modifier.height(6.dp))
                ChipGrid(
                    TitleLang.entries.map { it.name },
                    setOf(lang.name),
                    label = { if (it == "ENGLISH") "English" else "Japanese (romaji)" },
                    perRow = 2,
                ) { Settings.setTitleLang(TitleLang.valueOf(it)) }
                Spacer(Modifier.height(10.dp))
                ToggleRow("24-hour clock", "Show 21:30 instead of 9:30 PM", display.use24h) {
                    Settings.setDisplay(display.copy(use24h = it))
                }
                ToggleRow("Larger text", "Bump all text up a size", display.largeText) {
                    Settings.setDisplay(display.copy(largeText = it))
                }
                ToggleRow("Compact lists", "Smaller rows so more fit on screen", display.compact) {
                    Settings.setDisplay(display.copy(compact = it))
                }
                ToggleRow("Spoiler-safe", "Hide synopses and episode titles until you tap them", display.spoilerSafe) {
                    Settings.setDisplay(display.copy(spoilerSafe = it))
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
                ) { Settings.setDisplay(display.copy(streamDelayMin = it.toInt())) }
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    "English links only",
                    "On show pages, only list streams AniList marks as English. AniList doesn't track which country a service works in, so this is the closest match.",
                    display.englishLinksOnly,
                ) { Settings.setDisplay(display.copy(englishLinksOnly = it)) }
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
                    Settings.setAlertPrefs(next)
                    EpisodeCheckWorker.schedule(ctx, next.intervalMin, replace = true)
                }
                Text(
                    "Android may run checks a little late to save battery.",
                    color = Space.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                )
                ToggleRow("Group alerts", "One notification when several episodes come out together", prefs.grouped) {
                    Settings.setAlertPrefs(prefs.copy(grouped = it))
                }
                ToggleRow("Sequel alerts", "Tell me when a starred show gets a new season or an air date", prefs.sequels) {
                    Settings.setAlertPrefs(prefs.copy(sequels = it))
                }
                ToggleRow("Daily digest", "One morning notification with what's out today", prefs.digest) {
                    val next = prefs.copy(digest = it)
                    Settings.setAlertPrefs(next)
                    DigestWorker.schedule(ctx, next, replace = true)
                }
                if (prefs.digest) {
                    HourStepper("At", prefs.digestHour) {
                        val next = prefs.copy(digestHour = it)
                        Settings.setAlertPrefs(next)
                        DigestWorker.schedule(ctx, next, replace = true)
                    }
                }
                ToggleRow("Quiet hours", "Hold alerts overnight and deliver them after", prefs.quiet) {
                    Settings.setAlertPrefs(prefs.copy(quiet = it))
                }
                if (prefs.quiet) {
                    HourStepper("From", prefs.quietStart) { Settings.setAlertPrefs(prefs.copy(quietStart = it)) }
                    HourStepper("Until", prefs.quietEnd) { Settings.setAlertPrefs(prefs.copy(quietEnd = it)) }
                }
            }

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
                    "Stored on this phone: only show IDs (your watchlist, hidden shows and episode progress) and these settings. No show data is saved.",
                    color = Space.Muted, fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { runCatching { uri.openUri("https://anilist.co") } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Visit AniList", color = Space.Cyan)
                }
            }
        }
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
