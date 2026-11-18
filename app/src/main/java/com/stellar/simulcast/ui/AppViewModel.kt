package com.stellar.simulcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stellar.simulcast.data.AiringEp
import com.stellar.simulcast.data.AniList
import com.stellar.simulcast.data.Anime
import com.stellar.simulcast.data.Filters
import com.stellar.simulcast.data.Load
import com.stellar.simulcast.data.Settings
import com.stellar.simulcast.data.Paged
import com.stellar.simulcast.data.SeasonKey
import com.stellar.simulcast.data.attempt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class PagedList(
    val items: List<Anime> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Holds what's on screen in memory only, so switching tabs doesn't refetch every time. */
class AppViewModel : ViewModel() {

    val schedule = MutableStateFlow<Load<List<AiringEp>>>(Load.Loading)
    val recent = MutableStateFlow<Load<List<AiringEp>>>(Load.Loading)
    val watchlist = MutableStateFlow<Load<List<Anime>>>(Load.Loading)

    val seasonKey = MutableStateFlow(SeasonKey.current())
    val season = MutableStateFlow(PagedList())

    val query = MutableStateFlow("")
    val search = MutableStateFlow(PagedList(hasMore = false))

    private var scheduleJob: Job? = null
    private var recentJob: Job? = null
    private var watchJob: Job? = null
    private var seasonJob: Job? = null
    private var searchJob: Job? = null

    private var scheduleAt = 0L
    private var recentAt = 0L

    private fun nowSec() = System.currentTimeMillis() / 1000

    fun loadSchedule(force: Boolean = false) {
        if (scheduleJob?.isActive == true) return
        if (!force && schedule.value is Load.Ready && nowSec() - scheduleAt < 600) return
        scheduleJob = viewModelScope.launch {
            schedule.value = Load.Loading
            val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
            val result = attempt { AniList.schedule(start - 1, start + 7 * 86400L) }
            if (result is Load.Ready) scheduleAt = nowSec()
            schedule.value = result
        }
    }

    fun loadRecent(force: Boolean = false) {
        if (recentJob?.isActive == true) return
        if (!force && recent.value is Load.Ready && nowSec() - recentAt < 300) return
        recentJob = viewModelScope.launch {
            recent.value = Load.Loading
            val result = attempt { AniList.recent(48) }
            if (result is Load.Ready) recentAt = nowSec()
            recent.value = result
        }
    }

    fun loadWatchlist(ids: Set<Int>) {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            if (watchlist.value !is Load.Ready) watchlist.value = Load.Loading
            watchlist.value = attempt {
                AniList.byIds(ids.toList()).sortedWith(compareBy<Anime, Long?>(nullsLast()) { it.nextAiringAt })
            }
        }
    }

    private var seasonFilters = Settings.filters.value
    private var searchFilters = Settings.filters.value

    fun setSeason(key: SeasonKey) {
        if (key == seasonKey.value && (season.value.items.isNotEmpty() || season.value.loading)) return
        seasonJob?.cancel()
        seasonKey.value = key
        season.value = PagedList()
        loadMoreSeason()
    }

    /** Called when filters change; restarts the season list and the current search. */
    fun applyFilters(f: Filters) {
        if (f != seasonFilters) {
            seasonFilters = f
            seasonJob?.cancel()
            season.value = PagedList()
            loadMoreSeason()
        }
        if (f != searchFilters) {
            searchFilters = f
            setQuery(query.value)
        }
    }

    fun loadMoreSeason() {
        val cur = season.value
        if (cur.loading || !cur.hasMore) return
        season.value = cur.copy(loading = true, error = null)
        val key = seasonKey.value
        val f = seasonFilters
        seasonJob = viewModelScope.launch {
            season.value = merge(cur, attempt { AniList.season(key, cur.page + 1, f) })
        }
    }

    fun setQuery(q: String) {
        query.value = q
        searchJob?.cancel()
        if (q.isBlank()) {
            search.value = PagedList(hasMore = false)
            return
        }
        search.value = PagedList(loading = true)
        searchJob = viewModelScope.launch {
            delay(450) // wait for typing to pause
            search.value = merge(PagedList(), attempt { AniList.search(q.trim(), 1, searchFilters) })
        }
    }

    fun loadMoreSearch() {
        val cur = search.value
        val q = query.value.trim()
        if (cur.loading || !cur.hasMore || q.isEmpty()) return
        search.value = cur.copy(loading = true, error = null)
        searchJob = viewModelScope.launch {
            search.value = merge(cur, attempt { AniList.search(q, cur.page + 1, searchFilters) })
        }
    }

    private fun merge(cur: PagedList, result: Load<Paged<Anime>>): PagedList = when (result) {
        is Load.Ready -> cur.copy(
            items = (cur.items + result.data.items).distinctBy { it.id },
            page = cur.page + 1,
            hasMore = result.data.hasMore,
            loading = false,
            error = null,
        )
        is Load.Error -> cur.copy(loading = false, error = result.message)
        Load.Loading -> cur
    }
}
