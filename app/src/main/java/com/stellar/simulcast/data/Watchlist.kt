package com.stellar.simulcast.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the app keeps on the phone about shows: only AniList IDs.
 * Starred shows, hidden shows, how many episodes you've watched, and alert bookkeeping.
 * No show info (titles, images, schedules) is ever saved.
 */
object Watchlist {
    private const val KEY_IDS = "watch_ids"
    private const val KEY_HIDDEN = "hidden_ids"
    private const val KEY_PROGRESS = "progress"
    private const val KEY_ALERTS = "alerts"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_SEEN_SEQUELS = "seen_sequels"
    private const val KEY_LAST_SEQUEL_CHECK = "last_sequel_check"

    private var prefs: SharedPreferences? = null

    private val _ids = MutableStateFlow<Set<Int>>(emptySet())
    val ids: StateFlow<Set<Int>> = _ids

    private val _hidden = MutableStateFlow<Set<Int>>(emptySet())
    val hidden: StateFlow<Set<Int>> = _hidden

    /** Show ID -> number of episodes watched. */
    private val _progress = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val progress: StateFlow<Map<Int, Int>> = _progress

    private val _alerts = MutableStateFlow(true)
    val alerts: StateFlow<Boolean> = _alerts

    @Synchronized
    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("simulcast", Context.MODE_PRIVATE)
        prefs = p
        _ids.value = p.intSet(KEY_IDS)
        _hidden.value = p.intSet(KEY_HIDDEN)
        _progress.value = p.getStringSet(KEY_PROGRESS, emptySet()).orEmpty().mapNotNull { entry ->
            val parts = entry.split(":")
            val id = parts.getOrNull(0)?.toIntOrNull()
            val ep = parts.getOrNull(1)?.toIntOrNull()
            if (id != null && ep != null) id to ep else null
        }.toMap()
        _alerts.value = p.getBoolean(KEY_ALERTS, true)
    }

    private fun SharedPreferences.intSet(key: String): Set<Int> =
        getStringSet(key, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    private fun saveInts(key: String, set: Set<Int>) {
        prefs?.edit()?.putStringSet(key, set.map { it.toString() }.toSet())?.apply()
    }

    /** Returns true if the show was added, false if it was removed. */
    fun toggle(id: Int): Boolean {
        val set = _ids.value.toMutableSet()
        val added = set.add(id)
        if (!added) set.remove(id)
        _ids.value = set
        saveInts(KEY_IDS, set)
        return added
    }

    fun toggleHidden(id: Int) {
        val set = _hidden.value.toMutableSet()
        if (!set.add(id)) set.remove(id)
        _hidden.value = set
        saveInts(KEY_HIDDEN, set)
    }

    fun setProgress(id: Int, watched: Int) {
        val map = _progress.value.toMutableMap()
        if (watched <= 0) map.remove(id) else map[id] = watched
        _progress.value = map
        prefs?.edit()?.putStringSet(KEY_PROGRESS, map.map { "${it.key}:${it.value}" }.toSet())?.apply()
    }

    fun setAlerts(on: Boolean) {
        _alerts.value = on
        prefs?.edit()?.putBoolean(KEY_ALERTS, on)?.apply()
    }

    var lastCheck: Long
        get() = prefs?.getLong(KEY_LAST_CHECK, 0L) ?: 0L
        set(value) {
            prefs?.edit()?.putLong(KEY_LAST_CHECK, value)?.apply()
        }

    var lastSequelCheck: Long
        get() = prefs?.getLong(KEY_LAST_SEQUEL_CHECK, 0L) ?: 0L
        set(value) {
            prefs?.edit()?.putLong(KEY_LAST_SEQUEL_CHECK, value)?.apply()
        }

    /** Sequel IDs already announced ("123"), and ones whose air date was announced ("123:d"). */
    var seenSequels: Set<String>
        get() = prefs?.getStringSet(KEY_SEEN_SEQUELS, emptySet()).orEmpty().toSet()
        set(value) {
            prefs?.edit()?.putStringSet(KEY_SEEN_SEQUELS, value)?.apply()
        }
}
