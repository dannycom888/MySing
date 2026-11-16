package com.dannycom888.shieldkaraoke.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.delay

/** QR code of the phone-ordering URL + LAN address discovery. */
object Qr {

    fun encode(text: String, size: Int = 360): Bitmap {
        val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        return bitmap
    }

    /** First site-local IPv4 of this device (the Shield's LAN address). */
    fun lanIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val addresses = interfaces.nextElement().inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address && addr.isSiteLocalAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            // No network: no address; the corner card shows its
            // waiting-for-network placeholder instead (v1.0.42).
        }
        return null
    }
}

/** Cadence of the corner card's LAN-address refresh (v1.0.42). */
object LanIpPoll {
    const val INTERVAL_MS = 5_000L
}

/**
 * The visible LAN address of the corner QR card, as a tiny state
 * machine the unit tests can pin (v1.0.42).
 *
 * Before this, the card read [Qr.lanIpAddress] inside
 * `remember(app.state.version)`: the address was only re-fetched
 * when the queue state bumped, so during long stretches of random
 * playback it never refreshed at all. If WiFi was not up yet when
 * the app started — or dropped and came back — the card stayed
 * missing forever (and after a DHCP change it kept printing the
 * stale address). Now the address is polled slowly and the card
 * always shows the LATEST fetch: a real address renders the QR,
 * no address renders the placeholder, and a changed address
 * replaces the stale one.
 */
class LanIpTracker(initial: String?) {

    var current: String? = initial
        private set

    /** True while there is no address to print a QR for. */
    val showsPlaceholder: Boolean
        get() = current == null

    /**
     * Adopt a fresh fetch. Returns true when the visible value
     * changed (the caller then republishes state; an unchanged
     * fetch costs no recomposition and no new QR bitmap).
     */
    fun onFetched(fetched: String?): Boolean {
        val changed = fetched != current
        current = fetched
        return changed
    }
}

/**
 * The corner card's LAN address, refreshed on a slow poll
 * ([LanIpPoll.INTERVAL_MS]) for as long as the caller is composed.
 * The first value is fetched synchronously (as before); afterwards
 * a [LaunchedEffect] loop re-fetches and only republishes when the
 * value actually changed. No threads are started — the loop rides
 * the composition's own effect scope, like the player's existing
 * diag tick.
 */
@Composable
fun rememberLanIpAddress(): String? {
    val tracker = remember { LanIpTracker(Qr.lanIpAddress()) }
    var ip by remember { mutableStateOf(tracker.current) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(LanIpPoll.INTERVAL_MS)
            if (tracker.onFetched(Qr.lanIpAddress())) {
                ip = tracker.current
            }
        }
    }
    return ip
}
