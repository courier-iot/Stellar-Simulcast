package com.stellar.simulcast.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Time text that follows the 12/24-hour setting. Reads Compose state, so screens refresh when it changes. */
object TimeText {
    private val clock12 = DateTimeFormatter.ofPattern("h:mm a")
    private val clock24 = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d")
    private val longDateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")

    private fun zoned(epoch: Long) = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault())

    fun clock(epoch: Long): String =
        zoned(epoch).format(if (Settings.display.use24h) clock24 else clock12)

    fun dateTime(epoch: Long): String = zoned(epoch).format(dateFmt) + " · " + clock(epoch)

    fun date(epoch: Long): String = zoned(epoch).format(dateFmt)

    fun longDate(d: LocalDate): String = d.format(longDateFmt)

    fun hour(h: Int): String = when {
        Settings.display.use24h -> "%02d:00".format(h)
        h == 0 -> "12 AM"
        h < 12 -> "$h AM"
        h == 12 -> "12 PM"
        else -> "${h - 12} PM"
    }

    /** When an episode shows up for you: Japan air time plus your stream delay. */
    fun streamAt(airingAt: Long): Long = airingAt + Settings.delaySec
}
