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
        query (%season: MediaSeason, %year: Int, %page: Int) {
          Page(page: %page, perPage: 30) {
            pageInfo { hasNextPage }
            media(season: %season, seasonYear: %year, type: ANIME, isAdult: false, sort: POPULARITY_DESC) { $MEDIA }
          }
        }
        """
    )

    private val SEARCH = gql(
        """
        query (%q: String, %page: Int) {
          Page(page: %page, perPage: 30) {
            pageInfo { hasNextPage }
            media(search: %q, type: ANIME, isAdult: false, sort: SEARCH_MATCH) { $MEDIA }
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

    private val DETAIL = gql(
        """
        query (%id: Int) {
          Media(id: %id) {
            $MEDIA
            description(asHtml: false)
            bannerImage season seasonYear duration source
            studios(isMain: true) { nodes { name } }
            externalLinks { site url type }
            airingSchedule(notYetAired: true, perPage: 4) { nodes { episode airingAt } }
          }
        }
        """
    )

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

    suspend fun season(key: SeasonKey, page: Int): Paged<Anime> =
        mediaPage(SEASON, JSONObject().put("season", key.season.name).put("year", key.year).put("page", page))

    suspend fun search(q: String, page: Int): Paged<Anime> =
        mediaPage(SEARCH, JSONObject().put("q", q).put("page", page))

    suspend fun byIds(ids: List<Int>): List<Anime> {
        val out = mutableListOf<Anime>()
        for (chunk in ids.chunked(50)) {
            out += mediaPage(BY_IDS, JSONObject().put("ids", JSONArray(chunk)).put("page", 1), keepAdult = true).items
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
                StreamLink(l.str("site") ?: "Stream", url)
            }
            .distinctBy { it.site }
        val upcoming = m.optJSONObject("airingSchedule")?.optJSONArray("nodes").objects()
            .map { it.optInt("episode") to it.optLong("airingAt") }
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
            upcoming = upcoming,
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

    private fun parseAnime(o: JSONObject): Anime {
        val t = o.optJSONObject("title")
        val english = t?.str("english")
        val romaji = t?.str("romaji")
        val next = o.optJSONObject("nextAiringEpisode")
        val genres = o.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        return Anime(
            id = o.getInt("id"),
            title = english ?: romaji ?: "Untitled",
            romaji = romaji?.takeIf { it != english },
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
