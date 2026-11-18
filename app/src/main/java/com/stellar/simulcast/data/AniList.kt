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
        id isAdult format episodes status averageScore genres siteUrl
        title { romaji english }
        coverImage { large }
        nextAiringEpisode { episode airingAt }
        externalLinks { site url type language }
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
            studios(isMain: true) { nodes { name } }
            trailer { id site }
            airingSchedule(perPage: 50) { nodes { episode airingAt } }
            streamingEpisodes { title url }
            characters(sort: [ROLE, RELEVANCE], perPage: 12) {
              edges {
                role
                node { name { full } image { medium } }
                voiceActors(language: JAPANESE) { name { full } }
              }
            }
            relations {
              edges {
                relationType(version: 2)
                node { type $MEDIA }
              }
            }
            trends(sort: DATE_DESC, perPage: 40) { nodes { date averageScore } }
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

    /** Anime made from an AniList manga (used for "Watch the anime" on manga pages). */
    suspend fun animeForManga(aniListMangaId: Int): List<Anime> {
        val m = post(MANGA_ADAPTATIONS, JSONObject().put("id", aniListMangaId)).getJSONObject("Media")
        return m.optJSONObject("relations")?.optJSONArray("edges").objects()
            .mapNotNull { e -> e.optJSONObject("node")?.takeIf { it.optString("type") == "ANIME" } }
            .map { parseAnime(it) }
            .filter { !it.isAdult }
            .distinctBy { it.id }
    }

    private suspend fun post(query: String, vars: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().put("query", query).put("variables", vars).toString().toRequestBody(jsonType)
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Accept", "application/json")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            if (resp.code == 429) throw IOException("AniList is rate limiting us. Wait a minute and try again.")
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
            data
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
        val studios = m.optJSONObject("studios")?.optJSONArray("nodes").objects().mapNotNull { it.str("name") }
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
            episodeList = episodeList(m),
            trailerUrl = trailer,
            cast = cast,
            related = related,
            scoreChange = scoreChange(m.optJSONObject("trends")?.optJSONArray("nodes").objects()),
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
        )
    }

    private val episodeTitle = Regex("""^Episode\s+(\d+)\s*[-:–]?\s*(.*)$""", RegexOption.IGNORE_CASE)

    private fun episodeList(m: JSONObject): List<EpisodeInfo> {
        val dates = m.optJSONObject("airingSchedule")?.optJSONArray("nodes").objects()
            .associate { it.optInt("episode") to it.optLong("airingAt") }
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
