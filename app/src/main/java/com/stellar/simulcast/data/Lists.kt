package com.stellar.simulcast.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ListStatus(val anime: String, val manga: String) {
    WATCHING("Watching", "Reading"),
    PLANNING("Plan to watch", "Plan to read"),
    ON_HOLD("On hold", "On hold"),
    DROPPED("Dropped", "Dropped"),
    COMPLETED("Completed", "Completed"),
}

/**
 * Your own lists and 1–10 scores, for anime/novels (AniList IDs) and manga (MangaDex IDs).
 * Only IDs and numbers are stored.
 */
object Lists {
    private var prefs: SharedPreferences? = null

    private val _animeStatus = MutableStateFlow<Map<Int, ListStatus>>(emptyMap())
    val animeStatus: StateFlow<Map<Int, ListStatus>> = _animeStatus
    private val _animeScore = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val animeScore: StateFlow<Map<Int, Int>> = _animeScore
    private val _mangaStatus = MutableStateFlow<Map<String, ListStatus>>(emptyMap())
    val mangaStatus: StateFlow<Map<String, ListStatus>> = _mangaStatus
    private val _mangaScore = MutableStateFlow<Map<String, Int>>(emptyMap())
    val mangaScore: StateFlow<Map<String, Int>> = _mangaScore

    @Synchronized
    fun init(ctx: Context, force: Boolean = false) {
        if (prefs != null && !force) return
        val p = ctx.applicationContext.getSharedPreferences("simulcast", Context.MODE_PRIVATE)
        prefs = p
        _animeStatus.value = read(p, "l_anime_status").mapNotNull { (k, v) ->
            val id = k.toIntOrNull()
            val s = runCatching { ListStatus.valueOf(v) }.getOrNull()
            if (id != null && s != null) id to s else null
        }.toMap()
        _animeScore.value = read(p, "l_anime_score").mapNotNull { (k, v) ->
            val id = k.toIntOrNull()
            val s = v.toIntOrNull()
            if (id != null && s != null) id to s else null
        }.toMap()
        _mangaStatus.value = read(p, "l_manga_status").mapNotNull { (k, v) ->
            runCatching { ListStatus.valueOf(v) }.getOrNull()?.let { k to it }
        }.toMap()
        _mangaScore.value = read(p, "l_manga_score").mapNotNull { (k, v) -> v.toIntOrNull()?.let { k to it } }.toMap()
    }

    private fun read(p: SharedPreferences, key: String): List<Pair<String, String>> =
        p.getStringSet(key, emptySet()).orEmpty().mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }

    private fun <K, V> save(key: String, map: Map<K, V>, value: (V) -> String) {
        prefs?.edit()?.putStringSet(key, map.map { "${it.key}=${value(it.value)}" }.toSet())?.apply()
    }

    fun setAnimeStatus(id: Int, status: ListStatus?) {
        val m = _animeStatus.value.toMutableMap()
        if (status == null) m.remove(id) else m[id] = status
        _animeStatus.value = m
        save("l_anime_status", m) { it.name }
    }

    fun setAnimeScore(id: Int, score: Int?) {
        val m = _animeScore.value.toMutableMap()
        if (score == null) m.remove(id) else m[id] = score.coerceIn(1, 10)
        _animeScore.value = m
        save("l_anime_score", m) { it.toString() }
    }

    fun setMangaStatus(id: String, status: ListStatus?) {
        val m = _mangaStatus.value.toMutableMap()
        if (status == null) m.remove(id) else m[id] = status
        _mangaStatus.value = m
        save("l_manga_status", m) { it.name }
    }

    fun setMangaScore(id: String, score: Int?) {
        val m = _mangaScore.value.toMutableMap()
        if (score == null) m.remove(id) else m[id] = score.coerceIn(1, 10)
        _mangaScore.value = m
        save("l_manga_score", m) { it.toString() }
    }
}
