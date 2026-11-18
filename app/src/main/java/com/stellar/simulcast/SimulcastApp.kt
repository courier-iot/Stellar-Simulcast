package com.stellar.simulcast

import android.app.Application
import com.stellar.simulcast.data.Watchlist
import com.stellar.simulcast.work.EpisodeCheckWorker
import com.stellar.simulcast.work.Notifier

class SimulcastApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Watchlist.init(this)
        Notifier.createChannel(this)
        EpisodeCheckWorker.schedule(this)
    }
}
