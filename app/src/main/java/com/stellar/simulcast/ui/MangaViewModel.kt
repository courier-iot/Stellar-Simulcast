package com.stellar.simulcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stellar.simulcast.data.AniList
import com.stellar.simulcast.data.Anime
import com.stellar.simulcast.data.NovelBrowse
import com.stellar.simulcast.data.LatestChapter
import com.stellar.simulcast.data.Load
import com.stellar.simulcast.data.Manga
import com.stellar.simulcast.data.MangaBrowse
import com.stellar.simulcast.data.MangaDex
import com.stellar.simulcast.data.MangaLibrary
import com.stellar.simulcast.data.MangaPrefs
import com.stellar.simulcast.data.Paged
import com.stellar.simulcast.data.Settings
import com.stellar.simulcast.data.attempt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class MPaged<T>(
    val items: List<T> = emptyList(),
    val offset: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Manga screens' data, held in memory only. */
class MangaViewModel : ViewModel() {

    val latest = MutableStateFlow(MPaged<LatestChapter>())
    val browseMode = MutableStateFlow(MangaBrowse.POPULAR)
    val browse = MutableStateFlow(MPaged<Manga>())
    val query = MutableStateFlow("")
    val search = MutableStateFlow(MPaged<Manga>(hasMore = false))
    /** Matches hidden by your content-rating settings for the current search. */
    val hiddenCount = MutableStateFlow(0)
    val library = MutableStateFlow<Load<List<Manga>>>(Load.Loading)

    // Light novels (tracking only, from AniList).
    /** Non-null while Browse shows a light-novel list instead of a manga list. */
    val novelMode = MutableStateFlow<NovelBrowse?>(null)
    val novels = MutableStateFlow(MPaged<Anime>())
    val searchNovels = MutableStateFlow(false)
    val novelSearch = MutableStateFlow(MPaged<Anime>(hasMore = false))
    val novelLibrary = MutableStateFlow<Load<List<Anime>>>(Load.Loading)
    private var novelJob: Job? = null
    private var novelSearchJob: Job? = null

    fun setNovelMode(mode: NovelBrowse?) {
        novelMode.value = mode
        if (mode == null) return
        novelJob?.cancel()
        novels.value = MPaged()
        loadMoreNovels()
    }

    fun loadMoreNovels() {
        val mode = novelMode.value ?: return
        val cur = novels.value
        if (cur.loading || !cur.hasMore) return
        novels.value = cur.copy(loading = true, error = null)
        novelJob = viewModelScope.launch {
            // AniList pages are 1-based; offset here counts pages.
            novels.value = merge(cur, attempt { AniList.novels(mode, cur.offset + 1) }, 1) { it.id }
        }
    }

    fun setSearchNovels(on: Boolean) {
        searchNovels.value = on
        setQuery(query.value)
    }

    fun loadMoreNovelSearch() {
        val cur = novelSearch.value
        val q = query.value.trim()
        if (cur.loading || !cur.hasMore || q.isEmpty()) return
        novelSearch.value = cur.copy(loading = true, error = null)
        novelSearchJob = viewModelScope.launch {
            novelSearch.value = merge(cur, attempt { AniList.searchNovels(q, cur.offset + 1) }, 1) { it.id }
        }
    }

    fun loadNovelLibrary(ids: Set<Int>) {
        viewModelScope.launch {
            if (novelLibrary.value !is Load.Ready) novelLibrary.value = Load.Loading
            novelLibrary.value = attempt { if (ids.isEmpty()) emptyList() else AniList.novelsByIds(ids.toList()) }
        }
    }

    /** Manga ID -> chapters newer than where you left off. */
    val newCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

    private var latestJob: Job? = null
    private var browseJob: Job? = null
    private var searchJob: Job? = null
    private var libraryJob: Job? = null
    private var countsJob: Job? = null

    /** Ratings and languages decide what MangaDex returns, so lists restart when they change. */
    private var prefsKey = key(Settings.manga)

    private fun key(m: MangaPrefs) = m.ratings.sorted().joinToString() + "|" + m.languages.sorted().joinToString()

    fun applyPrefs(m: MangaPrefs) {
        val k = key(m)
        if (k == prefsKey) return
        prefsKey = k
        latestJob?.cancel(); browseJob?.cancel()
        latest.value = MPaged()
        browse.value = MPaged()
        loadMoreLatest()
        loadMoreBrowse()
        setQuery(query.value)
        newCounts.value = emptyMap()
    }

    // ---------- Latest ----------

    fun refreshLatest() {
        latestJob?.cancel()
        latest.value = MPaged()
        loadMoreLatest()
    }

    fun loadMoreLatest() {
        val cur = latest.value
        if (cur.loading || !cur.hasMore) return
        latest.value = cur.copy(loading = true, error = null)
        latestJob = viewModelScope.launch {
            latest.value = merge(cur, attempt { MangaDex.latest(cur.offset) }, 40) { it.chapter.id }
        }
    }

    // ---------- Browse ----------

    fun setBrowseMode(mode: MangaBrowse) {
        if (mode == browseMode.value && (browse.value.items.isNotEmpty() || browse.value.loading)) return
        browseJob?.cancel()
        browseMode.value = mode
        browse.value = MPaged()
        loadMoreBrowse()
    }

    fun loadMoreBrowse() {
        val cur = browse.value
        if (cur.loading || !cur.hasMore) return
        browse.value = cur.copy(loading = true, error = null)
        val mode = browseMode.value
        browseJob = viewModelScope.launch {
            browse.value = merge(cur, attempt { MangaDex.browse(mode, cur.offset) }, 30) { it.id }
        }
    }

    // ---------- Search ----------

    fun setQuery(q: String) {
        query.value = q
        searchJob?.cancel()
        novelSearchJob?.cancel()
        if (q.isBlank()) {
            search.value = MPaged(hasMore = false)
            novelSearch.value = MPaged(hasMore = false)
            return
        }
        if (searchNovels.value) {
            novelSearch.value = MPaged(loading = true)
            novelSearchJob = viewModelScope.launch {
                delay(450)
                novelSearch.value = merge(MPaged(), attempt { AniList.searchNovels(q.trim(), 1) }, 1) { it.id }
            }
            return
        }
        search.value = MPaged(loading = true)
        hiddenCount.value = 0
        searchJob = viewModelScope.launch {
            delay(450)
            search.value = merge(MPaged(), attempt { MangaDex.search(q.trim(), 0) }, 30) { it.id }
            hiddenCount.value = runCatching { MangaDex.hiddenByRatings(q.trim()) }.getOrDefault(0)
        }
    }

    fun loadMoreSearch() {
        val cur = search.value
        val q = query.value.trim()
        if (cur.loading || !cur.hasMore || q.isEmpty()) return
        search.value = cur.copy(loading = true, error = null)
        searchJob = viewModelScope.launch {
            search.value = merge(cur, attempt { MangaDex.search(q, cur.offset) }, 30) { it.id }
        }
    }

    // ---------- Library ----------

    fun loadLibrary(ids: Set<String>) {
        libraryJob?.cancel()
        libraryJob = viewModelScope.launch {
            if (library.value !is Load.Ready) library.value = Load.Loading
            val result = attempt { if (ids.isEmpty()) emptyList() else MangaDex.byIds(ids) }
            library.value = if (result is Load.Ready) Load.Ready(result.data.sortedBy { it.title.lowercase() }) else result
            loadNewCounts(ids)
        }
    }

    /** Counts chapters past your reading spot, one manga at a time to respect MangaDex's rate limit. */
    private fun loadNewCounts(ids: Set<String>) {
        countsJob?.cancel()
        val marks = MangaLibrary.marks.value
        if (!Settings.manga.trackReading) return
        countsJob = viewModelScope.launch {
            val out = newCounts.value.toMutableMap()
            for (id in ids.filter { it in marks }.take(40)) {
                val mark = marks[id] ?: continue
                val numbers = runCatching { MangaDex.chapterNumbers(id) }.getOrNull() ?: continue
                out[id] = numbers.count { it > mark.num }
                newCounts.value = out.toMap()
                delay(250)
            }
        }
    }

    private fun <T> merge(cur: MPaged<T>, result: Load<Paged<T>>, pageSize: Int, key: (T) -> Any): MPaged<T> = when (result) {
        is Load.Ready -> cur.copy(
            items = (cur.items + result.data.items).distinctBy(key),
            offset = cur.offset + pageSize,
            hasMore = result.data.hasMore,
            loading = false,
            error = null,
        )
        is Load.Error -> cur.copy(loading = false, error = result.message)
        Load.Loading -> cur
    }
}
