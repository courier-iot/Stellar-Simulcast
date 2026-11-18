package com.stellar.simulcast

import android.app.Application
import com.stellar.simulcast.data.CrashLog
import com.stellar.simulcast.data.Downloads
import com.stellar.simulcast.data.Lists
import com.stellar.simulcast.data.MangaLibrary
import com.stellar.simulcast.data.Settings
import com.stellar.simulcast.data.Watchlist
import com.stellar.simulcast.work.ChapterCheckWorker
import com.stellar.simulcast.work.DigestWorker
import com.stellar.simulcast.work.EpisodeCheckWorker
import com.stellar.simulcast.work.Notifier

class SimulcastApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        Watchlist.init(this)
        Settings.init(this)
        MangaLibrary.init(this)
        Lists.init(this)
        Downloads.refresh(this)
        Notifier.createChannels(this)
        EpisodeCheckWorker.schedule(this, Settings.alertPrefs.intervalMin)
        DigestWorker.schedule(this, Settings.alertPrefs)
        ChapterCheckWorker.schedule(this)
    }
}
