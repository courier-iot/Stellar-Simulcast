package com.stellar.simulcast.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
) {
    fun isQuiet(hour: Int): Boolean {
        if (!quiet || quietStart == quietEnd) return false
        return if (quietStart < quietEnd) hour in quietStart until quietEnd
        else hour >= quietStart || hour < quietEnd
    }
}

/** Small display and alert settings. Kept on the phone alongside the watchlist. */
object Settings {
    private var prefs: SharedPreferences? = null

    private val _titleLang = MutableStateFlow(TitleLang.ENGLISH)
    val titleLang: StateFlow<TitleLang> = _titleLang

    private val _filters = MutableStateFlow(Filters())
    val filters: StateFlow<Filters> = _filters

    private val _alertPrefs = MutableStateFlow(AlertPrefs())
    val alertPrefs: StateFlow<AlertPrefs> = _alertPrefs

    @Synchronized
    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("simulcast", Context.MODE_PRIVATE)
        prefs = p
        _titleLang.value = runCatching { TitleLang.valueOf(p.getString("title_lang", null) ?: "ENGLISH") }
            .getOrDefault(TitleLang.ENGLISH)
        _filters.value = Filters(
            services = p.getStringSet("f_services", emptySet()).orEmpty().toSet(),
            formats = p.getStringSet("f_formats", emptySet()).orEmpty().toSet(),
            genres = p.getStringSet("f_genres", emptySet()).orEmpty().toSet(),
        )
        _alertPrefs.value = AlertPrefs(
            intervalMin = p.getInt("a_interval", 30),
            grouped = p.getBoolean("a_grouped", true),
            quiet = p.getBoolean("a_quiet", false),
            quietStart = p.getInt("a_quiet_start", 23),
            quietEnd = p.getInt("a_quiet_end", 8),
        )
    }

    fun setTitleLang(lang: TitleLang) {
        _titleLang.value = lang
        prefs?.edit()?.putString("title_lang", lang.name)?.apply()
    }

    fun setFilters(f: Filters) {
        _filters.value = f
        prefs?.edit()
            ?.putStringSet("f_services", f.services)
            ?.putStringSet("f_formats", f.formats)
            ?.putStringSet("f_genres", f.genres)
            ?.apply()
    }

    fun setAlertPrefs(a: AlertPrefs) {
        _alertPrefs.value = a
        prefs?.edit()
            ?.putInt("a_interval", a.intervalMin)
            ?.putBoolean("a_grouped", a.grouped)
            ?.putBoolean("a_quiet", a.quiet)
            ?.putInt("a_quiet_start", a.quietStart)
            ?.putInt("a_quiet_end", a.quietEnd)
            ?.apply()
    }
}
