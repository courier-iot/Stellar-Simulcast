package com.stellar.simulcast.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class TitleLang { ENGLISH, ROMAJI }

val SERVICES = listOf("Crunchyroll", "Netflix", "HIDIVE", "Other")
private val MAIN_SERVICES = listOf("crunchyroll", "netflix", "hidive")

/** Services that can have their own stream delay. */
val DELAY_SERVICES = listOf("Crunchyroll", "Netflix", "HIDIVE")

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
    val pureBlack: Boolean = false,
    val use24h: Boolean = false,
    val largeText: Boolean = false,
    val compact: Boolean = false,
    val spoilerSafe: Boolean = false,
    /** Minutes added to Japan air time, to match when it shows up on streaming sites. */
    val streamDelayMin: Int = 0,
    /** Per-service delays (minutes). A service missing here uses [streamDelayMin]. */
    val serviceDelays: Map<String, Int> = emptyMap(),
    val englishLinksOnly: Boolean = false,
    val updateChecks: Boolean = true,
)

/** MangaDex content ratings, mildest first. The last two are 18+. */
val CONTENT_RATINGS = listOf("safe", "suggestive", "erotica", "pornographic")
val ADULT_RATINGS = setOf("erotica", "pornographic")

/** Chapter languages offered in Settings (MangaDex language codes). */
val MANGA_LANGUAGES = listOf(
    "en" to "English", "es-la" to "Spanish (LatAm)", "es" to "Spanish", "pt-br" to "Portuguese (BR)",
    "fr" to "French", "de" to "German", "it" to "Italian", "ru" to "Russian",
    "id" to "Indonesian", "vi" to "Vietnamese", "ja" to "Japanese", "ko" to "Korean",
)

enum class PageFilter(val label: String) { OFF("Off"), NIGHT("Night"), SEPIA("Sepia") }

data class MangaPrefs(
    /** When off, nothing about what you read is saved. */
    val trackReading: Boolean = true,
    val ratings: Set<String> = setOf("safe", "suggestive"),
    val languages: Set<String> = setOf("en"),
    val webtoon: Boolean = false,
    val rtl: Boolean = true,
    val dataSaver: Boolean = false,
    val chapterAlerts: Boolean = true,
    /** MangaDex tags as "id:name". Included tags must all match; excluded tags hide a series. */
    val includedTags: Set<String> = emptySet(),
    val excludedTags: Set<String> = emptySet(),
    /** Offline chapter downloads. Off by default since the app is online-first. */
    val downloads: Boolean = false,
    val pageFilter: PageFilter = PageFilter.OFF,
    val keepScreenOn: Boolean = true,
    val volumeKeys: Boolean = false,
    /** Two pages side by side when the phone is sideways (page mode only). */
    val spreads: Boolean = true,
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
    var manga by mutableStateOf(MangaPrefs())
        private set

    /** Default stream delay in seconds, for time maths. */
    val delaySec: Long get() = display.streamDelayMin * 60L

    /** [force] re-reads everything, used after importing from another phone. */
    @Synchronized
    fun init(ctx: Context, force: Boolean = false) {
        if (prefs != null && !force) return
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
            pureBlack = p.getBoolean("d_black", false),
            use24h = p.getBoolean("d_24h", false),
            largeText = p.getBoolean("d_large", false),
            compact = p.getBoolean("d_compact", false),
            spoilerSafe = p.getBoolean("d_spoiler", false),
            streamDelayMin = p.getInt("d_delay", 0),
            serviceDelays = p.getStringSet("d_service_delays", emptySet()).orEmpty().mapNotNull {
                val parts = it.split("=")
                val v = parts.getOrNull(1)?.toIntOrNull()
                if (parts.size == 2 && v != null) parts[0] to v else null
            }.toMap(),
            englishLinksOnly = p.getBoolean("d_en_links", false),
            updateChecks = p.getBoolean("d_updates", true),
        )
        manga = MangaPrefs(
            trackReading = p.getBoolean("m_track", true),
            ratings = p.getStringSet("m_ratings", null)?.toSet()?.takeIf { it.isNotEmpty() } ?: setOf("safe", "suggestive"),
            languages = p.getStringSet("m_langs", null)?.toSet()?.takeIf { it.isNotEmpty() } ?: setOf("en"),
            webtoon = p.getBoolean("m_webtoon", false),
            rtl = p.getBoolean("m_rtl", true),
            dataSaver = p.getBoolean("m_saver", false),
            chapterAlerts = p.getBoolean("m_alerts", true),
            includedTags = p.getStringSet("m_tags_in", emptySet()).orEmpty().toSet(),
            excludedTags = p.getStringSet("m_tags_out", emptySet()).orEmpty().toSet(),
            downloads = p.getBoolean("m_downloads", false),
            pageFilter = runCatching { PageFilter.valueOf(p.getString("m_filter", null) ?: "OFF") }.getOrDefault(PageFilter.OFF),
            keepScreenOn = p.getBoolean("m_screen_on", true),
            volumeKeys = p.getBoolean("m_volume_keys", false),
            spreads = p.getBoolean("m_spreads", true),
        )
    }

    fun updateTitleLang(lang: TitleLang) {
        titleLang = lang
        prefs?.edit()?.putString("title_lang", lang.name)?.apply()
    }

    fun updateFilters(f: Filters) {
        filters = f
        prefs?.edit()
            ?.putStringSet("f_services", f.services)
            ?.putStringSet("f_formats", f.formats)
            ?.putStringSet("f_genres", f.genres)
            ?.apply()
    }

    fun updateAlertPrefs(a: AlertPrefs) {
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

    fun updateDisplay(d: DisplayPrefs) {
        display = d
        prefs?.edit()
            ?.putString("d_accent", d.accent)
            ?.putBoolean("d_black", d.pureBlack)
            ?.putBoolean("d_24h", d.use24h)
            ?.putBoolean("d_large", d.largeText)
            ?.putBoolean("d_compact", d.compact)
            ?.putBoolean("d_spoiler", d.spoilerSafe)
            ?.putInt("d_delay", d.streamDelayMin)
            ?.putStringSet("d_service_delays", d.serviceDelays.map { "${it.key}=${it.value}" }.toSet())
            ?.putBoolean("d_en_links", d.englishLinksOnly)
            ?.putBoolean("d_updates", d.updateChecks)
            ?.apply()
    }

    fun updateManga(m: MangaPrefs) {
        manga = m
        prefs?.edit()
            ?.putBoolean("m_track", m.trackReading)
            ?.putStringSet("m_ratings", m.ratings)
            ?.putStringSet("m_langs", m.languages)
            ?.putBoolean("m_webtoon", m.webtoon)
            ?.putBoolean("m_rtl", m.rtl)
            ?.putBoolean("m_saver", m.dataSaver)
            ?.putBoolean("m_alerts", m.chapterAlerts)
            ?.putStringSet("m_tags_in", m.includedTags)
            ?.putStringSet("m_tags_out", m.excludedTags)
            ?.putBoolean("m_downloads", m.downloads)
            ?.putString("m_filter", m.pageFilter.name)
            ?.putBoolean("m_screen_on", m.keepScreenOn)
            ?.putBoolean("m_volume_keys", m.volumeKeys)
            ?.putBoolean("m_spreads", m.spreads)
            ?.apply()
    }

    /** Small bookkeeping values (update check time etc.). */
    fun getLong(key: String): Long = prefs?.getLong(key, 0L) ?: 0L
    fun putLong(key: String, v: Long) {
        prefs?.edit()?.putLong(key, v)?.apply()
    }
}
