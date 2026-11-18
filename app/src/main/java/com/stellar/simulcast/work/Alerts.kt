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
import com.stellar.simulcast.data.Watchlist
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** Runs about every 30 minutes and posts a notification for each new episode of a starred show. */
class EpisodeCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        Watchlist.init(applicationContext)
        val now = System.currentTimeMillis() / 1000
        val ids = Watchlist.ids.value
        if (!Watchlist.alerts.value || ids.isEmpty()) {
            Watchlist.lastCheck = now
            return Result.success()
        }
        // Look back to the last check, but never more than a day (avoids a flood after the phone was off).
        val from = maxOf(Watchlist.lastCheck.takeIf { it > 0 } ?: (now - 3600), now - 86400)
        return try {
            AniList.airedFor(ids.toList(), from, now + 1).forEach { Notifier.episode(applicationContext, it) }
            Watchlist.lastCheck = now
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<EpisodeCheckWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("episode-check", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

object Notifier {
    private const val CHANNEL = "episodes"

    fun createChannel(ctx: Context) {
        val channel = NotificationChannel(CHANNEL, "New episodes", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When a show on your watchlist releases an episode"
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun episode(ctx: Context, ep: AiringEp) {
        if (!canNotify(ctx)) return
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ANIME, ep.anime.id)
        }
        val pending = PendingIntent.getActivity(
            ctx, ep.id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_simulcast)
            .setColor(0xFF00E5FF.toInt())
            .setContentTitle(ep.anime.title)
            .setContentText("Episode ${ep.episode} is out now")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ep.id, notification)
        } catch (_: SecurityException) {
        }
    }
}
