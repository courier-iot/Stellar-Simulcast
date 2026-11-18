package com.stellar.simulcast.data

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/**
 * Saves crash details on the phone (never sent anywhere) so they can be copied into a bug report.
 * Keeps the last 5 crashes.
 */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "crashes.txt")
    private const val DIVIDER = "\n=====\n"

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun record(ctx: Context, threadName: String, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
        val entry = buildString {
            appendLine("Time: ${LocalDateTime.now()}")
            appendLine("App: Stellar Simulcast $version")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: $threadName")
            append(trace.take(12_000))
        }
        val old = read(ctx).split(DIVIDER).filter { it.isNotBlank() }
        file(ctx).writeText((listOf(entry) + old).take(5).joinToString(DIVIDER))
    }

    fun read(ctx: Context): String = file(ctx).takeIf { it.exists() }?.readText().orEmpty()

    fun count(ctx: Context): Int = read(ctx).split(DIVIDER).count { it.isNotBlank() }

    fun clear(ctx: Context) {
        file(ctx).delete()
    }
}
