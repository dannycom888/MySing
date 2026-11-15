package com.dannycom888.shieldkaraoke

import android.app.Application
import com.dannycom888.shieldkaraoke.server.KaraokeHttpServer
import com.dannycom888.shieldkaraoke.state.KaraokeState
import com.dannycom888.shieldkaraoke.state.Prefs

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
        ).also { it.start() }
    }

    fun localServerPort(): Int = server?.port ?: KaraokeHttpServer.DEFAULT_PORT
}
