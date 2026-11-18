package com.stellar.simulcast.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stellar.simulcast.MainActivity
import com.stellar.simulcast.R
import com.stellar.simulcast.data.AiringEp
import com.stellar.simulcast.data.AniList
import com.stellar.simulcast.data.Settings
import com.stellar.simulcast.data.Watchlist
import kotlinx.coroutines.CancellationException
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Runs on the chosen interval and posts notifications for new episodes of starred shows. */
class EpisodeCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        Watchlist.init(applicationContext)
        Settings.init(applicationContext)
        val now = System.currentTimeMillis() / 1000
        val ids = Watchlist.ids.value
        if (!Watchlist.alerts.value || ids.isEmpty()) {
            Watchlist.lastCheck = now
            return Result.success()
        }
        val prefs = Settings.alertPrefs.value
        // During quiet hours, hold off. The next check after quiet hours ends picks up anything missed.
        if (prefs.isQuiet(LocalTime.now().hour)) return Result.success()

        // Look back to the last check, but never more than a day (avoids a flood after the phone was off).
        val from = maxOf(Watchlist.lastCheck.takeIf { it > 0 } ?: (now - 3600), now - 86400)
        return try {
            val eps = AniList.airedFor(ids.toList(), from, now + 1)
            if (prefs.grouped && eps.size > 1) Notifier.grouped(applicationContext, eps)
            else eps.forEach { Notifier.episode(applicationContext, it) }
            Watchlist.lastCheck = now
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "episode-check"

        /** [replace] = true applies a new interval right away. */
        fun schedule(ctx: Context, minutes: Int, replace: Boolean = false) {
            val request = PeriodicWorkRequestBuilder<EpisodeCheckWorker>(minutes.toLong().coerceAtLeast(15), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                NAME,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

object Notifier {
    private const val CHANNEL = "episodes"
    private const val GROUP_ID = 1

    fun createChannel(ctx: Context) {
        val channel = NotificationChannel(CHANNEL, "New episodes", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When a show on your watchlist releases an episode"
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun title(ep: AiringEp) = ep.anime.name(Settings.titleLang.value)

    private fun openIntent(ctx: Context, requestCode: Int, animeId: Int?): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (animeId != null) putExtra(MainActivity.EXTRA_ANIME, animeId)
        }
        return PendingIntent.getActivity(
            ctx, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun base(ctx: Context) = NotificationCompat.Builder(ctx, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_simulcast)
        .setColor(0xFF00E5FF.toInt())
        .setAutoCancel(true)

    fun episode(ctx: Context, ep: AiringEp) {
        val n = base(ctx)
            .setContentTitle(title(ep))
            .setContentText("Episode ${ep.episode} is out now")
            .setContentIntent(openIntent(ctx, ep.id, ep.anime.id))
            .build()
        post(ctx, ep.id, n)
    }

    /** One notification listing every new episode. */
    fun grouped(ctx: Context, eps: List<AiringEp>) {
        val style = NotificationCompat.InboxStyle()
        eps.take(7).forEach { style.addLine("${title(it)} · EP ${it.episode}") }
        if (eps.size > 7) style.setSummaryText("+${eps.size - 7} more")
        val n = base(ctx)
            .setContentTitle("${eps.size} new episodes are out")
            .setContentText(eps.joinToString(", ") { title(it) })
            .setStyle(style)
            .setContentIntent(openIntent(ctx, GROUP_ID, null))
            .build()
        post(ctx, GROUP_ID, n)
    }

    private fun post(ctx: Context, id: Int, n: android.app.Notification) {
        if (!canNotify(ctx)) return
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}
