package com.stellar.simulcast.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The only thing the app keeps on the phone: the AniList IDs of starred shows,
 * the alerts on/off switch, and when alerts last checked. No show data is stored.
 */
object Watchlist {
    private const val KEY_IDS = "watch_ids"
    private const val KEY_ALERTS = "alerts"
    private const val KEY_LAST_CHECK = "last_check"

    private var prefs: SharedPreferences? = null

    private val _ids = MutableStateFlow<Set<Int>>(emptySet())
    val ids: StateFlow<Set<Int>> = _ids

    private val _alerts = MutableStateFlow(true)
    val alerts: StateFlow<Boolean> = _alerts

    @Synchronized
    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("simulcast", Context.MODE_PRIVATE)
        prefs = p
        _ids.value = p.getStringSet(KEY_IDS, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        _alerts.value = p.getBoolean(KEY_ALERTS, true)
    }

    /** Returns true if the show was added, false if it was removed. */
    fun toggle(id: Int): Boolean {
        val set = _ids.value.toMutableSet()
        val added = set.add(id)
        if (!added) set.remove(id)
        _ids.value = set
        prefs?.edit()?.putStringSet(KEY_IDS, set.map { it.toString() }.toSet())?.apply()
        return added
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
}
