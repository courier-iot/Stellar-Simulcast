package com.stellar.simulcast

import android.app.Application
import com.stellar.simulcast.data.Settings
import com.stellar.simulcast.data.Watchlist
import com.stellar.simulcast.work.DigestWorker
import com.stellar.simulcast.work.EpisodeCheckWorker
import com.stellar.simulcast.work.Notifier

class SimulcastApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Watchlist.init(this)
        Settings.init(this)
        Notifier.createChannels(this)
        EpisodeCheckWorker.schedule(this, Settings.alertPrefs.intervalMin)
        DigestWorker.schedule(this, Settings.alertPrefs)
    }
}
