package com.stellar.simulcast

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.stellar.simulcast.ui.SimulcastRoot
import com.stellar.simulcast.ui.SimulcastTheme

class MainActivity : ComponentActivity() {

    /** Set when a notification is tapped, so the app opens straight to that show or manga. */
    private val openAnime = mutableStateOf<Int?>(null)
    private val openManga = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            SimulcastTheme {
                SimulcastRoot(openAnime, openManga)
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
    }

    companion object {
        const val EXTRA_ANIME = "anime_id"
        const val EXTRA_MANGA = "manga_id"
    }
}
