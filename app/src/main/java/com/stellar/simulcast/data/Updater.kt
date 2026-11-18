package com.stellar.simulcast.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(val version: String, val apkUrl: String?, val pageUrl: String)

/** Checks this app's GitHub releases for a newer version. Nothing is installed without you tapping it. */
object Updater {
    private const val LATEST = "https://api.github.com/repos/courier-iot/Stellar-Simulcast/releases/latest"
    private const val KEY_LAST = "last_update_check"

    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    fun currentVersion(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0.0.0"

    /** Returns update info if GitHub has a newer release, null if you're up to date. */
    suspend fun check(ctx: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(LATEST)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "StellarSimulcast")
            .build()
        val json = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("GitHub error (${resp.code})")
            JSONObject(resp.body?.string().orEmpty())
        }
        Settings.putLong(KEY_LAST, System.currentTimeMillis())
        val tag = json.optString("tag_name").removePrefix("v")
        if (!isNewer(tag, currentVersion(ctx))) return@withContext null
        val assets = json.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0))
            .mapNotNull { assets?.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") }
            ?.optString("browser_download_url")
        UpdateInfo(tag, apk, json.optString("html_url"))
    }

    /** True if it's been a day since the last automatic check. */
    fun due(): Boolean = System.currentTimeMillis() - Settings.getLong(KEY_LAST) > 24 * 3600_000L

    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split(".").map { it.toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
