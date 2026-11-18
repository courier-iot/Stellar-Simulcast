package com.stellar.simulcast.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.stellar.simulcast.data.Settings
import com.stellar.simulcast.data.Transfer
import com.stellar.simulcast.work.DigestWorker
import com.stellar.simulcast.work.EpisodeCheckWorker

/** The "Move to another phone" part of Settings. */
@Composable
fun TransferSection() {
    val ctx = LocalContext.current
    var qrParts by remember { mutableStateOf<List<String>?>(null) }
    var qrIndex by remember { mutableStateOf(0) }
    // Parts collected so far while scanning a multi-code transfer.
    var received by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var showPaste by remember { mutableStateOf(false) }
    var confirmCode by remember { mutableStateOf<String?>(null) }

    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(Transfer.export(ctx).toByteArray()) }
            Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            if (text != null) confirmCode = text else Toast.makeText(ctx, "Couldn't read that file", Toast.LENGTH_SHORT).show()
        }
    }
    fun scanOptions(prompt: String) = ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        .setPrompt(prompt).setBeepEnabled(false).setOrientationLocked(false)

    lateinit var scanLauncher: androidx.activity.result.ActivityResultLauncher<ScanOptions>
    scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        val part = Transfer.parsePart(text)
        if (part == null) {
            confirmCode = text
            return@rememberLauncherForActivityResult
        }
        val (i, n, data) = part
        val got = received + (i to data)
        if (got.size >= n && (1..n).all { it in got }) {
            received = emptyMap()
            confirmCode = (1..n).joinToString("") { got.getValue(it) }
        } else {
            received = got
            val missing = (1..n).first { it !in got }
            Toast.makeText(ctx, "Got ${got.size} of $n. Scan code $missing next.", Toast.LENGTH_SHORT).show()
            scanLauncher.launch(scanOptions("Scan code $missing of $n"))
        }
    }

    Text(
        "Copy your watchlist, hidden shows, progress, followed manga, reading spots and settings to another phone. No account needed.",
        color = Space.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp),
    )
    Text("Send from this phone", color = Space.Text)
    OutlinedButton(onClick = {
        qrIndex = 0
        qrParts = Transfer.parts(Transfer.export(ctx))
    }, modifier = Modifier.fillMaxWidth()) { Text("Show QR code", color = Space.Cyan) }
    OutlinedButton(onClick = {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, Transfer.export(ctx))
        }
        ctx.startActivity(Intent.createChooser(send, "Send Stellar Simulcast data"))
    }, modifier = Modifier.fillMaxWidth()) { Text("Share code", color = Space.Cyan) }
    OutlinedButton(onClick = { saveFile.launch("stellar-simulcast-backup.txt") }, modifier = Modifier.fillMaxWidth()) {
        Text("Save to a file", color = Space.Cyan)
    }

    Text("Receive on this phone", color = Space.Text, modifier = Modifier.padding(top = 10.dp))
    OutlinedButton(onClick = {
        received = emptyMap()
        scanLauncher.launch(scanOptions("Scan the code on your other phone"))
    }, modifier = Modifier.fillMaxWidth()) { Text("Scan QR code", color = Space.Magenta) }
    OutlinedButton(onClick = { showPaste = true }, modifier = Modifier.fillMaxWidth()) { Text("Paste code", color = Space.Magenta) }
    OutlinedButton(onClick = { openFile.launch(arrayOf("text/plain")) }, modifier = Modifier.fillMaxWidth()) {
        Text("Open a file", color = Space.Magenta)
    }

    qrParts?.let { parts ->
        val code = parts[qrIndex.coerceIn(0, parts.lastIndex)]
        val bitmap = remember(code) { qr(code) }
        AlertDialog(
            onDismissRequest = { qrParts = null },
            containerColor = Space.Surface,
            title = {
                Text(if (parts.size > 1) "Code ${qrIndex + 1} of ${parts.size}" else "Scan on your other phone", color = Space.Cyan)
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(bitmap, "Transfer QR code", Modifier.size(260.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp))
                    Text(if (parts.size > 1) "Big library, so it's split into ${parts.size} codes. Scan them in order; the other phone keeps the scanner open until it has them all." else "In Stellar Simulcast on the new phone: Settings → Move to another phone → Scan QR code.",
                        color = Space.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                if (qrIndex < parts.lastIndex) TextButton(onClick = { qrIndex++ }) { Text("Next code", color = Space.Cyan) }
                else TextButton(onClick = { qrParts = null }) { Text("Done", color = Space.Cyan) }
            },
            dismissButton = {
                if (qrIndex > 0) TextButton(onClick = { qrIndex-- }) { Text("Back", color = Space.Muted) }
            },
        )
    }

    if (showPaste) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPaste = false },
            containerColor = Space.Surface,
            title = { Text("Paste code", color = Space.Cyan) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text("SSIM1:…") }, maxLines = 6) },
            confirmButton = { TextButton(onClick = { showPaste = false; confirmCode = text }) { Text("Next", color = Space.Cyan) } },
            dismissButton = { TextButton(onClick = { showPaste = false }) { Text("Cancel", color = Space.Muted) } },
        )
    }

    confirmCode?.let { code ->
        AlertDialog(
            onDismissRequest = { confirmCode = null },
            containerColor = Space.Surface,
            title = { Text("Replace this phone's data?", color = Space.Magenta) },
            text = { Text("Your watchlist, library, progress and settings here will be replaced with the ones from the other phone.", color = Space.Text) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCode = null
                    if (Transfer.restore(ctx, code)) {
                        EpisodeCheckWorker.schedule(ctx, Settings.alertPrefs.intervalMin, replace = true)
                        DigestWorker.schedule(ctx, Settings.alertPrefs, replace = true)
                        Toast.makeText(ctx, "Everything's moved over", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "That isn't a valid Stellar Simulcast code", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Replace", color = Space.Magenta) }
            },
            dismissButton = { TextButton(onClick = { confirmCode = null }) { Text("Cancel", color = Space.Muted) } },
        )
    }
}

private fun qr(text: String, size: Int = 720): androidx.compose.ui.graphics.ImageBitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return android.graphics.Bitmap.createBitmap(pixels, m.width, m.height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}
