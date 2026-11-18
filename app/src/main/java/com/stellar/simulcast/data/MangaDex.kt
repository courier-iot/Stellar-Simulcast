package com.stellar.simulcast.data

import android.content.Context
import coil.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class Manga(
    val id: String,
    val title: String,
    val altTitle: String?,
    val cover: String?,
    val description: String,
    val status: String?,
    val year: Int?,
    val contentRating: String?,
    val tags: List<String>,
    val author: String?,
    val aniListId: Int?,
    /** Official and database links, already turned into (label, url). */
    val links: List<Pair<String, String>>,
)

data class Chapter(
    val id: String,
    val number: String?,
    val title: String?,
    val volume: String?,
    val language: String,
    val pages: Int,
    val group: String?,
    val groupId: String?,
    val publishAt: Long,
    /** Set for chapters hosted by an official publisher; these open in the browser. */
    val externalUrl: String?,
    val mangaId: String?,
) {
    val num: Double get() = number?.toDoubleOrNull() ?: -1.0
    val label: String get() = if (number != null) "Ch. $number" else "Oneshot"
}

data class LatestChapter(val chapter: Chapter, val manga: Manga)

data class PageSet(val baseUrl: String, val hash: String, val data: List<String>, val dataSaver: List<String>) {
    fun urls(saver: Boolean): List<String> =
        if (saver && dataSaver.isNotEmpty()) dataSaver.map { "$baseUrl/data-saver/$hash/$it" }
        else data.map { "$baseUrl/data/$hash/$it" }
}

enum class MangaBrowse(val label: String) { POPULAR("Popular"), RECENT("Recently added"), ONGOING("Ongoing"), COMPLETED("Completed") }

/**
 * MangaDex's public API (no account needed). Chapters stream live from MangaDex@Home; nothing is stored.
 * Rules we follow: identify the app, stay under ~5 requests a second, credit MangaDex and the
 * translation group, and report image-server results so MangaDex can spot bad servers.
 */
object MangaDex {
    private const val API = "https://api.mangadex.org"
    private const val USER_AGENT = "StellarSimulcast/2.1.0 (Android)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build()) }
        .build()

    private val reportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jsonType = "application/json".toMediaType()

    // ---------- requests ----------

    private suspend fun get(path: String, params: List<Pair<String, String>> = emptyList()): JSONObject =
        withContext(Dispatchers.IO) {
            val url = "$API$path".toHttpUrl().newBuilder().apply {
                params.forEach { (k, v) -> addQueryParameter(k, v) }
            }.build()
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (resp.code == 429) throw IOException("MangaDex is rate limiting us. Wait a minute and try again.")
                val text = resp.body?.string().orEmpty()
                val json = try {
                    JSONObject(text)
                } catch (e: Exception) {
                    throw IOException("MangaDex error (${resp.code})")
                }
                if (!resp.isSuccessful || json.optString("result") == "error") {
                    val err = json.optJSONArray("errors")?.optJSONObject(0)
                    throw IOException(err?.optString("detail")?.takeIf { it.isNotBlank() } ?: "MangaDex error (${resp.code})")
                }
                json
            }
        }

    private fun ratings(all: Boolean = false): List<Pair<String, String>> =
        (if (all) CONTENT_RATINGS.toSet() else Settings.manga.ratings).map { "contentRating[]" to it }

    private fun langs(key: String): List<Pair<String, String>> = Settings.manga.languages.map { "$key[]" to it }

    // ---------- public calls ----------

    suspend fun browse(mode: MangaBrowse, offset: Int): Paged<Manga> {
        val order = when (mode) {
            MangaBrowse.POPULAR, MangaBrowse.COMPLETED -> "order[followedCount]" to "desc"
            MangaBrowse.RECENT -> "order[createdAt]" to "desc"
            MangaBrowse.ONGOING -> "order[latestUploadedChapter]" to "desc"
        }
        val status = when (mode) {
            MangaBrowse.ONGOING -> listOf("status[]" to "ongoing")
            MangaBrowse.COMPLETED -> listOf("status[]" to "completed")
            else -> emptyList()
        }
        return mangaList(listOf(order, "hasAvailableChapters" to "true") + status + langs("availableTranslatedLanguage"), offset)
    }

    suspend fun search(q: String, offset: Int): Paged<Manga> =
        mangaList(listOf("title" to q, "order[relevance]" to "desc"), offset)

    private suspend fun mangaList(params: List<Pair<String, String>>, offset: Int, limit: Int = 30): Paged<Manga> {
        val json = get(
            "/manga",
            params + ratings() + listOf("limit" to "$limit", "offset" to "$offset", "includes[]" to "cover_art"),
        )
        val items = json.optJSONArray("data").objects().map { parseManga(it) }
        val total = json.optInt("total")
        return Paged(items, offset + limit < minOf(total, 9_900))
    }

    suspend fun byIds(ids: Collection<String>): List<Manga> {
        val out = mutableListOf<Manga>()
        for (chunk in ids.toList().chunked(100)) {
            val json = get(
                "/manga",
                chunk.map { "ids[]" to it } + ratings(all = true) +
                    listOf("limit" to "100", "includes[]" to "cover_art"),
            )
            out += json.optJSONArray("data").objects().map { parseManga(it) }
        }
        return out
    }

    suspend fun manga(id: String): Manga {
        val json = get("/manga/$id", listOf("includes[]" to "cover_art", "includes[]" to "author"))
        return parseManga(json.getJSONObject("data"))
    }

    /** Newest chapters across MangaDex in your languages and ratings. */
    suspend fun latest(offset: Int): Paged<LatestChapter> {
        val json = get(
            "/chapter",
            langs("translatedLanguage") + ratings() + listOf(
                "limit" to "40", "offset" to "$offset", "order[readableAt]" to "desc",
                "includes[]" to "scanlation_group", "includes[]" to "manga",
            ),
        )
        val chapters = json.optJSONArray("data").objects().map { parseChapter(it) }
        val mangaById = byIds(chapters.mapNotNull { it.mangaId }.toSet()).associateBy { it.id }
        val items = chapters.mapNotNull { c -> mangaById[c.mangaId]?.let { LatestChapter(c, it) } }
        return Paged(items, offset + 40 < minOf(json.optInt("total"), 9_900))
    }

    /** Every chapter of a manga in your languages, oldest first. */
    suspend fun chapters(mangaId: String): List<Chapter> {
        val out = mutableListOf<Chapter>()
        var offset = 0
        while (offset < 3000) {
            val json = get(
                "/manga/$mangaId/feed",
                langs("translatedLanguage") + ratings(all = true) + listOf(
                    "limit" to "500", "offset" to "$offset",
                    "order[volume]" to "asc", "order[chapter]" to "asc",
                    "includes[]" to "scanlation_group",
                ),
            )
            out += json.optJSONArray("data").objects().map { parseChapter(it) }
            offset += 500
            if (offset >= json.optInt("total")) break
        }
        return out.sortedWith(compareBy<Chapter> { it.num }.thenBy { it.publishAt })
    }

    /** Chapters of a manga published after [since] (epoch seconds). Used for alerts. */
    suspend fun chaptersSince(mangaId: String, since: Long): List<Chapter> {
        val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            .format(java.time.Instant.ofEpochSecond(since).atOffset(ZoneOffset.UTC))
        val json = get(
            "/manga/$mangaId/feed",
            langs("translatedLanguage") + ratings(all = true) + listOf(
                "limit" to "20", "publishAtSince" to stamp, "order[publishAt]" to "asc",
            ),
        )
        return json.optJSONArray("data").objects().map { parseChapter(it) }
    }

    /** Distinct chapter numbers available in your languages (cheap, for "new chapters" badges). */
    suspend fun chapterNumbers(mangaId: String): Set<Double> {
        val json = get("/manga/$mangaId/aggregate", langs("translatedLanguage"))
        val out = mutableSetOf<Double>()
        val volumes = json.optJSONObject("volumes") ?: return out
        for (vk in volumes.keys()) {
            val chapters = volumes.optJSONObject(vk)?.optJSONObject("chapters") ?: continue
            for (ck in chapters.keys()) {
                chapters.optJSONObject(ck)?.optString("chapter")?.toDoubleOrNull()?.let { out += it }
            }
        }
        return out
    }

    suspend fun pages(chapterId: String): PageSet {
        val json = get("/at-home/server/$chapterId")
        val ch = json.getJSONObject("chapter")
        return PageSet(
            baseUrl = json.getString("baseUrl"),
            hash = ch.getString("hash"),
            data = ch.optJSONArray("data").strings(),
            dataSaver = ch.optJSONArray("dataSaver").strings(),
        )
    }

    /** Finds the MangaDex entry for an AniList manga ID by searching its titles. */
    suspend fun findByAniList(aniListId: Int, titles: List<String>): Manga? {
        for (t in titles.distinct().take(3)) {
            val json = get(
                "/manga",
                listOf("title" to t, "limit" to "10", "includes[]" to "cover_art") + ratings(all = true),
            )
            json.optJSONArray("data").objects().map { parseManga(it) }
                .firstOrNull { it.aniListId == aniListId }
                ?.let { return it }
        }
        return null
    }

    // ---------- reader images (with MangaDex@Home reporting) ----------

    @Volatile
    private var readerLoader: ImageLoader? = null

    /** Image loader for chapter pages. Reports each page load to MangaDex as their docs ask. */
    fun readerImageLoader(ctx: Context): ImageLoader {
        readerLoader?.let { return it }
        val reporting = Interceptor { chain ->
            val req = chain.request()
            val start = System.currentTimeMillis()
            try {
                val resp = chain.proceed(req)
                report(req.url.toString(), resp.isSuccessful, resp.body?.contentLength() ?: 0, System.currentTimeMillis() - start,
                    resp.header("X-Cache")?.startsWith("HIT") == true)
                resp
            } catch (e: IOException) {
                report(req.url.toString(), false, 0, System.currentTimeMillis() - start, false)
                throw e
            }
        }
        val http = OkHttpClient.Builder()
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build()) }
            .addInterceptor(reporting)
            .build()
        return ImageLoader.Builder(ctx.applicationContext).okHttpClient(http).crossfade(true).build().also { readerLoader = it }
    }

    private fun report(url: String, success: Boolean, bytes: Long, durationMs: Long, cached: Boolean) {
        // MangaDex's own servers don't need reports, only volunteer MangaDex@Home ones.
        if (url.contains("mangadex.org")) return
        reportScope.launch {
            runCatching {
                val body = JSONObject()
                    .put("url", url).put("success", success).put("bytes", maxOf(bytes, 0))
                    .put("duration", durationMs).put("cached", cached)
                    .toString().toRequestBody(jsonType)
                client.newCall(Request.Builder().url("https://api.mangadex.network/report").post(body).build())
                    .execute().close()
            }
        }
    }

    // ---------- parsing ----------

    private fun localized(o: JSONObject?): String? {
        if (o == null || o.length() == 0) return null
        for (k in listOf("en", "ja-ro", "en-us")) o.optString(k).takeIf { it.isNotBlank() }?.let { return it }
        return o.keys().asSequence().map { o.optString(it) }.firstOrNull { it.isNotBlank() }
    }

    private fun parseManga(o: JSONObject): Manga {
        val id = o.getString("id")
        val a = o.optJSONObject("attributes") ?: JSONObject()
        val title = localized(a.optJSONObject("title")) ?: "Untitled"
        val alts = a.optJSONArray("altTitles").objects()
        val altTitle = (alts.firstNotNullOfOrNull { it.optString("ja-ro").takeIf { s -> s.isNotBlank() } }
            ?: alts.firstNotNullOfOrNull { it.optString("en").takeIf { s -> s.isNotBlank() } })
            ?.takeIf { it != title }
        var cover: String? = null
        var author: String? = null
        for (r in o.optJSONArray("relationships").objects()) {
            val attrs = r.optJSONObject("attributes")
            when (r.optString("type")) {
                "cover_art" -> attrs?.optString("fileName")?.takeIf { it.isNotBlank() }?.let {
                    cover = "https://uploads.mangadex.org/covers/$id/$it.256.jpg"
                }
                "author" -> if (author == null) author = attrs?.optString("name")?.takeIf { it.isNotBlank() }
            }
        }
        val linkObj = a.optJSONObject("links")
        val aniList = linkObj?.optString("al")?.toIntOrNull()
        val tags = a.optJSONArray("tags").objects().mapNotNull { localized(it.optJSONObject("attributes")?.optJSONObject("name")) }
        return Manga(
            id = id,
            title = title,
            altTitle = altTitle,
            cover = cover,
            description = cleanText(localized(a.optJSONObject("description"))),
            status = a.optString("status").takeIf { it.isNotBlank() && it != "null" },
            year = if (a.isNull("year")) null else a.optInt("year"),
            contentRating = a.optString("contentRating").takeIf { it.isNotBlank() },
            tags = tags,
            author = author,
            aniListId = aniList,
            links = links(linkObj),
        )
    }

    private fun links(o: JSONObject?): List<Pair<String, String>> {
        if (o == null) return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        fun v(k: String) = o.optString(k).takeIf { it.isNotBlank() }
        v("engtl")?.let { out += "Official English" to it }
        v("raw")?.let { out += "Official (Japanese)" to it }
        v("amz")?.let { out += "Amazon" to it }
        v("bw")?.let { out += "BookWalker" to "https://bookwalker.jp/$it" }
        v("ebj")?.let { out += "eBookJapan" to it }
        v("cdj")?.let { out += "CDJapan" to it }
        v("al")?.let { out += "AniList" to "https://anilist.co/manga/$it" }
        v("mal")?.let { out += "MyAnimeList" to "https://myanimelist.net/manga/$it" }
        return out.filter { it.second.startsWith("http") }
    }

    private fun parseChapter(o: JSONObject): Chapter {
        val a = o.optJSONObject("attributes") ?: JSONObject()
        var group: String? = null
        var groupId: String? = null
        var mangaId: String? = null
        for (r in o.optJSONArray("relationships").objects()) {
            when (r.optString("type")) {
                "scanlation_group" -> if (group == null) {
                    groupId = r.optString("id")
                    group = r.optJSONObject("attributes")?.optString("name")?.takeIf { it.isNotBlank() }
                }
                "manga" -> mangaId = r.optString("id")
            }
        }
        val published = (a.optString("readableAt").takeIf { it.isNotBlank() } ?: a.optString("publishAt"))
            .let { runCatching { OffsetDateTime.parse(it).toEpochSecond() }.getOrDefault(0L) }
        return Chapter(
            id = o.getString("id"),
            number = a.optString("chapter").takeIf { it.isNotBlank() && it != "null" },
            title = a.optString("title").takeIf { it.isNotBlank() && it != "null" },
            volume = a.optString("volume").takeIf { it.isNotBlank() && it != "null" },
            language = a.optString("translatedLanguage"),
            pages = a.optInt("pages"),
            group = group,
            groupId = groupId,
            publishAt = published,
            externalUrl = a.optString("externalUrl").takeIf { it.isNotBlank() && it != "null" },
            mangaId = mangaId,
        )
    }

    private fun cleanText(s: String?): String {
        if (s.isNullOrBlank()) return "No description yet."
        return s.replace(Regex("""\[([^\]]+)]\((https?://[^)]+)\)"""), "$1") // markdown links -> text
            .replace(Regex("""(?m)^---+\s*$"""), "")
            .replace("**", "").replace("__", "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }
}
