package com.stellar.simulcast.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class TitleLang { ENGLISH, ROMAJI }

val SERVICES = listOf("Crunchyroll", "Netflix", "HIDIVE", "Other")
private val MAIN_SERVICES = listOf("crunchyroll", "netflix", "hidive")

val FORMATS = listOf("TV", "TV_SHORT", "MOVIE", "ONA", "OVA", "SPECIAL")

val GENRES = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror",
    "Mahou Shoujo", "Mecha", "Music", "Mystery", "Psychological", "Romance",
    "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller",
)

data class Filters(
    val services: Set<String> = emptySet(),
    val formats: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
) {
    val count: Int get() = services.size + formats.size + genres.size
    val active: Boolean get() = count > 0

    fun matches(a: Anime): Boolean {
        if (formats.isNotEmpty() && a.format !in formats) return false
        if (genres.isNotEmpty() && a.genres.none { it in genres }) return false
        if (services.isNotEmpty()) {
            val hit = a.services.any { site ->
                val s = site.lowercase()
                val main = MAIN_SERVICES.firstOrNull { s.contains(it) }
                if (main != null) services.any { it.lowercase() == main } else "Other" in services
            }
            if (!hit) return false
        }
        return true
    }
}

data class AlertPrefs(
    val intervalMin: Int = 30,
    val grouped: Boolean = true,
    val quiet: Boolean = false,
    val quietStart: Int = 23,
    val quietEnd: Int = 8,
    val sequels: Boolean = true,
    val digest: Boolean = false,
    val digestHour: Int = 9,
) {
    fun isQuiet(hour: Int): Boolean {
        if (!quiet || quietStart == quietEnd) return false
        return if (quietStart < quietEnd) hour in quietStart until quietEnd
        else hour >= quietStart || hour < quietEnd
    }
}

data class DisplayPrefs(
    val accent: String = "NEON",
    val use24h: Boolean = false,
    val largeText: Boolean = false,
    val compact: Boolean = false,
    val spoilerSafe: Boolean = false,
    /** Minutes added to Japan air time, to match when it shows up on streaming sites. */
    val streamDelayMin: Int = 0,
    val englishLinksOnly: Boolean = false,
)

/**
 * Small settings kept on the phone alongside the watchlist.
 * Backed by Compose state so screens update the moment a setting changes.
 */
object Settings {
    private var prefs: SharedPreferences? = null

    var titleLang by mutableStateOf(TitleLang.ENGLISH)
        private set
    var filters by mutableStateOf(Filters())
        private set
    var alertPrefs by mutableStateOf(AlertPrefs())
        private set
    var display by mutableStateOf(DisplayPrefs())
        private set

    /** Stream delay in seconds, for time maths. */
    val delaySec: Long get() = display.streamDelayMin * 60L

    @Synchronized
    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("simulcast", Context.MODE_PRIVATE)
        prefs = p
        titleLang = runCatching { TitleLang.valueOf(p.getString("title_lang", null) ?: "ENGLISH") }
            .getOrDefault(TitleLang.ENGLISH)
        filters = Filters(
            services = p.getStringSet("f_services", emptySet()).orEmpty().toSet(),
            formats = p.getStringSet("f_formats", emptySet()).orEmpty().toSet(),
            genres = p.getStringSet("f_genres", emptySet()).orEmpty().toSet(),
        )
        alertPrefs = AlertPrefs(
            intervalMin = p.getInt("a_interval", 30),
            grouped = p.getBoolean("a_grouped", true),
            quiet = p.getBoolean("a_quiet", false),
            quietStart = p.getInt("a_quiet_start", 23),
            quietEnd = p.getInt("a_quiet_end", 8),
            sequels = p.getBoolean("a_sequels", true),
            digest = p.getBoolean("a_digest", false),
            digestHour = p.getInt("a_digest_hour", 9),
        )
        display = DisplayPrefs(
            accent = p.getString("d_accent", null) ?: "NEON",
            use24h = p.getBoolean("d_24h", false),
            largeText = p.getBoolean("d_large", false),
            compact = p.getBoolean("d_compact", false),
            spoilerSafe = p.getBoolean("d_spoiler", false),
            streamDelayMin = p.getInt("d_delay", 0),
            englishLinksOnly = p.getBoolean("d_en_links", false),
        )
    }

    fun setTitleLang(lang: TitleLang) {
        titleLang = lang
        prefs?.edit()?.putString("title_lang", lang.name)?.apply()
    }

    fun setFilters(f: Filters) {
        filters = f
        prefs?.edit()
            ?.putStringSet("f_services", f.services)
            ?.putStringSet("f_formats", f.formats)
            ?.putStringSet("f_genres", f.genres)
            ?.apply()
    }

    fun setAlertPrefs(a: AlertPrefs) {
        alertPrefs = a
        prefs?.edit()
            ?.putInt("a_interval", a.intervalMin)
            ?.putBoolean("a_grouped", a.grouped)
            ?.putBoolean("a_quiet", a.quiet)
            ?.putInt("a_quiet_start", a.quietStart)
            ?.putInt("a_quiet_end", a.quietEnd)
            ?.putBoolean("a_sequels", a.sequels)
            ?.putBoolean("a_digest", a.digest)
            ?.putInt("a_digest_hour", a.digestHour)
            ?.apply()
    }

    fun setDisplay(d: DisplayPrefs) {
        display = d
        prefs?.edit()
            ?.putString("d_accent", d.accent)
            ?.putBoolean("d_24h", d.use24h)
            ?.putBoolean("d_large", d.largeText)
            ?.putBoolean("d_compact", d.compact)
            ?.putBoolean("d_spoiler", d.spoilerSafe)
            ?.putInt("d_delay", d.streamDelayMin)
            ?.putBoolean("d_en_links", d.englishLinksOnly)
            ?.apply()
    }
}
