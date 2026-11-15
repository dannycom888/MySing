package com.dannycom888.shieldkaraoke

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.dannycom888.shieldkaraoke.server.DancerImages
import com.dannycom888.shieldkaraoke.server.KaraokeHttpServer
import com.dannycom888.shieldkaraoke.state.KaraokeState
import com.dannycom888.shieldkaraoke.state.Prefs
import com.dannycom888.shieldkaraoke.ui.Dancers
import java.io.ByteArrayOutputStream

/**
 * MySing — 本地 KTV 点歌播放器。
 * 本软件由 Muse 协助制作。
 */
class KaraokeApp : Application() {

    lateinit var state: KaraokeState
        private set
    lateinit var prefs: Prefs
        private set

    private var server: KaraokeHttpServer? = null

    override fun onCreate() {
        super.onCreate()
        state = KaraokeState()
        prefs = Prefs(this)
        server = KaraokeHttpServer(
            state = state,
            port = KaraokeHttpServer.DEFAULT_PORT,
            favoritesProvider = { prefs.favorites() },
            recentsProvider = { prefs.recents() },
            favoriteToggle = { id, fav -> prefs.setFavorite(id, fav) },
            dancerSelectionsProvider = { prefs.dancerSelections() },
            dancerSetter = { name, key ->
                prefs.setDancer(name, key)
                state.bump() // TV player screen re-reads the current song's dancer live
            },
            dancerImageProvider = { key -> dancerImage(key) },
        ).also { it.start() }
    }

    /** Portrait bytes for the phone page's dancer picker, decoded
     *  and downscaled ONCE per dancer, then cached — the HTTP
     *  server never re-decodes per request. */
    private val dancerImageCache = mutableMapOf<String, ByteArray?>()

    private fun dancerImage(key: String): ByteArray? {
        synchronized(dancerImageCache) {
            if (dancerImageCache.containsKey(key)) return dancerImageCache[key]
            val bytes = try {
                val dancer = Dancers.ALL.firstOrNull { it.key == key }
                val bmp = dancer?.let { BitmapFactory.decodeResource(resources, it.resId) }
                if (bmp == null) {
                    null
                } else {
                    val scaled = if (bmp.height > DancerImages.SERVED_HEIGHT_PX) {
                        val w = (bmp.width.toFloat() * DancerImages.SERVED_HEIGHT_PX / bmp.height)
                            .toInt().coerceAtLeast(1)
                        Bitmap.createScaledBitmap(bmp, w, DancerImages.SERVED_HEIGHT_PX, true)
                    } else {
                        bmp
                    }
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, baos)
                    baos.toByteArray()
                }
            } catch (e: Exception) {
                null
            }
            dancerImageCache[key] = bytes
            return bytes
        }
    }

    fun localServerPort(): Int = server?.port ?: KaraokeHttpServer.DEFAULT_PORT
}
