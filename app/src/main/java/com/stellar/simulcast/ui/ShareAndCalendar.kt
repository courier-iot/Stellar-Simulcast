package com.stellar.simulcast.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.CalendarContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.stellar.simulcast.data.Anime
import com.stellar.simulcast.data.Settings
import com.stellar.simulcast.data.TimeText

fun showUrl(a: Anime): String = a.siteUrl ?: "https://anilist.co/anime/${a.id}"

fun shareShow(ctx: Context, a: Anime) {
    val name = a.name(Settings.titleLang)
    val at = a.nextAiringAt
    val next = if (at != null && a.nextEpisode != null) {
        "\nNext: EP ${a.nextEpisode}, ${TimeText.dateTime(TimeText.streamAt(at))}"
    } else ""
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, name)
        putExtra(Intent.EXTRA_TEXT, "$name$next\n${showUrl(a)}")
    }
    ctx.startActivity(Intent.createChooser(send, "Share $name"))
}

/**
 * Opens the phone's calendar with the next episode filled in.
 * If the total episode count is known, it repeats weekly for the rest of the season.
 */
fun addToCalendar(ctx: Context, a: Anime, durationMin: Int?): Boolean {
    val at = a.nextAiringAt ?: return false
    val ep = a.nextEpisode ?: return false
    val name = a.name(Settings.titleLang)
    val start = TimeText.streamAt(at) * 1000
    val remaining = a.episodes?.let { it - ep + 1 }?.takeIf { it > 1 }
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + (durationMin ?: 24) * 60_000L)
        .putExtra(CalendarContract.Events.TITLE, if (remaining != null) "$name · new episode" else "$name · EP $ep")
        .putExtra(CalendarContract.Events.DESCRIPTION, showUrl(a))
    if (remaining != null) intent.putExtra(CalendarContract.Events.RRULE, "FREQ=WEEKLY;COUNT=$remaining")
    return try {
        ctx.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

private fun qrBitmap(text: String, size: Int = 640): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h) { i ->
        if (matrix.get(i % w, i / w)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

@Composable
fun QrDialog(a: Anime, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val url = showUrl(a)
    val bitmap = remember(url) { qrBitmap(url) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.Surface,
        title = { Text(a.name(Settings.titleLang), color = Space.Cyan, fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap,
                    contentDescription = "QR code for this show",
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(8.dp),
                )
                Text(
                    "Scan with any phone camera to open this show on AniList.",
                    color = Space.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Space.Cyan) } },
        dismissButton = {
            TextButton(onClick = { shareShow(ctx, a); onDismiss() }) { Text("Share link", color = Space.Magenta) }
        },
    )
}
