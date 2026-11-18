package com.stellar.simulcast.work

import android.Manifest
import android.app.Notification
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
import com.stellar.simulcast.data.AlertPrefs
import com.stellar.simulcast.data.AniList
import com.stellar.simulcast.data.Anime
import com.stellar.simulcast.data.Chapter
import com.stellar.simulcast.data.Manga
import com.stellar.simulcast.data.MangaDex
import com.stellar.simulcast.data.MangaLibrary
import com.stellar.simulcast.data.SequelNews
import com.stellar.simulcast.data.Settings
import com.stellar.simulcast.data.TimeText
import com.stellar.simulcast.data.Watchlist
import com.stellar.simulcast.widget.MangaWidget
import com.stellar.simulcast.widget.NextUpWidget
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

private val networkConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

private fun initAll(ctx: Context) {
    Watchlist.init(ctx)
    Settings.init(ctx)
    MangaLibrary.init(ctx)
    com.stellar.simulcast.data.Lists.init(ctx)
}

/** Runs on the chosen interval: new-episode alerts, sequel alerts (twice a day), and a widget refresh. */
class EpisodeCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        initAll(applicationContext)
        val now = System.currentTimeMillis() / 1000
        val ids = Watchlist.ids.value
        runCatching { NextUpWidget.refresh(applicationContext) }

        if (!Watchlist.alerts.value || ids.isEmpty()) {
            Watchlist.lastCheck = now
            return Result.success()
        }
        val prefs = Settings.alertPrefs
        // During quiet hours, hold off. The next check after quiet hours ends picks up anything missed.
        if (prefs.isQuiet(LocalTime.now().hour)) return Result.success()

        return try {
            // Episodes count as "out" once Japan air time + that show's stream delay has passed.
            val maxDelay = TimeText.maxDelaySec()
            val from = maxOf(Watchlist.lastCheck.takeIf { it > 0 } ?: (now - 3600), now - 86400)
            val eps = AniList.airedFor(ids.toList(), from - maxDelay, now + 1)
                .filter { TimeText.streamAt(it.airingAt, it.anime).let { at -> at > from && at <= now } }
            if (prefs.grouped && eps.size > 1) Notifier.grouped(applicationContext, eps)
            else eps.forEach { Notifier.episode(applicationContext, it) }
            Watchlist.lastCheck = now

            if (prefs.sequels && now - Watchlist.lastSequelCheck > 12 * 3600) {
                checkSequels(applicationContext, ids.toList())
                Watchlist.lastSequelCheck = now
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun checkSequels(ctx: Context, ids: List<Int>) {
        val firstEver = Watchlist.lastSequelCheck == 0L // first run just learns what's already announced
        val seen = Watchlist.seenSequels.toMutableSet()
        for (news in AniList.sequels(ids, Settings.titleLang)) {
            val key = news.sequel.id.toString()
            val dateKey = "$key:d"
            val hasDate = news.startDate != null
            when {
                key !in seen -> {
                    if (!firstEver) Notifier.sequel(ctx, news, announced = true)
                    seen += key
                    if (hasDate) seen += dateKey
                }
                hasDate && dateKey !in seen -> {
                    if (!firstEver) Notifier.sequel(ctx, news, announced = false)
                    seen += dateKey
                }
            }
        }
        Watchlist.seenSequels = seen
    }

    companion object {
        private const val NAME = "episode-check"

        /** [replace] = true applies a new interval right away. */
        fun schedule(ctx: Context, minutes: Int, replace: Boolean = false) {
            val request = PeriodicWorkRequestBuilder<EpisodeCheckWorker>(minutes.toLong().coerceAtLeast(15), TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                NAME,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

/** Every 2 hours: new chapters of manga you follow, in your languages. */
class ChapterCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        initAll(applicationContext)
        val now = System.currentTimeMillis() / 1000
        val follows = MangaLibrary.follows.value
        if (!Settings.manga.chapterAlerts || !Watchlist.alerts.value || follows.isEmpty()) {
            MangaLibrary.lastCheck = now
            return Result.success()
        }
        if (Settings.alertPrefs.isQuiet(LocalTime.now().hour)) return Result.success()
        val since = maxOf(MangaLibrary.lastCheck.takeIf { it > 0 } ?: (now - 3 * 3600), now - 3 * 86400)
        return try {
            val notified = MangaLibrary.notified.toMutableSet()
            val fresh = mutableListOf<Pair<String, Chapter>>()
            for (id in follows.take(60)) {
                val chapters = MangaDex.chaptersSince(id, since)
                    .filter { it.id !in notified && it.publishAt <= now }
                    .distinctBy { it.number }
                chapters.forEach { fresh += id to it; notified += it.id }
                kotlinx.coroutines.delay(250) // stay well under MangaDex's rate limit
            }
            if (fresh.isNotEmpty()) {
                MangaLibrary.recentNew = fresh.sortedByDescending { it.second.publishAt }
                    .map { (mid, c) -> "$mid|${c.id}|${c.label}|${c.publishAt}" } + MangaLibrary.recentNew
                runCatching { MangaWidget.refresh(applicationContext) }
                val titles = MangaDex.byIds(fresh.map { it.first }.toSet()).associateBy { it.id }
                val grouped = fresh.groupBy { it.first }
                for ((mangaId, list) in grouped) {
                    val manga = titles[mangaId] ?: continue
                    Notifier.chapters(applicationContext, manga, list.map { it.second })
                }
            }
            MangaLibrary.notified = notified
            MangaLibrary.lastCheck = now
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "chapter-check"

        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<ChapterCheckWorker>(2, TimeUnit.HOURS)
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** Once a day at the chosen hour: what's coming out today from your watchlist. */
class DigestWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        initAll(applicationContext)
        val ids = Watchlist.ids.value
        if (!Settings.alertPrefs.digest || !Watchlist.alerts.value || ids.isEmpty()) return Result.success()
        return try {
            val now = System.currentTimeMillis() / 1000
            val today = AniList.byIds(ids.toList())
                .filter { a -> a.nextAiringAt?.let { TimeText.streamAt(it, a) in now..(now + 86400) } == true }
                .sortedBy { it.nextAiringAt }
            if (today.isNotEmpty()) Notifier.digest(applicationContext, today)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "daily-digest"

        fun schedule(ctx: Context, prefs: AlertPrefs, replace: Boolean = false) {
            val wm = WorkManager.getInstance(ctx)
            if (!prefs.digest) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(prefs.digestHour, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<DigestWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .build()
            wm.enqueueUniquePeriodicWork(
                NAME,
                if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

object Notifier {
    private const val CHANNEL_EPISODES = "episodes"
    private const val CHANNEL_NEWS = "news"
    private const val GROUP_ID = 1
    private const val DIGEST_ID = 2

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_EPISODES, "New episodes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a show on your watchlist releases an episode"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_NEWS, "Sequels, chapters and digest", NotificationManager.IMPORTANCE_LOW).apply {
                description = "New seasons of your shows, and the morning rundown"
            }
        )
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun title(a: Anime) = a.name(Settings.titleLang)

    private fun openIntent(ctx: Context, requestCode: Int, animeId: Int?): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (animeId != null) putExtra(MainActivity.EXTRA_ANIME, animeId)
        }
        return PendingIntent.getActivity(
            ctx, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun base(ctx: Context, channel: String) = NotificationCompat.Builder(ctx, channel)
        .setSmallIcon(R.drawable.ic_stat_simulcast)
        .setColor(0xFF00E5FF.toInt())
        .setAutoCancel(true)

    fun episode(ctx: Context, ep: AiringEp) {
        val n = base(ctx, CHANNEL_EPISODES)
            .setContentTitle(title(ep.anime))
            .setContentText("Episode ${ep.episode} is out now")
            .setContentIntent(openIntent(ctx, ep.id, ep.anime.id))
            .addAction(0, "Mark watched", NotifyActionReceiver.markWatched(ctx, ep.id, ep.anime.id, ep.episode))
            .build()
        post(ctx, ep.id, n)
    }

    /** One notification listing every new episode. */
    fun grouped(ctx: Context, eps: List<AiringEp>) {
        val style = NotificationCompat.InboxStyle()
        eps.take(7).forEach { style.addLine("${title(it.anime)} · EP ${it.episode}") }
        if (eps.size > 7) style.setSummaryText("+${eps.size - 7} more")
        val n = base(ctx, CHANNEL_EPISODES)
            .setContentTitle("${eps.size} new episodes are out")
            .setContentText(eps.joinToString(", ") { title(it.anime) })
            .setStyle(style)
            .setContentIntent(openIntent(ctx, GROUP_ID, null))
            .build()
        post(ctx, GROUP_ID, n)
    }

    fun sequel(ctx: Context, news: SequelNews, announced: Boolean) {
        val name = title(news.sequel)
        val whenText = news.startDate?.let { "Starts ${TimeText.longDate(it)}" } ?: "No air date yet"
        val n = base(ctx, CHANNEL_NEWS)
            .setContentTitle(if (announced) "New season: $name" else "Air date set: $name")
            .setContentText(if (announced) "Follow-up to ${news.parentTitle}. $whenText" else whenText)
            .setContentIntent(openIntent(ctx, 100_000 + news.sequel.id, news.sequel.id))
            .build()
        post(ctx, 100_000 + news.sequel.id, n)
    }

    fun digest(ctx: Context, shows: List<Anime>) {
        val style = NotificationCompat.InboxStyle()
        shows.take(7).forEach { a ->
            val at = a.nextAiringAt ?: return@forEach
            style.addLine("${TimeText.clock(TimeText.streamAt(at, a))} · ${title(a)} EP ${a.nextEpisode ?: "?"}")
        }
        if (shows.size > 7) style.setSummaryText("+${shows.size - 7} more")
        val n = base(ctx, CHANNEL_NEWS)
            .setContentTitle(if (shows.size == 1) "1 episode today" else "${shows.size} episodes today")
            .setContentText(shows.joinToString(", ") { title(it) })
            .setStyle(style)
            .setContentIntent(openIntent(ctx, DIGEST_ID, null))
            .build()
        post(ctx, DIGEST_ID, n)
    }

    fun chapters(ctx: Context, manga: Manga, chapters: List<Chapter>) {
        val sorted = chapters.sortedBy { it.num }
        val text = if (sorted.size == 1) {
            val c = sorted[0]
            listOfNotNull(c.label, c.title, c.group?.let { "by $it" }).joinToString(" · ")
        } else "${sorted.size} new chapters: ${sorted.first().label} to ${sorted.last().label}"
        val id = 200_000 + (manga.id.hashCode() and 0xFFFFF)
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_MANGA, manga.id)
        }
        val pending = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = base(ctx, CHANNEL_NEWS)
            .setContentTitle("New chapter: ${manga.title}")
            .setContentText(text)
            .setContentIntent(pending)
        // "Read now" jumps straight into the reader at the first new chapter.
        sorted.firstOrNull { it.externalUrl == null }?.let { first ->
            val readIntent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_READ, "${manga.id}|${first.id}|0")
            }
            val readPending = PendingIntent.getActivity(ctx, id + 1, readIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(0, "Read now", readPending)
        }
        post(ctx, id, builder.build())
    }

    private fun post(ctx: Context, id: Int, n: Notification) {
        if (!canNotify(ctx)) return
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}

/** Handles buttons on notifications (e.g. "Mark watched"). */
class NotifyActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != ACTION_WATCHED) return
        initAll(ctx)
        val animeId = intent.getIntExtra(EXTRA_ANIME, -1)
        val episode = intent.getIntExtra(EXTRA_EP, -1)
        if (animeId > 0 && episode > 0) {
            val current = Watchlist.progress.value[animeId] ?: 0
            if (episode > current) Watchlist.setProgress(animeId, episode)
        }
        NotificationManagerCompat.from(ctx).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
    }

    companion object {
        private const val ACTION_WATCHED = "com.stellar.simulcast.MARK_WATCHED"
        private const val EXTRA_ANIME = "anime"
        private const val EXTRA_EP = "episode"
        private const val EXTRA_NOTIFICATION = "notification"

        fun markWatched(ctx: Context, notificationId: Int, animeId: Int, episode: Int): PendingIntent {
            val i = Intent(ctx, NotifyActionReceiver::class.java).apply {
                action = ACTION_WATCHED
                putExtra(EXTRA_ANIME, animeId)
                putExtra(EXTRA_EP, episode)
                putExtra(EXTRA_NOTIFICATION, notificationId)
            }
            return PendingIntent.getBroadcast(ctx, 300_000 + (notificationId and 0xFFFFF), i,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}

/** Downloads one chapter for offline reading. */
class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        initAll(applicationContext)
        val mangaId = inputData.getString("manga") ?: return Result.failure()
        val chapterId = inputData.getString("chapter") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Manga"
        return try {
            val chapter = MangaDex.chapters(mangaId).firstOrNull { it.id == chapterId } ?: return Result.failure()
            com.stellar.simulcast.data.Downloads.download(applicationContext, mangaId, title, chapter)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        fun enqueue(ctx: Context, mangaId: String, mangaTitle: String, chapterId: String) {
            val request = androidx.work.OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(androidx.work.workDataOf("manga" to mangaId, "chapter" to chapterId, "title" to mangaTitle))
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("dl-$chapterId", androidx.work.ExistingWorkPolicy.KEEP, request)
        }
    }
}
