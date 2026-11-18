package com.stellar.simulcast.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellar.simulcast.data.FORMATS
import com.stellar.simulcast.data.Filters
import com.stellar.simulcast.data.GENRES
import com.stellar.simulcast.data.SERVICES
import com.stellar.simulcast.data.Settings
import com.stellar.simulcast.data.TitleLang
import com.stellar.simulcast.data.Watchlist
import com.stellar.simulcast.work.EpisodeCheckWorker

// ---------- Filters dialog ----------

@Composable
fun FilterDialog(onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(Settings.filters.value) }

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
                    "Applies to Schedule, Just aired, Season and Search. Nothing picked in a group means any.",
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

// ---------- Settings + about ----------

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val lang by Settings.titleLang.collectAsStateWithLifecycle()
    val alerts by Watchlist.alerts.collectAsStateWithLifecycle()
    val prefs by Settings.alertPrefs.collectAsStateWithLifecycle()

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

            // Display
            Section("Show titles in") {
                ChipGrid(
                    TitleLang.entries.map { it.name },
                    setOf(lang.name),
                    label = { if (it == "ENGLISH") "English" else "Japanese (romaji)" },
                    perRow = 2,
                ) { Settings.setTitleLang(TitleLang.valueOf(it)) }
                Text(
                    "Falls back to the other one when a show has no title in your pick.",
                    color = Space.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }

            // Alerts
            Section("Episode alerts") {
                ToggleRow("Alerts on", "Notify me when a starred show drops an episode", alerts) { Watchlist.setAlerts(it) }
                Spacer(Modifier.height(10.dp))
                Text("Check for new episodes every", color = Space.Text)
                Spacer(Modifier.height(6.dp))
                val intervals = listOf(15, 30, 60, 180)
                ChipGrid(
                    intervals.map { it.toString() },
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
                    color = Space.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(10.dp))
                ToggleRow("Group alerts", "One notification when several episodes come out together", prefs.grouped) {
                    Settings.setAlertPrefs(prefs.copy(grouped = it))
                }
                Spacer(Modifier.height(6.dp))
                ToggleRow("Quiet hours", "Hold alerts overnight and deliver them after", prefs.quiet) {
                    Settings.setAlertPrefs(prefs.copy(quiet = it))
                }
                if (prefs.quiet) {
                    Spacer(Modifier.height(6.dp))
                    HourStepper("From", prefs.quietStart) { Settings.setAlertPrefs(prefs.copy(quietStart = it)) }
                    HourStepper("Until", prefs.quietEnd) { Settings.setAlertPrefs(prefs.copy(quietEnd = it)) }
                }
            }

            // About
            Section("About") {
                Text("STELLAR SIMULCAST", color = Space.Cyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Text("Version ${appVersion(ctx)}", color = Space.Muted)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Release times, show info and streaming links come live from AniList. " +
                        "Stellar Simulcast isn't affiliated with AniList or any streaming service.",
                    color = Space.Text, fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Stored on this phone: only your watchlist and these settings. No show data is saved.",
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

private fun appVersion(ctx: Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
} catch (e: PackageManager.NameNotFoundException) {
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
    Row(verticalAlignment = Alignment.CenterVertically) {
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
        Text(hourLabel(hour), color = Space.Cyan, fontFamily = FontFamily.Monospace, fontSize = 16.sp, modifier = Modifier.width(72.dp))
        IconButton(onClick = { onChange((hour + 1) % 24) }) { Icon(Icons.Rounded.Add, "Later", tint = Space.Cyan) }
    }
}

private fun hourLabel(h: Int): String = when {
    h == 0 -> "12 AM"
    h < 12 -> "$h AM"
    h == 12 -> "12 PM"
    else -> "${h - 12} PM"
}
