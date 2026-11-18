package com.stellar.simulcast

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.stellar.simulcast.ui.SimulcastRoot
import com.stellar.simulcast.ui.SimulcastTheme

/** Lets the reader take the volume keys while it's open (when that setting is on). */
object VolumeKeys {
    @Volatile
    var handler: ((up: Boolean) -> Boolean)? = null
}

class MainActivity : ComponentActivity() {

    /** Set by notifications, widgets and app shortcuts, so the app opens in the right place. */
    private val openAnime = mutableStateOf<Int?>(null)
    private val openManga = mutableStateOf<String?>(null)
    private val openRead = mutableStateOf<String?>(null)
    private val shortcut = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            SimulcastTheme {
                SimulcastRoot(openAnime, openManga, openRead, shortcut)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        intent.getIntExtra(EXTRA_ANIME, -1).takeIf { it > 0 }?.let { openAnime.value = it }
        intent.getStringExtra(EXTRA_MANGA)?.takeIf { it.isNotBlank() }?.let { openManga.value = it }
        intent.getStringExtra(EXTRA_READ)?.takeIf { it.isNotBlank() }?.let { openRead.value = it }
        intent.getStringExtra(EXTRA_SHORTCUT)?.takeIf { it.isNotBlank() }?.let { shortcut.value = it }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val h = VolumeKeys.handler
        if (h != null && (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            // Swallow both down and up so the volume doesn't change, but only act once.
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (h(event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)) return true
            } else {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val EXTRA_ANIME = "anime_id"
        const val EXTRA_MANGA = "manga_id"
        const val EXTRA_READ = "read_target"
        const val EXTRA_SHORTCUT = "shortcut"
    }
}
