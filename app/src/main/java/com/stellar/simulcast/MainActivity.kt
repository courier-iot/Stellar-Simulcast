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

    /** Set when a notification is tapped, so the app opens straight to that show. */
    private val openAnime = mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            openAnime.value = intent.getIntExtra(EXTRA_ANIME, -1).takeIf { it > 0 }
        }
        setContent {
            SimulcastTheme {
                SimulcastRoot(openAnime)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getIntExtra(EXTRA_ANIME, -1).takeIf { it > 0 }?.let { openAnime.value = it }
    }

    companion object {
        const val EXTRA_ANIME = "anime_id"
    }
}
