package com.stellar.simulcast.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Live data from AniList's public GraphQL API (no account or key needed). Nothing is cached on disk. */
object AniList {
    private const val ENDPOINT = "https://graphql.anilist.co"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    // GraphQL variables start with '$', which Kotlin treats as a string template,
    // so the queries below use '%' and it gets swapped here.
    private fun gql(s: String) = s.replace('%', '$')

    private const val MEDIA = """
        id isAdult type format episodes volumes chapters status averageScore genres siteUrl
        title { romaji english }
        coverImage { large }
        nextAiringEpisode { episode airingAt }
        externalLinks { site url type language }
    """

    /** Just enough to draw a poster tile. Keeps big queries under AniList's complexity limit. */
    private const val SMALL = """
        id isAdult type format episodes status siteUrl
        title { romaji english }
        coverImage { large }
    """

    private val AIRING = gql(
        """
        query (%start: Int, %end: Int, %page: Int, %sort: [AiringSort]) {
          Page(page: %page, perPage: 50) {
            pageInfo { hasNextPage }
            airingSchedules(airingAt_greater: %start, airingAt_lesser: %end, sort: %sort) {
              id episode airingAt
              media { $MEDIA }
            }
          }
        }
        """
    )

    private val AIRING_IDS = gql(
        """
        query (%start: Int, %end: Int, %page: Int, %sort: [AiringSort], %ids: [Int]) {
          Page(page: %page, perPage: 50) {
            pageInfo { hasNextPage }
            airingSchedules(airingAt_greater: %start, airingAt_lesser: %end, sort: %sort, mediaId_in: %ids) {
              id episode airingAt
              media { $MEDIA }
            }
          }
        }
        """
    )

    private val SEASON = gql(
        """
        query (%season: MediaSeason, %year: Int, %page: Int, %genres: [String], %formats: [MediaFormat]) {
          Page(page: %page, perPage: 30) {
            pageInfo { hasNextPage }
            media(season: %season, seasonYear: %year, type: ANIME, isAdult: false, sort: POPULARITY_DESC,
                  genre_in: %genres, format_in: %formats) { $MEDIA }
          }
        }
        """
    )

    private val TRENDING = gql(
        """
        query (%page: Int, %genres: [String], %formats: [MediaFormat]) {
          Page(page: %page, perPage: 30) {
            pageInfo { hasNextPage }
            media(type: ANIME, isAdult: false, status: RELEASING, sort: [TRENDING_DESC, POPULARITY_DESC],
                  genre_in: %genres, format_in: %formats) { $MEDIA }
          }
        }
        """
    )

    private val BINGE = gql(
        """
        query (%page: Int, %after: FuzzyDateInt, %genres: [String], %formats: [MediaFormat]) {
          Page(page: %page, perPage: 30) {
            pageInfo { hasNextPage }
            media(type: ANIME, isAdult: false, status: FINISHED, endDate_greater: %after, sort: [POPULARITY_DESC],
                  genre_in: %genres, format_in: %formats) { $MEDIA }
          }
        }
        """
    )

    private val SEARCH = gql(
        """
        query (%q: String, %page: Int, %genres: [String], %formats: [MediaFormat]) {
          Page(page: %page, perPage: 30) {
            pageInfo { hasNextPage }
            media(search: %q, type: ANIME, isAdult: false, sort: SEARCH_MATCH,
                  genre_in: %genres, format_in: %formats) { $MEDIA }
          }
        }
        """
    )

    private val BY_IDS = gql(
        """
        query (%ids: [Int], %page: Int) {
          Page(page: %page, perPage: 50) {
            pageInfo { hasNextPage }
            media(id_in: %ids, type: ANIME) { $MEDIA }
          }
        }
        """
    )

    private val SEQUELS = gql(
        """
        query (%ids: [Int]) {
          Page(page: 1, perPage: 50) {
            media(id_in: %ids, type: ANIME) {
              id title { romaji english }
              relations {
                edges {
                  relationType(version: 2)
                  node { type startDate { year month day } $MEDIA }
                }
              }
            }
          }
        }
        """
    )

    private val DETAIL = gql(
        """
        query (%id: Int) {
          Media(id: %id) {
            $MEDIA
            description(asHtml: false)
            bannerImage season seasonYear duration source
            studios(isMain: true) { nodes { id name } }
            trailer { id site }
            airingSchedule(page: 1, perPage: 50) { pageInfo { hasNextPage } nodes { episode airingAt } }
            streamingEpisodes { title url }
            characters(sort: [ROLE, RELEVANCE], perPage: 10) {
              edges {
                role
                node { name { full } image { medium } }
                voiceActors(language: JAPANESE) { name { full } }
              }
            }
            relations { edges { relationType(version: 2) node { $SMALL } } }
            recommendations(sort: RATING_DESC, perPage: 10) { nodes { mediaRecommendation { $SMALL } } }
            trends(sort: DATE_DESC, perPage: 30) { nodes { date averageScore } }
          }
        }
        """
    )

    private val AIRING_PAGE = gql(
        """
        query (%id: Int, %page: Int) {
          Media(id: %id) {
            airingSchedule(page: %page, perPage: 50) { pageInfo { hasNextPage } nodes { episode airingAt } }
          }
        }
        """
    )

    private val STUDIO = gql(
        """
        query (%id: Int, %page: Int) {
          Studio(id: %id) {
            id name
            media(sort: START_DATE_DESC, isMain: true, page: %page, perPage: 30) {
              pageInfo { hasNextPage }
              nodes { $MEDIA }
            }
          }
        }
        """
    )

    private val NOVELS = gql(
        """
        query (%page: Int, %sort: [MediaSort], %q: String) {
          Page(page: %page, perPage: 30) {
            pageInfo { hasNextPage }
            media(type: MANGA, format: NOVEL, isAdult: false, sort: %sort, search: %q) { $MEDIA }
          }
        }
        """
    )

    private val BY_IDS_ANY = gql(
        """
        query (%ids: [Int], %page: Int) {
          Page(page: %page, perPage: 50) {
            pageInfo { hasNextPage }
            media(id_in: %ids) { $MEDIA }
          }
        }
        """
    )

    private val MANGA_ADAPTATIONS = gql(
        """
        query (%id: Int) {
          Media(id: %id, type: MANGA) {
            relations {
              edges {
                relationType(version: 2)
                node { type $MEDIA }
              }
            }
          }
        }
        """
    )

    suspend fun studio(id: Int, page: Int): StudioPage {
        val st = post(STUDIO, JSONObject().put("id", id).put("page", page)).getJSONObject("Studio")
        val media = st.optJSONObject("media")
        return StudioPage(
            id = id,
            name = st.str("name") ?: "Studio",
            shows = media?.optJSONArray("nodes").objects().map { parseAnime(it) }.filter { !it.isAdult },
            hasMore = media?.optJSONObject("pageInfo")?.optBoolean("hasNextPage") == true,
        )
    }

    suspend fun novels(mode: NovelBrowse, page: Int): Paged<Anime> {
        val sort = when (mode) {
            NovelBrowse.POPULAR -> "POPULARITY_DESC"
            NovelBrowse.TRENDING -> "TRENDING_DESC"
            NovelBrowse.RECENT -> "START_DATE_DESC"
        }
        return mediaPage(NOVELS, JSONObject().put("page", page).put("sort", JSONArray().put(sort)).put("q", JSONObject.NULL))
    }

    suspend fun searchNovels(q: String, page: Int): Paged<Anime> =
        mediaPage(NOVELS, JSONObject().put("page", page).put("sort", JSONArray().put("SEARCH_MATCH")).put("q", q))

    /** Light novels among the given AniList IDs. */
    suspend fun novelsByIds(ids: List<Int>): List<Anime> {
        val out = mutableListOf<Anime>()
        for (chunk in ids.chunked(50)) {
            out += mediaPage(BY_IDS_ANY, JSONObject().put("ids", JSONArray(chunk)).put("page", 1), keepAdult = true)
                .items.filter { it.isNovel }
        }
        return out
    }

    /** Anime made from an AniList manga (used for "Watch the anime" on manga pages). */
    suspend fun animeForManga(aniListMangaId: Int): List<Anime> {
        val m = post(MANGA_ADAPTATIONS, JSONObject().put("id", aniListMangaId)).getJSONObject("Media")
        return m.optJSONObject("relations")?.optJSONArray("edges").objects()
            .mapNotNull { e -> e.optJSONObject("node")?.takeIf { it.optString("type") == "ANIME" } }
            .map { parseAnime(it) }
            .filter { !it.isAdult }
            .distinctBy { it.id }
    }

    // Recent answers are kept in memory for a few minutes, so tabs, the widget and
    // background checks don't spend AniList's ~30 requests/minute on the same thing.
    private const val CACHE_MS = 5 * 60_000L
    private val cache = object : LinkedHashMap<String, Pair<Long, JSONObject>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, JSONObject>>?) = size > 80
    }

    fun clearCache() = synchronized(cache) { cache.clear() }

    private suspend fun post(query: String, vars: JSONObject): JSONObject {
        val key = query.hashCode().toString() + vars.toString()
        synchronized(cache) {
            cache[key]?.let { (at, data) -> if (System.currentTimeMillis() - at < CACHE_MS) return data }
        }
        var attempt = 0
        while (true) {
            attempt++
            val result = withContext(Dispatchers.IO) { send(query, vars) }
            when (result) {
                is Sent.Ok -> {
                    synchronized(cache) { cache[key] = System.currentTimeMillis() to result.data }
                    return result.data
                }
                // Rate limited: wait what AniList asks (up to a minute) and try again, at most twice.
                is Sent.Limited -> {
                    if (attempt >= 3 || result.waitSec > 60) {
                        throw IOException("AniList is busy right now. Try again in a minute.")
                    }
                    kotlinx.coroutines.delay(result.waitSec * 1000L + 500)
                }
            }
        }
    }

    private sealed interface Sent {
        data class Ok(val data: JSONObject) : Sent
        data class Limited(val waitSec: Int) : Sent
    }

    private fun send(query: String, vars: JSONObject): Sent {
        val body = JSONObject().put("query", query).put("variables", vars).toString().toRequestBody(jsonType)
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Accept", "application/json")
            .post(body)
            .build()
        return client.newCall(request).execute().use { resp ->
            if (resp.code == 429) return@use Sent.Limited(resp.header("Retry-After")?.toIntOrNull() ?: 20)
            val text = resp.body?.string().orEmpty()
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw IOException("AniList error (${resp.code})")
            }
            val data = json.optJSONObject("data")
            if (!resp.isSuccessful || data == null) {
                val msg = json.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                throw IOException(if (msg.isNullOrBlank()) "AniList error (${resp.code})" else msg)
            }
            Sent.Ok(data)
        }
    }

    // ---------- public calls ----------

    /** Every episode airing between [start] and [end] (epoch seconds), oldest first. */
    suspend fun schedule(start: Long, end: Long): List<AiringEp> = airing(start, end, "TIME", null, 10)

    /** Episodes released in the last [hours], newest first. */
    suspend fun recent(hours: Int): List<AiringEp> {
        val now = System.currentTimeMillis() / 1000
        return airing(now - hours * 3600L, now + 1, "TIME_DESC", null, 6)
    }

    /** Episodes of the given shows that aired after [from] and up to [to]. Used for alerts. */
    suspend fun airedFor(ids: List<Int>, from: Long, to: Long): List<AiringEp> =
        if (ids.isEmpty()) emptyList() else airing(from, to, "TIME", ids, 4)

    suspend fun browse(mode: BrowseMode, page: Int, f: Filters): Paged<Anime> = when (mode) {
        BrowseMode.Trending -> mediaPage(TRENDING, JSONObject().put("page", page).withFilters(f))
        BrowseMode.BingeReady -> {
            val d = LocalDate.now().minusDays(120)
            val after = d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
            mediaPage(BINGE, JSONObject().put("page", page).put("after", after).withFilters(f))
        }
        is BrowseMode.OfSeason -> mediaPage(
            SEASON,
            JSONObject().put("season", mode.key.season.name).put("year", mode.key.year).put("page", page).withFilters(f),
        )
    }

    suspend fun search(q: String, page: Int, f: Filters): Paged<Anime> =
        mediaPage(SEARCH, JSONObject().put("q", q).put("page", page).withFilters(f))

    suspend fun byIds(ids: List<Int>): List<Anime> {
        val out = mutableListOf<Anime>()
        for (chunk in ids.chunked(50)) {
            out += mediaPage(BY_IDS, JSONObject().put("ids", JSONArray(chunk)).put("page", 1), keepAdult = true).items
        }
        return out
    }

    /** Sequels (announced or airing) of the given shows. */
    suspend fun sequels(ids: List<Int>, lang: TitleLang): List<SequelNews> {
        val out = mutableListOf<SequelNews>()
        for (chunk in ids.chunked(50)) {
            val page = post(SEQUELS, JSONObject().put("ids", JSONArray(chunk))).getJSONObject("Page")
            for (m in page.optJSONArray("media").objects()) {
                val t = m.optJSONObject("title")
                val parentTitle = when (lang) {
                    TitleLang.ENGLISH -> t?.str("english") ?: t?.str("romaji")
                    TitleLang.ROMAJI -> t?.str("romaji") ?: t?.str("english")
                } ?: "A show you starred"
                for (edge in m.optJSONObject("relations")?.optJSONArray("edges").objects()) {
                    if (edge.optString("relationType") != "SEQUEL") continue
                    val node = edge.optJSONObject("node") ?: continue
                    if (node.optString("type") != "ANIME") continue
                    val sequel = parseAnime(node)
                    if (sequel.status != "NOT_YET_RELEASED" && sequel.status != "RELEASING") continue
                    out += SequelNews(parentTitle, sequel, fuzzyDate(node.optJSONObject("startDate")))
                }
            }
        }
        return out
    }

    suspend fun detail(id: Int): AnimeDetail {
        val m = post(DETAIL, JSONObject().put("id", id)).getJSONObject("Media")
        val studioNodes = m.optJSONObject("studios")?.optJSONArray("nodes").objects()
        val studios = studioNodes.mapNotNull { it.str("name") }
        val studioId = studioNodes.firstOrNull()?.int("id")

        // Long-running shows have more than 50 air dates; fetch the rest page by page.
        val airing = m.optJSONObject("airingSchedule")
        val nodes = airing?.optJSONArray("nodes").objects().toMutableList()
        var more = airing?.optJSONObject("pageInfo")?.optBoolean("hasNextPage") == true
        var page = 2
        while (more && page <= 30) {
            val next = runCatching {
                post(AIRING_PAGE, JSONObject().put("id", id).put("page", page))
                    .getJSONObject("Media").optJSONObject("airingSchedule")
            }.getOrNull() ?: break
            nodes += next.optJSONArray("nodes").objects()
            more = next.optJSONObject("pageInfo")?.optBoolean("hasNextPage") == true
            page++
        }

        val recommendations = m.optJSONObject("recommendations")?.optJSONArray("nodes").objects()
            .mapNotNull { it.optJSONObject("mediaRecommendation") }
            .map { parseAnime(it) }
            .filter { !it.isAdult }
        val links = m.optJSONArray("externalLinks").objects()
            .filter { it.optString("type") == "STREAMING" }
            .mapNotNull { l ->
                val url = l.str("url") ?: return@mapNotNull null
                StreamLink(l.str("site") ?: "Stream", url, l.str("language"))
            }
            .distinctBy { it.site + it.language }

        val trailer = m.optJSONObject("trailer")?.let { t ->
            val tid = t.str("id") ?: return@let null
            when (t.str("site")?.lowercase()) {
                "youtube" -> "https://www.youtube.com/watch?v=$tid"
                "dailymotion" -> "https://www.dailymotion.com/video/$tid"
                else -> null
            }
        }

        val cast = m.optJSONObject("characters")?.optJSONArray("edges").objects().mapNotNull { e ->
            val node = e.optJSONObject("node") ?: return@mapNotNull null
            val name = node.optJSONObject("name")?.str("full") ?: return@mapNotNull null
            CastMember(
                character = name,
                characterImage = node.optJSONObject("image")?.str("medium"),
                role = e.str("role"),
                voiceActor = e.optJSONArray("voiceActors").objects().firstOrNull()?.optJSONObject("name")?.str("full"),
            )
        }

        val related = m.optJSONObject("relations")?.optJSONArray("edges").objects().mapNotNull { e ->
            val node = e.optJSONObject("node") ?: return@mapNotNull null
            val rel = e.str("relationType") ?: return@mapNotNull null
            val isManga = node.optString("type") == "MANGA"
            val label = relationLabel(rel) + if (isManga) " (manga)" else ""
            Related(parseAnime(node), label)
        }.sortedBy { relationOrder(it.relation) }

        return AnimeDetail(
            anime = parseAnime(m),
            description = cleanDescription(m.str("description")),
            banner = m.str("bannerImage"),
            studios = studios,
            season = m.str("season"),
            seasonYear = m.int("seasonYear"),
            duration = m.int("duration"),
            source = m.str("source"),
            links = links,
            episodeList = episodeList(nodes, m),
            trailerUrl = trailer,
            cast = cast,
            related = related,
            scoreChange = scoreChange(m.optJSONObject("trends")?.optJSONArray("nodes").objects()),
            recommendations = recommendations,
            studioId = studioId,
        )
    }

    // ---------- helpers ----------

    private suspend fun airing(start: Long, end: Long, sort: String, ids: List<Int>?, maxPages: Int): List<AiringEp> {
        val out = mutableListOf<AiringEp>()
        var page = 1
        while (page <= maxPages) {
            val vars = JSONObject()
                .put("start", start)
                .put("end", end)
                .put("page", page)
                .put("sort", JSONArray().put(sort))
            if (ids != null) vars.put("ids", JSONArray(ids))
            val p = post(if (ids != null) AIRING_IDS else AIRING, vars).getJSONObject("Page")
            for (s in p.optJSONArray("airingSchedules").objects()) {
                val m = s.optJSONObject("media") ?: continue
                val anime = parseAnime(m)
                if (!anime.isAdult) out += AiringEp(s.getInt("id"), s.optInt("episode"), s.optLong("airingAt"), anime)
            }
            if (p.optJSONObject("pageInfo")?.optBoolean("hasNextPage") != true) break
            page++
        }
        return out
    }

    private suspend fun mediaPage(query: String, vars: JSONObject, keepAdult: Boolean = false): Paged<Anime> {
        val p = post(query, vars).getJSONObject("Page")
        val items = p.optJSONArray("media").objects().map { parseAnime(it) }.filter { keepAdult || !it.isAdult }
        return Paged(items, p.optJSONObject("pageInfo")?.optBoolean("hasNextPage") == true)
    }

    /** Genre and format filters run on AniList's side; null means "any". */
    private fun JSONObject.withFilters(f: Filters): JSONObject = this
        .put("genres", if (f.genres.isEmpty()) JSONObject.NULL else JSONArray(f.genres.toList()))
        .put("formats", if (f.formats.isEmpty()) JSONObject.NULL else JSONArray(f.formats.toList()))

    private fun parseAnime(o: JSONObject): Anime {
        val t = o.optJSONObject("title")
        val next = o.optJSONObject("nextAiringEpisode")
        val genres = o.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        val services = o.optJSONArray("externalLinks").objects()
            .filter { it.optString("type") == "STREAMING" }
            .mapNotNull { it.str("site") }
            .distinct()
        return Anime(
            id = o.getInt("id"),
            english = t?.str("english"),
            romaji = t?.str("romaji"),
            services = services,
            cover = o.optJSONObject("coverImage")?.str("large"),
            format = o.str("format"),
            episodes = o.int("episodes"),
            status = o.str("status"),
            score = o.int("averageScore"),
            genres = genres,
            nextEpisode = next?.int("episode"),
            nextAiringAt = next?.long("airingAt"),
            siteUrl = o.str("siteUrl"),
            isAdult = o.optBoolean("isAdult", false),
            type = o.str("type") ?: "ANIME",
            volumes = o.int("volumes"),
            chapters = o.int("chapters"),
        )
    }

    private val episodeTitle = Regex("""^Episode\s+(\d+)\s*[-:–]?\s*(.*)$""", RegexOption.IGNORE_CASE)

    private fun episodeList(airingNodes: List<JSONObject>, m: JSONObject): List<EpisodeInfo> {
        val dates = airingNodes.associate { it.optInt("episode") to it.optLong("airingAt") }
        val titles = mutableMapOf<Int, Pair<String?, String?>>()
        for (e in m.optJSONArray("streamingEpisodes").objects()) {
            val raw = e.str("title") ?: continue
            val match = episodeTitle.find(raw) ?: continue
            val n = match.groupValues[1].toIntOrNull() ?: continue
            if (n !in titles) titles[n] = match.groupValues[2].trim().ifBlank { null } to e.str("url")
        }
        return (dates.keys + titles.keys).filter { it > 0 }.toSortedSet().map { n ->
            EpisodeInfo(n, dates[n], titles[n]?.first, titles[n]?.second)
        }
    }

    private fun scoreChange(nodes: List<JSONObject>): Int? {
        val scored = nodes.filter { !it.isNull("averageScore") }
            .map { it.optLong("date") to it.optInt("averageScore") }
            .sortedByDescending { it.first }
        val latest = scored.firstOrNull() ?: return null
        val older = scored.firstOrNull { it.first <= latest.first - 25 * 86400 } ?: scored.lastOrNull() ?: return null
        if (older === latest) return null
        return latest.second - older.second
    }

    private fun fuzzyDate(o: JSONObject?): LocalDate? {
        if (o == null) return null
        val y = o.int("year") ?: return null
        val mo = o.int("month") ?: return null
        val d = o.int("day") ?: 1
        return runCatching { LocalDate.of(y, mo, d) }.getOrNull()
    }

    private fun relationLabel(r: String): String = when (r) {
        "SEQUEL" -> "Sequel"
        "PREQUEL" -> "Prequel"
        "SOURCE" -> "Source"
        "ADAPTATION" -> "Adaptation"
        "SIDE_STORY" -> "Side story"
        "SPIN_OFF" -> "Spin-off"
        "ALTERNATIVE" -> "Alternative"
        "PARENT" -> "Parent story"
        "SUMMARY" -> "Summary"
        "COMPILATION" -> "Compilation"
        else -> r.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private fun relationOrder(label: String): Int = when {
        label.startsWith("Sequel") -> 0
        label.startsWith("Prequel") -> 1
        label.startsWith("Source") -> 2
        label.startsWith("Parent") -> 3
        label.startsWith("Side") -> 4
        else -> 5
    }

    private fun cleanDescription(s: String?): String {
        if (s.isNullOrBlank()) return "No synopsis yet."
        return s.replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotBlank() }
    private fun JSONObject.int(k: String): Int? = if (isNull(k)) null else optInt(k)
    private fun JSONObject.long(k: String): Long? = if (isNull(k)) null else optLong(k)
    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
