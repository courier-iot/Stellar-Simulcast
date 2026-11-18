package com.stellar.simulcast.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where you stopped in a manga. */
data class ReadMark(val chapterId: String, val chapter: String?, val page: Int, val groupId: String?, val at: Long) {
    val num: Double get() = chapter?.toDoubleOrNull() ?: -1.0

    fun encode(): String = listOf(chapterId, chapter.orEmpty(), page.toString(), groupId.orEmpty(), at.toString()).joinToString("|")

    companion object {
        fun decode(s: String): ReadMark? {
            val p = s.split("|")
            if (p.size < 5) return null
            return ReadMark(p[0], p[1].ifBlank { null }, p[2].toIntOrNull() ?: 0, p[3].ifBlank { null }, p[4].toLongOrNull() ?: 0L)
        }
    }
}

/**
 * Manga you follow and (only if reading tracking is on) where you left off.
 * Stores MangaDex IDs and numbers only, never manga data.
 */
object MangaLibrary {
    private const val KEY_FOLLOWS = "manga_follows"
    private const val KEY_MARKS = "manga_marks"
    private const val KEY_LAST_CHECK = "manga_last_check"
    private const val KEY_NOTIFIED = "manga_notified"

    private var prefs: SharedPreferences? = null

    private val _follows = MutableStateFlow<Set<String>>(emptySet())
    val follows: StateFlow<Set<String>> = _follows

    /** Manga ID -> where you left off. */
    private val _marks = MutableStateFlow<Map<String, ReadMark>>(emptyMap())
    val marks: StateFlow<Map<String, ReadMark>> = _marks

    @Synchronized
    fun init(ctx: Context, force: Boolean = false) {
        if (prefs != null && !force) return
        val p = ctx.applicationContext.getSharedPreferences("simulcast", Context.MODE_PRIVATE)
        prefs = p
        _follows.value = p.getStringSet(KEY_FOLLOWS, emptySet()).orEmpty().toSet()
        _marks.value = p.getStringSet(KEY_MARKS, emptySet()).orEmpty().mapNotNull { entry ->
            val idx = entry.indexOf('=')
            if (idx <= 0) null else ReadMark.decode(entry.substring(idx + 1))?.let { entry.substring(0, idx) to it }
        }.toMap()
    }

    /** Returns true if now followed. */
    fun toggleFollow(id: String): Boolean {
        val set = _follows.value.toMutableSet()
        val added = set.add(id)
        if (!added) set.remove(id)
        _follows.value = set
        prefs?.edit()?.putStringSet(KEY_FOLLOWS, set)?.apply()
        return added
    }

    /** Saves your place, but only while reading tracking is switched on. */
    fun saveMark(mangaId: String, chapter: Chapter, page: Int) {
        if (!Settings.manga.trackReading) return
        val map = _marks.value.toMutableMap()
        map[mangaId] = ReadMark(chapter.id, chapter.number, page, chapter.groupId, System.currentTimeMillis() / 1000)
        _marks.value = map
        saveMarks(map)
        Watchlist.markActive()
    }

    fun clearMark(mangaId: String) {
        val map = _marks.value.toMutableMap()
        map.remove(mangaId)
        _marks.value = map
        saveMarks(map)
    }

    /** Wipes all reading history (used when tracking is switched off). */
    fun clearAllMarks() {
        _marks.value = emptyMap()
        saveMarks(emptyMap())
    }

    private fun saveMarks(map: Map<String, ReadMark>) {
        prefs?.edit()?.putStringSet(KEY_MARKS, map.map { "${it.key}=${it.value.encode()}" }.toSet())?.apply()
    }

    var lastCheck: Long
        get() = prefs?.getLong(KEY_LAST_CHECK, 0L) ?: 0L
        set(value) {
            prefs?.edit()?.putLong(KEY_LAST_CHECK, value)?.apply()
        }

    /**
     * The latest new chapters found by alerts, newest first, for the manga widget.
     * Each entry: "mangaId|chapterId|label|epochSeconds". Only IDs and chapter numbers.
     */
    var recentNew: List<String>
        get() = prefs?.getString("manga_recent_new", null)?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
        set(value) {
            prefs?.edit()?.putString("manga_recent_new", value.take(12).joinToString("\n"))?.apply()
        }

    /** Chapter IDs we've already sent an alert for (keeps the most recent few hundred). */
    var notified: Set<String>
        get() = prefs?.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty().toSet()
        set(value) {
            prefs?.edit()?.putStringSet(KEY_NOTIFIED, value.toList().takeLast(400).toSet())?.apply()
        }
}
