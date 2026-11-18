package com.stellar.simulcast.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class DownloadInfo(
    val chapterId: String,
    val mangaId: String,
    val mangaTitle: String,
    val chapterLabel: String,
    val group: String?,
    val pages: Int,
    val bytes: Long,
)

/**
 * Optional offline chapters (off by default). Saved in the app's private storage,
 * removed when you delete them or uninstall the app.
 */
object Downloads {
    private val _ready = MutableStateFlow<Set<String>>(emptySet())
    /** Chapter IDs that are fully downloaded. */
    val ready: StateFlow<Set<String>> = _ready

    private val _active = MutableStateFlow<Set<String>>(emptySet())
    /** Chapter IDs downloading right now. */
    val active: StateFlow<Set<String>> = _active

    private fun root(ctx: Context) = File(ctx.filesDir, "chapters").apply { mkdirs() }
    private fun dir(ctx: Context, chapterId: String) = File(root(ctx), chapterId)

    fun refresh(ctx: Context) {
        _ready.value = root(ctx).listFiles().orEmpty()
            .filter { File(it, "meta.json").exists() }
            .map { it.name }
            .toSet()
    }

    /** Local page files (as file:// URIs), or null if the chapter isn't downloaded. */
    fun localPages(ctx: Context, chapterId: String): List<String>? {
        val d = dir(ctx, chapterId)
        if (!File(d, "meta.json").exists()) return null
        return d.listFiles().orEmpty()
            .filter { it.name != "meta.json" }
            .sortedBy { it.nameWithoutExtension.toIntOrNull() ?: 0 }
            .map { "file://" + it.absolutePath }
    }

    fun list(ctx: Context): List<DownloadInfo> = root(ctx).listFiles().orEmpty().mapNotNull { d ->
        val meta = File(d, "meta.json").takeIf { it.exists() } ?: return@mapNotNull null
        runCatching {
            val j = JSONObject(meta.readText())
            DownloadInfo(
                chapterId = d.name,
                mangaId = j.optString("mangaId"),
                mangaTitle = j.optString("mangaTitle"),
                chapterLabel = j.optString("chapterLabel"),
                group = j.optString("group").ifBlank { null },
                pages = j.optInt("pages"),
                bytes = d.listFiles().orEmpty().sumOf { it.length() },
            )
        }.getOrNull()
    }.sortedWith(compareBy<DownloadInfo> { it.mangaTitle }.thenBy { it.chapterLabel })

    /** Downloaded chapters of one manga, rebuilt from saved info, for reading with no connection. */
    fun offline(ctx: Context, mangaId: String): Pair<String, List<Chapter>>? {
        var title: String? = null
        val chapters = root(ctx).listFiles().orEmpty().mapNotNull { d ->
            val meta = File(d, "meta.json").takeIf { it.exists() } ?: return@mapNotNull null
            val j = runCatching { JSONObject(meta.readText()) }.getOrNull() ?: return@mapNotNull null
            if (j.optString("mangaId") != mangaId) return@mapNotNull null
            title = j.optString("mangaTitle")
            Chapter(
                id = d.name,
                number = j.optString("number").ifBlank { null },
                title = j.optString("title").ifBlank { null },
                volume = j.optString("volume").ifBlank { null },
                language = j.optString("language"),
                pages = j.optInt("pages"),
                group = j.optString("group").ifBlank { null },
                groupId = j.optString("groupId").ifBlank { null },
                publishAt = j.optLong("publishAt"),
                externalUrl = null,
                mangaId = mangaId,
            )
        }.sortedBy { it.num }
        return if (chapters.isEmpty()) null else (title ?: "Manga") to chapters
    }

    fun delete(ctx: Context, chapterId: String) {
        dir(ctx, chapterId).deleteRecursively()
        refresh(ctx)
    }

    fun deleteAll(ctx: Context) {
        root(ctx).deleteRecursively()
        refresh(ctx)
    }

    /** Downloads every page of a chapter. Runs inside a background worker. */
    suspend fun download(ctx: Context, mangaId: String, mangaTitle: String, chapter: Chapter) = withContext(Dispatchers.IO) {
        _active.value = _active.value + chapter.id
        val d = dir(ctx, chapter.id)
        try {
            d.deleteRecursively()
            d.mkdirs()
            val urls = MangaDex.pages(chapter.id).urls(Settings.manga.dataSaver)
            val http = MangaDex.imageClient()
            urls.forEachIndexed { i, url ->
                val ext = url.substringAfterLast('.', "jpg").take(4)
                http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("Page ${i + 1} failed (${resp.code})")
                    File(d, "${i + 1}.$ext").outputStream().use { out -> resp.body?.byteStream()?.copyTo(out) }
                }
            }
            File(d, "meta.json").writeText(
                JSONObject()
                    .put("mangaId", mangaId)
                    .put("mangaTitle", mangaTitle)
                    .put("chapterLabel", chapterLine(chapter))
                    .put("group", chapter.group ?: "")
                    .put("groupId", chapter.groupId ?: "")
                    .put("number", chapter.number ?: "")
                    .put("volume", chapter.volume ?: "")
                    .put("title", chapter.title ?: "")
                    .put("language", chapter.language)
                    .put("publishAt", chapter.publishAt)
                    .put("pages", urls.size)
                    .toString()
            )
        } catch (e: Exception) {
            d.deleteRecursively()
            throw e
        } finally {
            _active.value = _active.value - chapter.id
            refresh(ctx)
        }
    }

    private fun chapterLine(c: Chapter): String = listOfNotNull(c.volume?.let { "Vol. $it" }, c.label, c.title).joinToString(" · ")
}
