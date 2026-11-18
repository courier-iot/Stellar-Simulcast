package com.stellar.simulcast.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Moves everything the app keeps (watchlist, hidden shows, progress, followed manga,
 * reading spots and settings) to another phone as one compact code, with no account.
 */
object Transfer {
    private const val PREFIX = "SSIM1:"

    /** QR codes start getting hard to scan past roughly this many characters. */
    const val QR_LIMIT = 2200

    fun export(ctx: Context): String {
        val p = ctx.getSharedPreferences("simulcast", Context.MODE_PRIVATE)
        val root = JSONObject()
        for ((k, v) in p.all) {
            val entry = when (v) {
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                is String -> JSONObject().put("t", "s").put("v", v)
                is Set<*> -> JSONObject().put("t", "S").put("v", JSONArray(v.map { it.toString() }))
                else -> null
            } ?: continue
            root.put(k, entry)
        }
        val bytes = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(root.toString().toByteArray()) }
        }.toByteArray()
        return PREFIX + Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    /** Replaces this phone's data with the code's. Returns false if the code isn't valid. */
    fun restore(ctx: Context, code: String): Boolean {
        val trimmed = code.trim()
        if (!trimmed.startsWith(PREFIX)) return false
        val json = runCatching {
            val raw = Base64.decode(trimmed.removePrefix(PREFIX), Base64.NO_WRAP or Base64.URL_SAFE)
            JSONObject(GZIPInputStream(ByteArrayInputStream(raw)).bufferedReader().use { it.readText() })
        }.getOrNull() ?: return false

        val edit = ctx.getSharedPreferences("simulcast", Context.MODE_PRIVATE).edit().clear()
        for (k in json.keys()) {
            val e = json.optJSONObject(k) ?: continue
            when (e.optString("t")) {
                "b" -> edit.putBoolean(k, e.optBoolean("v"))
                "i" -> edit.putInt(k, e.optInt("v"))
                "l" -> edit.putLong(k, e.optLong("v"))
                "f" -> edit.putFloat(k, e.optDouble("v").toFloat())
                "s" -> edit.putString(k, e.optString("v"))
                "S" -> {
                    val arr = e.optJSONArray("v") ?: JSONArray()
                    edit.putStringSet(k, (0 until arr.length()).map { arr.optString(it) }.toSet())
                }
            }
        }
        edit.commit()
        Watchlist.init(ctx, force = true)
        Settings.init(ctx, force = true)
        MangaLibrary.init(ctx, force = true)
        return true
    }
}
