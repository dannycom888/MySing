package com.dannycom888.shieldkaraoke.ui

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.text.InputFilter
import android.view.WindowManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import com.dannycom888.shieldkaraoke.KaraokeApp
import com.dannycom888.shieldkaraoke.audio.AudioMath
import com.dannycom888.shieldkaraoke.audio.LoudnessAnalyzer
import com.dannycom888.shieldkaraoke.audio.PlayerController
import com.dannycom888.shieldkaraoke.audio.TempoAnalyzer
import com.dannycom888.shieldkaraoke.i18n.TvKey
import com.dannycom888.shieldkaraoke.i18n.TvStrings
import com.dannycom888.shieldkaraoke.i18n.UiLang
import com.dannycom888.shieldkaraoke.library.Catalog
import com.dannycom888.shieldkaraoke.library.Gender
import com.dannycom888.shieldkaraoke.library.Language
import com.dannycom888.shieldkaraoke.library.LibraryCacheStore
import com.dannycom888.shieldkaraoke.library.LibraryScanner
import com.dannycom888.shieldkaraoke.library.SafLibrarySource
import com.dannycom888.shieldkaraoke.library.Song
import com.dannycom888.shieldkaraoke.lyrics.LrcParser
import com.dannycom888.shieldkaraoke.lyrics.LyricLine
import com.dannycom888.shieldkaraoke.queue.EntrySource
import com.dannycom888.shieldkaraoke.queue.FillerPicker
import com.dannycom888.shieldkaraoke.queue.FillerPolicy
import com.dannycom888.shieldkaraoke.queue.FillerSession
import com.dannycom888.shieldkaraoke.queue.QueueEntry
import com.dannycom888.shieldkaraoke.state.DancerNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Screen { HOME, BROWSE, PLAYER, ADMIN }
private enum class ListType { SONGS, SINGERS }
private enum class Special { NONE, FAVORITES, RECENTS }

/** Where the player queue panel's focus plumbing moves D-pad focus
 *  next (v1.0.19); set by panel / control-bar events, consumed once
 *  by a LaunchedEffect in PlayerScreen. */
private sealed interface PanelFocusTarget {
    object None : PanelFocusTarget
    data class Row(val index: Int) : PanelFocusTarget
    object PromoteAction : PanelFocusTarget
    object DeleteAction : PanelFocusTarget
    object ControlBar : PanelFocusTarget
}

private val bg = Color(0xFF0D1B3E)
private val cardBg = Color(0xFF16295C)
private val gold = Color(0xFFFFD54F)
private val soft = Color(0xFF9DB4E8)

/** Dance tempo when a song has not been measured (yet). */
private const val DEFAULT_BPM = 96.0

/** applause.wav is exactly 3.8 s (v1.0.14 crowd synthesis; see
 *  audio/make_applause.py). The celebration stays up for
 *  APPLAUSE_MS + a small margin so the thinning tail is never cut;
 *  with a next song queued, the 5 s interlude covers it fully. */
private const val APPLAUSE_MS = 3800L

class MainActivity : ComponentActivity() {

    private lateinit var app: KaraokeApp
    private lateinit var controller: PlayerController
    private var applausePlayer: MediaPlayer? = null

    private var screen by mutableStateOf(Screen.HOME)
    private var lyrics by mutableStateOf<List<LyricLine>>(emptyList())
    private var positionMs by mutableStateOf(0L)
    private var durationMs by mutableStateOf(-1L)
    /** Red error banner text, refreshed by the player poll loop
     *  from [PlayerController.visibleError] (v1.0.15). */
    private var errorBanner by mutableStateOf<String?>(null)
    private var tickMs by mutableStateOf(0L)
    private var dancersOn by mutableStateOf(true)
    /** Idle filler playback switch (v1.0.20), persisted in prefs. */
    private var fillerOn by mutableStateOf(true)
    private val fillerPicker = FillerPicker()

    /** Filler session tracking (v1.0.24): only the first filler
     *  song of a session brings the player screen forward. */
    private val fillerSession = FillerSession()
    private var currentBpm by mutableStateOf(DEFAULT_BPM)
    private var celebrating by mutableStateOf(false)
    private var applauseStartMs by mutableStateOf(0L)
    private var celebrationToken = 0
    /** The v1.0.23 handover ceremony (see [Ceremony]): the next
     *  entry locked in when a song finished, the keeper dancer's
     *  key (the next orderer's choice) and the wall-clock start.
     *  [ceremonyNext] null = no ceremony running. */
    private var ceremonyNext by mutableStateOf<QueueEntry?>(null)
    private var ceremonyKeeperKey by mutableStateOf<String?>(null)
    private var ceremonyStartMs by mutableStateOf(0L)
    private var confirmQuit by mutableStateOf(false)
    /** Host-renamed dancer names (v1.0.21), mirrored from prefs
     *  so every label on the TV resolves through [DancerNames]
     *  against the same snapshot; refreshed after each admin edit. */
    private var customDancerNames by mutableStateOf<Map<String, String>>(emptyMap())
    private var confirmRestoreNames by mutableStateOf(false)
    private var accompaniment by mutableStateOf(false)
    private var semitoneLabel by mutableStateOf(0)
    private var scanning by mutableStateOf(false)
    private var quitting = false

    /** TV interface language (v1.0.13), switched on the home
     *  screen and persisted in prefs. Every user-facing string on
     *  the TV goes through [s] and the zh/en table — song and
     *  singer names are file-name data and stay untranslated. */
    private var uiLang by mutableStateOf(UiLang.ZH)

    private fun s(key: TvKey, vararg args: Any): String = TvStrings.get(uiLang, key, *args)

    private fun switchUiLang(lang: UiLang) {
        uiLang = lang
        app.prefs.uiLangCode = lang.code
    }

    /** Every dancer name shown anywhere on the TV resolves here
     *  (v1.0.21): the host's custom name when one is set on the
     *  admin page, the bilingual default stage name otherwise. */
    private fun dancerDisplayName(key: String?): String =
        DancerNames.display(key, uiLang, customDancerNames)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A karaoke box must not go dark mid-party (v1.0.8): keep
        // the screen on for as long as the app is in the
        // foreground. No setting for this — it is simply what a
        // song-ordering TV does — and the flag is not a permission;
        // leaving the app restores the system's own timeout.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        app = application as KaraokeApp
        dancersOn = app.prefs.dancersEnabled
        fillerOn = app.prefs.randomFillerEnabled
        customDancerNames = app.prefs.customDancerNames()
        uiLang = UiLang.parse(app.prefs.uiLangCode)
        controller = PlayerController(this)
        controller.onEnded = { onSongEndedNaturally() }
        app.state.onShouldStart = { entry ->
            runOnUiThread { startSong(entry); screen = Screen.PLAYER }
        }
        app.prefs.libraryTreeUri?.let { loadLibrary(Uri.parse(it)) }

        setContent {
            MaterialTheme {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize().background(bg)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                if (event.key == Key.MediaPlayPause) controller.togglePlayPause()
                            }
                            false
                        },
                ) {
                    val compact = maxHeight < 500.dp
                    when (screen) {
                        Screen.HOME -> HomeScreen(compact)
                        Screen.BROWSE -> BrowseScreen(compact)
                        Screen.PLAYER -> PlayerScreen(compact)
                        Screen.ADMIN -> AdminScreen(compact)
                    }
                    // Corner QR: pinned to the TOP-RIGHT corner at
                    // all times so a phone can join in mid-song
                    // (v1.0.19 — it lived bottom-right before, and
                    // Home had its own big QR; now every screen uses
                    // this one card in this one corner. Home renders
                    // the same card in-flow in its header instead of
                    // this overlay, so the language chips next to it
                    // are never covered.)
                    val cornerIp = remember(app.state.version) { Qr.lanIpAddress() }
                    if (cornerIp != null && screen != Screen.HOME) {
                        CornerQrCard(
                            ip = cornerIp,
                            port = app.localServerPort(),
                            compact = compact,
                            modifier = Modifier.align(Alignment.TopEnd)
                                .padding(
                                    end = if (compact) 12.dp else 24.dp,
                                    top = if (compact) 12.dp else 24.dp,
                                ),
                        )
                    }
                    if (confirmQuit) {
                        AlertDialog(
                            onDismissRequest = { confirmQuit = false },
                            title = { Text(s(TvKey.QUIT_TITLE)) },
                            text = { Text(s(TvKey.QUIT_TEXT)) },
                            confirmButton = {
                                Button(onClick = { confirmQuit = false; quitCurrent() }) { Text(s(TvKey.QUIT_CONFIRM)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmQuit = false }) { Text(s(TvKey.QUIT_CANCEL)) }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        applausePlayer?.release()
        controller.release()
        super.onDestroy()
    }

    // ---------- library ----------

    /** True once a real SAF scan has landed in this process; the
     *  cached catalog may only fill the state before that. */
    private var freshScanDone = false

    /**
     * Two-stage library load: the last scan's cached catalog fills
     * the lists immediately (singers appear at once instead of
     * waiting for the whole SAF tree walk), then the normal full
     * scan runs and replaces it wholesale.
     */
    private fun loadLibrary(treeUri: Uri) {
        lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) {
                LibraryCacheStore(this@MainActivity).load(treeUri.toString())
            }
            if (cached != null && !freshScanDone && app.state.catalog.songs.isEmpty()) {
                app.state.setCatalog(Catalog(cached))
                // Library is usable: if nobody has ordered anything,
                // the idle filler may start (v1.0.20 trigger a).
                maybeStartFiller()
            }
        }
        rescan(treeUri)
    }

    private fun rescan(treeUri: Uri) {
        scanning = true
        lifecycleScope.launch {
            val songs = withContext(Dispatchers.IO) {
                LibraryScanner.scan(SafLibrarySource.listFiles(this@MainActivity, treeUri))
            }
            app.state.setCatalog(Catalog(songs))
            freshScanDone = true
            scanning = false
            maybeStartFiller()
            withContext(Dispatchers.IO) {
                LibraryCacheStore(this@MainActivity).save(treeUri.toString(), songs)
            }
        }
    }

    // ---------- playback flow ----------

    private fun startSong(entry: QueueEntry) {
        app.state.nowPlaying = entry
        // An ordered song taking the stage ends any filler session
        // (v1.0.24): the next filler start after it drains is a
        // fresh session-entry edge that brings the player screen
        // forward again.
        if (!entry.isFiller) fillerSession.onOrderedStarted()
        // Filler songs were sung by nobody: they stay out of the
        // "recently sung" list just as they stay out of the queue.
        if (!entry.isFiller) app.prefs.pushRecent(entry.song.id)
        lyrics = emptyList()
        positionMs = 0
        errorBanner = null
        accompaniment = false
        semitoneLabel = 0
        celebrating = false
        celebrationToken++
        clearCeremony()
        val uri = Uri.parse(entry.song.audioRef)
        val cachedGain = app.prefs.loudnessDb(entry.song.id)
        controller.load(uri, AudioMath.normalizationGainDb(cachedGain))
        // Dance tempo: cached per song next to the loudness cache;
        // measured in the background when missing (default 96 BPM).
        val cachedBpm = app.prefs.bpm(entry.song.id)
        currentBpm = cachedBpm ?: DEFAULT_BPM
        if (cachedBpm == null) {
            lifecycleScope.launch {
                val measured = withContext(Dispatchers.IO) { TempoAnalyzer.measureBpm(this@MainActivity, uri) }
                if (measured != null) {
                    app.prefs.setBpm(entry.song.id, measured)
                    if (app.state.nowPlaying?.song?.id == entry.song.id) {
                        currentBpm = measured
                    }
                }
            }
        }
        entry.song.lyricsRef?.let { ref ->
            lifecycleScope.launch {
                val parsed = withContext(Dispatchers.IO) {
                    try {
                        contentResolver.openInputStream(Uri.parse(ref))?.bufferedReader()?.readText()
                            ?.let { LrcParser.parse(it) } ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                if (app.state.nowPlaying?.song?.id == entry.song.id) lyrics = parsed
            }
        }
        if (cachedGain == null) {
            lifecycleScope.launch {
                val measured = withContext(Dispatchers.IO) { LoudnessAnalyzer.measureRmsDbfs(this@MainActivity, uri) }
                if (measured != null) {
                    app.prefs.setLoudnessDb(entry.song.id, measured)
                    if (app.state.nowPlaying?.song?.id == entry.song.id) {
                        controller.applyGainDb(AudioMath.normalizationGainDb(measured))
                    }
                }
            }
        }
    }

    // ---------- idle filler playback (v1.0.20) ----------

    /** A standalone filler entry: typed source marker, no orderer
     *  (the dancer lookup falls back to the default dancer, exactly
     *  like an order with no dancer choice on file). Never added
     *  to the PlayQueue, so it leaves no trace in any queue view. */
    private fun pickFillerEntry(): QueueEntry? {
        val song = fillerPicker.pick(app.state.catalog.songs) ?: return null
        return QueueEntry(song, "", EntrySource.FILLER)
    }

    /**
     * Start a filler song if the policy says the stage is free for
     * one. Returns true when a song was started. Called when the
     * library becomes ready, when the real queue drains (naturally
     * or by quit), and when the filler switch is turned on.
     *
     * Screen rule (v1.0.24): the first filler song of a session —
     * the [FillerSession] entry edge — brings the player screen
     * forward from wherever the TV is (Home, Browse, Admin), so a
     * filler session can never sing invisibly behind another
     * screen. Rollovers inside the session never touch the screen:
     * a host who backed out to Home on purpose stays there, and
     * opens the player screen (the home screen's now-singing card)
     * to find the filler song's full picture — lyrics, dancer,
     * title — because [startSong] loaded it exactly like an
     * ordered song. (Before v1.0.24 the entry edge fired only from
     * Home and rollovers never fired at all, which is how filler
     * ended up singing with no picture and no lyrics.)
     */
    private fun maybeStartFiller(): Boolean {
        val canStart = FillerPolicy.shouldAutoStart(
            enabled = fillerOn,
            catalogReady = app.state.catalog.songs.isNotEmpty(),
            queueIdle = app.state.queue.isIdle,
            somethingPlaying = app.state.nowPlaying != null || celebrating,
        )
        if (!canStart) return false
        val entry = pickFillerEntry() ?: return false
        startSong(entry)
        if (fillerSession.onFillerStarted()) screen = Screen.PLAYER
        return true
    }

    /** A filler song ended (or was quit): no applause, no interlude
     *  countdown — straight to the next random pick, or back to the
     *  idle home screen when filler is off / the library is empty. */
    private fun startNextFillerOrHome() {
        app.state.nowPlaying = null
        if (!maybeStartFiller()) {
            // No next filler could start: the session is over, and
            // the stage falls back to the idle home screen.
            fillerSession.onStopped()
            if (screen == Screen.PLAYER) screen = Screen.HOME
        }
    }

    /** The control-bar 随机 switch. Turning it off mid-filler stops
     *  the song and returns to the idle home screen (the pre-v1.0.20
     *  behaviour); turning it on starts filler if the stage is free. */
    private fun toggleFiller() {
        fillerOn = !fillerOn
        app.prefs.randomFillerEnabled = fillerOn
        if (!fillerOn) {
            if (app.state.nowPlaying?.isFiller == true) {
                quitting = true
                controller.stop()
                quitting = false
                celebrating = false
                celebrationToken++
                app.state.interludeSeconds = 0
                app.state.nowPlaying = null
                fillerSession.onStopped()
                screen = Screen.HOME
            }
        } else {
            maybeStartFiller()
        }
    }

    private fun onSongEndedNaturally() {
        if (quitting) return
        runOnUiThread {
            // A filler song ending is not a performance ending:
            // no applause, no queue advance (it was never in the
            // queue) — just the next random pick. A real order that
            // arrived meanwhile would already have cut the filler
            // off via onShouldStart, so the queue is idle here.
            if (app.state.nowPlaying?.isFiller == true) {
                startNextFillerOrHome()
                return@runOnUiThread
            }
            val next = app.state.advanceAfterFinish()
            // Applause celebration: all six dancers come out and clap
            // along with the applause sound — with or without a next
            // song, and regardless of the per-orderer dancer choice.
            playApplause()
            celebrating = true
            applauseStartMs = SystemClock.elapsedRealtime()
            val token = ++celebrationToken
            if (next == null) {
                // No next song: stay on the player screen until the
                // applause has played out — only then go home, so the
                // celebration is never swallowed by the screen switch.
                lifecycleScope.launch {
                    delay(APPLAUSE_MS + 300)
                    if (celebrationToken == token && celebrating) {
                        celebrating = false
                        app.state.nowPlaying = null
                        // The applause belonged to that last ordered
                        // song; with the real queue drained, the idle
                        // filler takes over from here (v1.0.20
                        // trigger b) — or home when filler is off.
                        if (!maybeStartFiller()) screen = Screen.HOME
                    }
                }
            } else {
                // A real next song is queued: the applause opens the
                // v1.0.23 handover ceremony (see Ceremony) — the six
                // clap under a big next-song card, invite together,
                // five fade away, the next orderer's dancer invites
                // once more alone, then the song starts. The
                // ceremony is locked to this entry; at every segment
                // boundary the lock is re-checked (Ceremony's
                // interruption rule) — a superseding flow (token
                // bump: quit, filler toggle…) simply ends it.
                ceremonyNext = next
                ceremonyKeeperKey = Dancers.byKey(app.prefs.dancerFor(next.orderedBy)).key
                ceremonyStartMs = applauseStartMs
                lifecycleScope.launch {
                    var lastPhase = Ceremony.Phase.CLAPPING
                    while (celebrationToken == token) {
                        delay(100)
                        if (celebrationToken != token) return@launch
                        val phase = Ceremony.phaseAt(
                            SystemClock.elapsedRealtime() - ceremonyStartMs,
                        )
                        if (phase != lastPhase) {
                            val current = app.state.queue.current
                            when (
                                Ceremony.boundaryAction(
                                    lockedStillCurrent = current === next,
                                    hasCurrent = current != null,
                                )
                            ) {
                                Ceremony.BoundaryAction.CONTINUE -> lastPhase = phase
                                Ceremony.BoundaryAction.START_CURRENT -> {
                                    clearCeremony()
                                    startSong(current!!)
                                    return@launch
                                }
                                Ceremony.BoundaryAction.END_TO_IDLE -> {
                                    clearCeremony()
                                    celebrating = false
                                    app.state.nowPlaying = null
                                    if (!maybeStartFiller()) screen = Screen.HOME
                                    return@launch
                                }
                            }
                        }
                        if (phase == Ceremony.Phase.DONE) {
                            clearCeremony()
                            startSong(next)
                            return@launch
                        }
                    }
                }
            }
        }
    }

    private fun quitCurrent() {
        quitting = true
        controller.stop()
        quitting = false
        celebrating = false
        celebrationToken++
        clearCeremony()
        app.state.interludeSeconds = 0
        if (app.state.nowPlaying?.isFiller == true) {
            // Cutting a filler song (the remote holder's 切歌 on a
            // background song): it was never in the queue, so there
            // is nothing to advance — just deal the next random one.
            startNextFillerOrHome()
            return
        }
        val next = app.state.advanceAfterQuit()
        if (next == null) {
            app.state.nowPlaying = null
            // The stage is idle: the filler takes over (v1.0.24 —
            // quitting the last ordered song used to strand the TV
            // on a silent home screen even with the filler switch
            // on, unlike the natural-end drain). Home only when no
            // filler can start (switch off / library empty).
            if (!maybeStartFiller()) screen = Screen.HOME
        } else {
            startSong(next)
        }
    }

    /** End the handover ceremony's state (its driver coroutine is
     *  separately retired by the celebration token). */
    private fun clearCeremony() {
        ceremonyNext = null
        ceremonyKeeperKey = null
    }

    private fun playApplause() {
        try {
            applausePlayer?.release()
            applausePlayer = MediaPlayer().apply {
                assets.openFd("applause.wav").use { fd ->
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Applause is garnish; never let it break the flow.
        }
    }

    private fun orderFromTv(song: Song) {
        // The TV's own orders carry the TV as orderer name, in the
        // interface's current language （电视 / TV).
        app.state.order(song, s(TvKey.TV_ORDERER))
        if (app.state.queue.current != null) screen = Screen.PLAYER
    }

    // ---------- screens ----------

    @Composable
    private fun HomeScreen(compact: Boolean) {
        val state = app.state
        val ip = remember(state.version) { Qr.lanIpAddress() }
        val port = app.localServerPort()
        Column(modifier = Modifier.fillMaxSize().padding(if (compact) 14.dp else 28.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("MySing", color = gold, fontSize = (if (compact) 24 else 36).sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            scanning -> s(TvKey.SCANNING)
                            state.catalog.songs.isEmpty() -> s(TvKey.PICK_FOLDER)
                            else -> s(TvKey.LIBRARY_COUNT, state.catalog.songs.size)
                        },
                        color = soft, fontSize = (if (compact) 13 else 17).sp,
                    )
                    if (!app.serverAlive) {
                        Text(
                            s(TvKey.SERVER_DOWN) +
                                (app.serverError?.let { "（$it）" } ?: ""),
                            color = Color(0xFFFF7043), fontSize = (if (compact) 12 else 15).sp,
                        )
                    }
                }
                // Language switch (v1.0.13): William can't read
                // Chinese, so the whole TV UI flips to English here
                // and stays flipped across restarts (prefs). The two
                // chips label themselves in their own language, so
                // the switch is findable from either side.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(end = if (compact) 8.dp else 16.dp),
                ) {
                    Chip("中文", uiLang == UiLang.ZH) { switchUiLang(UiLang.ZH) }
                    Chip("EN", uiLang == UiLang.EN) { switchUiLang(UiLang.EN) }
                    // Admin entry (v1.0.21): rename the dancers —
                    // same chip style as the language switch.
                    Chip(s(TvKey.ADMIN_TITLE), false) { screen = Screen.ADMIN }
                }
                if (ip != null) {
                    // v1.0.19: the home header's big QR is gone — the
                    // header carries the same corner card as every
                    // other screen, in-flow at the row's right end
                    // (the top-right corner), so nothing overlaps.
                    CornerQrCard(ip = ip, port = port, compact = compact)
                }
            }
            Spacer(Modifier.height(if (compact) 10.dp else 20.dp))
            val entries = listOf(
                s(TvKey.ENTRY_SINGERS) to { openBrowse(ListType.SINGERS, null, null, Special.NONE) },
                s(TvKey.ENTRY_SONGS) to { openBrowse(ListType.SONGS, null, null, Special.NONE) },
                s(TvKey.ENTRY_CHINESE) to { openBrowse(ListType.SONGS, Language.CHINESE, null, Special.NONE) },
                s(TvKey.ENTRY_ENGLISH) to { openBrowse(ListType.SONGS, Language.ENGLISH, null, Special.NONE) },
                s(TvKey.ENTRY_FAVORITES) to { openBrowse(ListType.SONGS, null, null, Special.FAVORITES) },
                s(TvKey.ENTRY_RECENTS) to { openBrowse(ListType.SONGS, null, null, Special.RECENTS) },
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(entries) { (label, action) ->
                    EntryCard(label, compact, action)
                }
            }
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    if (uri != null) {
                        try {
                            contentResolver.takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        } catch (e: Exception) { /* still usable this session */ }
                        app.prefs.libraryTreeUri = uri.toString()
                        loadLibrary(uri)
                    }
                }
                EntryCard(s(TvKey.PICK_FOLDER_BTN), compact) { picker.launch(null) }
                EntryCard(s(TvKey.RESCAN), compact) {
                    app.prefs.libraryTreeUri?.let { rescan(Uri.parse(it)) }
                }
                if (state.nowPlaying != null) {
                    EntryCard(s(TvKey.NOW_SINGING_CARD, state.nowPlaying!!.song.displayName), compact) { screen = Screen.PLAYER }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(s(TvKey.CREDIT), color = soft.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }

    // ---------- admin: rename the dancers (v1.0.21) ----------

    /**
     * The TV admin page. Friends come over, the host puts their
     * names on the dancers here. Renaming is TV-only: the phone
     * server reads the names for its picker cards but has no
     * write path. Rows carry the dancer's standing frame so the
     * host recognises who is who even after a rename; a renamed
     * row also shows the default it would fall back to.
     */
    @Composable
    private fun AdminScreen(compact: Boolean) {
        BackHandler { screen = Screen.HOME }
        // The corner QR overlay is pinned top-right here too;
        // reserve its width so no row slides under it (same
        // pattern as BrowseScreen).
        val qrEndReserve = if (remember(app.state.version) { Qr.lanIpAddress() } != null) {
            if (compact) 88.dp else 112.dp
        } else {
            0.dp
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(
                start = if (compact) 14.dp else 28.dp,
                top = if (compact) 14.dp else 28.dp,
                bottom = if (compact) 14.dp else 28.dp,
                end = (if (compact) 14.dp else 28.dp) + qrEndReserve,
            ),
        ) {
            Text(s(TvKey.ADMIN_TITLE), color = gold, fontSize = (if (compact) 22 else 32).sp, fontWeight = FontWeight.Bold)
            Text(s(TvKey.ADMIN_HINT), color = soft, fontSize = (if (compact) 13 else 16).sp)
            Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Dancers.ALL.forEach { dancer ->
                    AdminDancerRow(dancer, compact) { showRenameDialog(dancer) }
                }
            }
            Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EntryCard(s(TvKey.ADMIN_RESTORE), compact) { confirmRestoreNames = true }
                EntryCard(s(TvKey.ADMIN_BACK), compact) { screen = Screen.HOME }
            }
        }
        if (confirmRestoreNames) {
            AlertDialog(
                onDismissRequest = { confirmRestoreNames = false },
                title = { Text(s(TvKey.ADMIN_RESTORE_TITLE)) },
                text = { Text(s(TvKey.ADMIN_RESTORE_TEXT)) },
                confirmButton = {
                    Button(onClick = {
                        confirmRestoreNames = false
                        app.prefs.clearCustomDancerNames()
                        customDancerNames = emptyMap()
                    }) { Text(s(TvKey.ADMIN_RESTORE_CONFIRM)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmRestoreNames = false }) { Text(s(TvKey.CANCEL)) }
                },
            )
        }
    }

    @Composable
    private fun AdminDancerRow(dancer: Dancer, compact: Boolean, onClick: () -> Unit) {
        var focused by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, if (focused) gold else Color.Transparent, RoundedCornerShape(10.dp))
                .background(if (focused) cardBg else Color.Transparent, RoundedCornerShape(10.dp))
                .focusable()
                .onFocusChanged { focused = it.isFocused }
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(dancer.resId),
                contentDescription = dancerDisplayName(dancer.key),
                modifier = Modifier
                    .height(if (compact) 40.dp else 56.dp)
                    .width(if (compact) 32.dp else 44.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                dancerDisplayName(dancer.key),
                color = Color.White,
                fontSize = (if (compact) 16 else 21).sp,
                fontWeight = FontWeight.Bold,
            )
            if (customDancerNames.containsKey(dancer.key)) {
                Spacer(Modifier.width(10.dp))
                Text(
                    s(TvKey.ADMIN_DEFAULT, DancerNames.display(dancer.key, uiLang, emptyMap())),
                    color = soft, fontSize = (if (compact) 12 else 15).sp,
                )
            }
        }
    }

    /**
     * The rename editor: a native EditText dialog, so the Shield
     * gets its Leanback keyboard and a phone in landscape the
     * regular soft keyboard. Prefilled with the current display
     * name, fully selected, so typing simply replaces it. The
     * input filter caps the name at [DancerNames.MAX_NAME_LENGTH]
     * characters while typing; an edit that cleans down to
     * nothing is not saved — the old name stands.
     */
    private fun showRenameDialog(dancer: Dancer) {
        val current = dancerDisplayName(dancer.key)
        val edit = EditText(this).apply {
            setText(current)
            setSelectAllOnFocus(true)
            isSingleLine = true
            hint = s(TvKey.ADMIN_NAME_HINT)
            filters = arrayOf(InputFilter.LengthFilter(DancerNames.MAX_NAME_LENGTH))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(s(TvKey.ADMIN_RENAME_TITLE, current))
            .setView(edit)
            .setPositiveButton(s(TvKey.SAVE)) { _, _ ->
                val clean = DancerNames.sanitize(edit.text?.toString())
                if (clean != null) {
                    app.prefs.setCustomDancerName(dancer.key, clean)
                    customDancerNames = app.prefs.customDancerNames()
                }
            }
            .setNegativeButton(s(TvKey.CANCEL), null)
            .show()
        edit.requestFocus()
    }

    private var listType by mutableStateOf(ListType.SONGS)
    private var filterLang by mutableStateOf<Language?>(null)
    private var filterGender by mutableStateOf<Gender?>(null)
    private var filterArtist by mutableStateOf<String?>(null)
    private var special by mutableStateOf(Special.NONE)

    private fun openBrowse(type: ListType, lang: Language?, gender: Gender?, sp: Special) {
        listType = type; filterLang = lang; filterGender = gender; filterArtist = null; special = sp
        screen = Screen.BROWSE
    }

    @Composable
    private fun BrowseScreen(compact: Boolean) {
        BackHandler { screen = Screen.HOME }
        @Suppress("UNUSED_VARIABLE") val versionTick = app.state.version
        val catalog = app.state.catalog
        // The corner QR is pinned top-right (v1.0.19): browse content
        // reserves the card's width on the right so neither the
        // filter chips nor the list rows slide under it. No reserve
        // without a LAN address (no card is drawn then).
        val qrEndReserve = if (remember(app.state.version) { Qr.lanIpAddress() } != null) {
            if (compact) 88.dp else 112.dp
        } else {
            0.dp
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(
                start = if (compact) 12.dp else 24.dp,
                top = if (compact) 12.dp else 24.dp,
                bottom = if (compact) 12.dp else 24.dp,
                end = (if (compact) 12.dp else 24.dp) + qrEndReserve,
            ),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(s(TvKey.TAB_SONGS), listType == ListType.SONGS) { listType = ListType.SONGS }
                Chip(s(TvKey.TAB_SINGERS), listType == ListType.SINGERS) { listType = ListType.SINGERS }
                Chip(s(TvKey.FILTER_ALL), filterLang == null && special == Special.NONE) {
                    filterLang = null; special = Special.NONE
                }
                Chip(s(TvKey.FILTER_CHINESE), filterLang == Language.CHINESE) { filterLang = Language.CHINESE; special = Special.NONE }
                Chip(s(TvKey.FILTER_ENGLISH), filterLang == Language.ENGLISH) { filterLang = Language.ENGLISH; special = Special.NONE }
                Chip(s(TvKey.FILTER_ANY_GENDER), filterGender == null) { filterGender = null }
                Chip(s(TvKey.FILTER_MALE), filterGender == Gender.MALE) { filterGender = Gender.MALE }
                Chip(s(TvKey.FILTER_FEMALE), filterGender == Gender.FEMALE) { filterGender = Gender.FEMALE }
                if (filterArtist != null) {
                    Chip(s(TvKey.SINGER_CHIP, filterArtist!!), true) { filterArtist = null }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (listType == ListType.SINGERS) {
                val singers = catalog.singers(filterLang, filterGender)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(singers) { singer ->
                        ListRow("🎤 $singer", compact) {
                            filterArtist = singer; listType = ListType.SONGS
                        }
                    }
                }
            } else {
                val songs = when (special) {
                    Special.FAVORITES -> catalog.songs.filter { it.id in app.prefs.favorites() }
                    Special.RECENTS -> app.prefs.recents().mapNotNull { catalog.byId(it) }
                    Special.NONE -> catalog.filtered(filterLang, filterGender)
                        .filter { filterArtist == null || it.artist.equals(filterArtist, ignoreCase = true) }
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(songs) { song ->
                        ListRow("🎵 ${song.displayName}", compact) { orderFromTv(song) }
                    }
                }
            }
        }
    }

    @Composable
    private fun PlayerScreen(compact: Boolean) {
        BackHandler { screen = Screen.HOME }
        @Suppress("UNUSED_VARIABLE") val versionTick = app.state.version
        val state = app.state
        val entry = state.nowPlaying
        LaunchedEffect(entry?.song?.id) {
            while (true) {
                positionMs = try { controller.player.currentPosition } catch (e: Exception) { 0L }
                durationMs = try { controller.player.duration } catch (e: Exception) { -1L }
                errorBanner = controller.visibleError()
                tickMs = SystemClock.elapsedRealtime()
                // ~30 fps so the karaoke sweep's gold boundary
                // travels continuously through each character.
                delay(33)
            }
        }
        // --- queue panel (v1.0.19): the remote holder manages the
        // waiting list from the player screen. Focus plumbing: rows
        // and their action chips own FocusRequesters; events ask for
        // a target here and one effect performs the move after the
        // recomposition that the queue change caused. ---
        val waiting = state.queue.waitingList
        // The panel's "current" is what is actually singing: the
        // queue's current song, or — while idle filler is playing
        // (v1.0.20) — the filler entry, which the panel labels
        // 随机/Random via the row model. Filler never appears as a
        // waiting row: it is not in the queue at all.
        val panelCurrent = state.queue.current ?: entry?.takeIf { it.isFiller }
        val panelRows = QueuePanelModel.rows(panelCurrent, waiting)
        val panelVisible = QueuePanelModel.isVisible(panelCurrent, waiting)
        val qrPresent = remember(state.version) { Qr.lanIpAddress() } != null
        var actionIndex by remember { mutableStateOf<Int?>(null) }
        var panelFocused by remember { mutableStateOf(false) }
        var focusTarget by remember { mutableStateOf<PanelFocusTarget>(PanelFocusTarget.None) }
        val rowRequesters = remember { mutableStateMapOf<Int, FocusRequester>() }
        val promoteRequester = remember { FocusRequester() }
        val deleteRequester = remember { FocusRequester() }
        val controlRequester = remember { FocusRequester() }
        // The expanded row may vanish under the panel (a phone
        // deleted that song): collapse its actions instead of
        // pointing at a song that is no longer there.
        LaunchedEffect(state.version) {
            val ai = actionIndex
            if (ai != null && ai !in waiting.indices) actionIndex = null
        }
        LaunchedEffect(focusTarget, state.version) {
            val target = focusTarget
            if (target != PanelFocusTarget.None) {
                focusTarget = PanelFocusTarget.None
                try {
                    when (target) {
                        is PanelFocusTarget.Row -> rowRequesters[target.index]?.requestFocus()
                        PanelFocusTarget.PromoteAction -> promoteRequester.requestFocus()
                        PanelFocusTarget.DeleteAction -> deleteRequester.requestFocus()
                        PanelFocusTarget.ControlBar -> controlRequester.requestFocus()
                        PanelFocusTarget.None -> Unit
                    }
                } catch (e: Exception) {
                    // Target row not composed (scrolled off / just
                    // removed): focus stays where it is.
                }
            }
        }
        // Back while working the panel: collapse the row actions,
        // or hand focus back to the control bar — never leave the
        // player screen mid-queue-management. (The plain BackHandler
        // above still owns Back everywhere else on this screen.)
        BackHandler(enabled = panelFocused || actionIndex != null) {
            val ai = actionIndex
            if (ai != null) {
                actionIndex = null
                focusTarget = PanelFocusTarget.Row(ai)
            } else {
                focusTarget = PanelFocusTarget.ControlBar
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenH = maxHeight
            val screenW = maxWidth
            Column(modifier = Modifier.fillMaxSize().padding(if (compact) 12.dp else 24.dp)) {
                val nextUp = state.queue.nextUpText(uiLang)
                Box(
                    modifier = Modifier.fillMaxWidth().height(if (compact) 26.dp else 36.dp)
                        // The banner runs along the top: keep its
                        // right end clear of the top-right QR card.
                        .padding(end = if (qrPresent) (if (compact) 90.dp else 114.dp) else 0.dp),
                ) {
                    // During the v1.0.23 ceremony the banner yields
                    // to the big centred next-song card on the stage
                    // (never the same line in two places at once).
                    if (nextUp != null && ceremonyNext == null) {
                        Text(nextUp, color = gold, fontSize = (if (compact) 14 else 19).sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                  Box(modifier = Modifier.fillMaxSize()) {
                    val clapElapsed = tickMs - applauseStartMs
                    // The v1.0.23 handover ceremony owns the stage
                    // while it runs; the plain applause celebration
                    // below is only for the no-next-song send-off.
                    val ceremony = ceremonyNext
                    val ceremonyElapsed = if (ceremony != null) tickMs - ceremonyStartMs else 0L
                    val ceremonyPhase = ceremony?.let { Ceremony.phaseAt(ceremonyElapsed) }
                    val showCelebration = ceremony == null && celebrating && entry != null &&
                        clapElapsed in 0..(APPLAUSE_MS + 200)
                    if (ceremony != null && ceremonyPhase != null &&
                        ceremonyPhase != Ceremony.Phase.DONE
                    ) {
                        CeremonyStage(ceremony, ceremonyPhase, ceremonyElapsed, compact, screenH, screenW)
                    } else if (state.interludeSeconds > 0) {
                        // --- interlude: centred text over the whole stage ---
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("👏", fontSize = (if (compact) 30 else 48).sp)
                            Text(nextUp ?: "", color = Color.White, fontSize = (if (compact) 18 else 26).sp, fontWeight = FontWeight.Bold)
                            Text(s(TvKey.INTERLUDE_COUNTDOWN, state.interludeSeconds), color = soft, fontSize = (if (compact) 14 else 18).sp)
                        }
                    } else {
                        // Dancer phases (DancerActivity): the
                        // intro invites (invite frame + slow bow),
                        // the sung part dances; once the last lyric
                        // line has been sung the dancer holds its
                        // final pose for 2 s while settling to
                        // neutral (v1.0.22), then melts into its
                        // standing frame and only breathes slowly.
                        // A song without an LRC has neither
                        // boundary and dances throughout.
                        val lastLyricEnd = remember(lyrics) { DancerActivity.lastLyricEndMs(lyrics) }
                        val firstLyricStart = remember(lyrics) { DancerActivity.firstLyricStartMs(lyrics) }
                        val dancerPhase = DancerActivity.phase(
                            positionMs, firstLyricStart, lastLyricEnd,
                            durationMs.takeIf { it > 0 },
                        )
                        // --- dancer LEFT of the lyrics, lyrics column gives
                        //     up the left zone and takes the rest ---
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (entry != null && !showCelebration && dancersOn) {
                                // The dancer belongs to whoever ordered this song.
                                val dancer = Dancers.byKey(app.prefs.dancerFor(entry.orderedBy))
                                Box(
                                    modifier = Modifier.fillMaxHeight().width(screenW * 0.36f),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    DancerImage(
                                        dancer = dancer,
                                        pose = when (dancerPhase) {
                                            DancerActivity.Phase.DANCING -> DanceMotion.pose(
                                                dancer.profile, positionMs, currentBpm,
                                                phaseOffsetBeats = Dancers.indexOf(dancer) * 0.25,
                                            )
                                            // Intro: the invite frame and
                                            // a slow "please sing" bow.
                                            DancerActivity.Phase.INVITING ->
                                                DancerActivity.invitePose(positionMs, dancer.profile)
                                            // v1.0.22 outro hold: the
                                            // last dance pose, frozen at
                                            // the lyric end and easing
                                            // back to neutral over 2 s.
                                            DancerActivity.Phase.OUTRO_HOLD ->
                                                DancerActivity.outroHoldPose(
                                                    DanceMotion.pose(
                                                        dancer.profile,
                                                        lastLyricEnd ?: positionMs,
                                                        currentBpm,
                                                        phaseOffsetBeats = Dancers.indexOf(dancer) * 0.25,
                                                    ),
                                                    positionMs,
                                                    lastLyricEnd ?: positionMs,
                                                )
                                            // Sung out: neutral stance, no
                                            // bounce/sway/fall — only a
                                            // slow breath (v1.0.7).
                                            DancerActivity.Phase.STILL ->
                                                DancerActivity.breathPose(positionMs, dancer.profile)
                                        },
                                        frameIndex = if (dancerPhase == DancerActivity.Phase.DANCING) {
                                            // v1.0.17: visit the frames in
                                            // DancerPoseOrder's re-sequenced
                                            // dance order, not sheet order.
                                            PoseCycle.frameIndex(
                                                positionMs, currentBpm,
                                                DancerPoseOrder.orderFor(dancer.key),
                                                phaseOffsetBeats = Dancers.indexOf(dancer) * 0.25,
                                            )
                                        } else if (dancerPhase == DancerActivity.Phase.OUTRO_HOLD) {
                                            // v1.0.22: freeze on the
                                            // frame that was showing
                                            // when the singing ended.
                                            PoseCycle.frameIndex(
                                                lastLyricEnd ?: positionMs, currentBpm,
                                                DancerPoseOrder.orderFor(dancer.key),
                                                phaseOffsetBeats = Dancers.indexOf(dancer) * 0.25,
                                            )
                                        } else {
                                            DancerActivity.STILL_FRAME
                                        },
                                        // v1.0.10: cross-fade each new
                                        // pose frame in (see PoseCycle)
                                        // — except the robot, whose
                                        // mechanical hard cuts are its
                                        // character and stay stepped.
                                        blend = if (dancer.profile == DanceMotion.Profile.ROBOT) {
                                            // The robot never blends —
                                            // not between dance frames,
                                            // and not into the standing
                                            // frame either (v1.0.22
                                            // settle included).
                                            null
                                        } else if (dancerPhase == DancerActivity.Phase.DANCING) {
                                            PoseCycle.blend(
                                                positionMs, currentBpm,
                                                DancerPoseOrder.orderFor(dancer.key),
                                                phaseOffsetBeats = Dancers.indexOf(dancer) * 0.25,
                                            )
                                        } else if (dancerPhase == DancerActivity.Phase.STILL) {
                                            // v1.0.22: for ~0.4 s after
                                            // the outro hold, melt the
                                            // frozen dance frame into
                                            // the standing frame — the
                                            // "hands coming down" relax.
                                            DancerActivity.outroSettleAlpha(positionMs, lastLyricEnd)?.let { a ->
                                                PoseCycle.Blend(
                                                    previousIndex = PoseCycle.frameIndex(
                                                        lastLyricEnd ?: positionMs, currentBpm,
                                                        DancerPoseOrder.orderFor(dancer.key),
                                                        phaseOffsetBeats = Dancers.indexOf(dancer) * 0.25,
                                                    ),
                                                    currentIndex = DancerActivity.STILL_FRAME,
                                                    currentAlpha = a,
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        invite = dancerPhase == DancerActivity.Phase.INVITING,
                                        // v1.0.24: the dancer stands taller
                                        // (was 0.62/0.70) — the stage
                                        // gives the space the narrower
                                        // floating queue panel freed up.
                                        heightDp = screenH * if (compact) 0.72f else 0.80f,
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                Text(
                                    // A filler song has no orderer to
                                    // credit: just its name (v1.0.20).
                                    entry?.let {
                                        if (it.isFiller) it.song.displayName
                                        else s(TvKey.NOW_SINGING_LINE, it.song.displayName, it.orderedBy)
                                    } ?: "",
                                    color = soft, fontSize = (if (compact) 12 else 15).sp,
                                )
                                LyricsView(lyrics, positionMs, compact, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    if (showCelebration) {
                        // Song finished: all six come out and clap together.
                        Row(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Dancers.ALL.forEach { dancer ->
                                DancerImage(
                                    dancer = dancer,
                                    pose = DanceMotion.clapPose(dancer.profile, clapElapsed),
                                    heightDp = screenH * if (compact) 0.36f else 0.32f,
                                )
                            }
                        }
                    }
                  }
                    // --- queue panel: a floating, semi-transparent
                    // overlay on the stage's right edge, under the QR
                    // card (v1.0.24 — until now it was a fixed 23%
                    // column stealing width from the lyrics for the
                    // whole song; as an overlay the stage keeps its
                    // full width and the panel just floats over the
                    // lyrics' right edge, narrower at 19%). Focus and
                    // actions are unchanged: explicit requesters and
                    // the control bar's D-pad Up handler never cared
                    // where in the tree the panel sits. ---
                    if (panelVisible) {
                      Box(modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight()) {
                        QueuePanel(
                            rows = panelRows,
                            compact = compact,
                            width = screenW * 0.19f,
                            topPad = if (qrPresent) (if (compact) 70.dp else 88.dp) else 0.dp,
                            actionIndex = actionIndex,
                            rowRequesters = rowRequesters,
                            promoteRequester = promoteRequester,
                            deleteRequester = deleteRequester,
                            onRowClick = { idx ->
                                if (actionIndex == idx) {
                                    actionIndex = null
                                    focusTarget = PanelFocusTarget.Row(idx)
                                } else {
                                    actionIndex = idx
                                    focusTarget = if (QueuePanelModel.canPromote(idx)) {
                                        PanelFocusTarget.PromoteAction
                                    } else {
                                        PanelFocusTarget.DeleteAction
                                    }
                                }
                            },
                            onPromote = { idx ->
                                app.state.promoteWaitingAt(idx)
                                actionIndex = null
                                // The promoted song is now first in line.
                                focusTarget = PanelFocusTarget.Row(0)
                            },
                            onDelete = { idx ->
                                app.state.removeWaitingAt(idx)
                                actionIndex = null
                                val remaining = app.state.queue.waitingList
                                focusTarget = if (remaining.isEmpty()) {
                                    PanelFocusTarget.ControlBar
                                } else {
                                    PanelFocusTarget.Row(minOf(idx, remaining.lastIndex))
                                }
                            },
                            onPanelFocus = { panelFocused = true },
                        )
                      }
                    }
                }
                // --- diagnostics: always visible. A real failure's
                // error banner stays up; a self-healed one clears
                // once the plain retry is confirmed playing (v1.0.15,
                // see ErrorDisplayPolicy). ---
                errorBanner?.let { err ->
                    Text(s(TvKey.PLAY_ERROR, err), color = Color(0xFFFF7043), fontSize = (if (compact) 11 else 12).sp)
                }
                Text(
                    diagnosticsText(entry),
                    color = soft.copy(alpha = 0.8f), fontSize = (if (compact) 10 else 11).sp,
                )
                // --- control bar: permanently visible, never auto-hides ---
                Row(
                    modifier = Modifier.fillMaxWidth()
                        // D-pad Up from the control bar steps into the
                        // queue panel's first waiting row (v1.0.19);
                        // the panel's own Back returns focus here.
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionUp && waiting.isNotEmpty()
                            ) {
                                focusTarget = PanelFocusTarget.Row(0)
                                true
                            } else {
                                false
                            }
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    Chip(
                        if (controller.player.isPlaying) s(TvKey.PAUSE) else s(TvKey.RESUME), false,
                        modifier = Modifier.focusRequester(controlRequester),
                        onFocused = { panelFocused = false },
                    ) { controller.togglePlayPause() }
                    Chip(s(TvKey.REPLAY), false, onFocused = { panelFocused = false }) { controller.replay() }
                    Chip(
                        if (accompaniment) s(TvKey.ACCOMP_ON) else s(TvKey.ACCOMP_OFF), accompaniment,
                        onFocused = { panelFocused = false },
                    ) {
                        accompaniment = !accompaniment; controller.setAccompaniment(accompaniment)
                    }
                    Chip(s(TvKey.PITCH_DOWN), false, onFocused = { panelFocused = false }) { controller.shiftPitch(-1); semitoneLabel = controller.semitones }
                    Chip(s(TvKey.PITCH_LABEL, semitoneLabel), false, onFocused = { panelFocused = false }) { }
                    Chip(s(TvKey.PITCH_UP), false, onFocused = { panelFocused = false }) { controller.shiftPitch(1); semitoneLabel = controller.semitones }
                    Chip(
                        if (dancersOn) s(TvKey.DANCERS_ON) else s(TvKey.DANCERS_OFF), dancersOn,
                        onFocused = { panelFocused = false },
                    ) {
                        dancersOn = !dancersOn; app.prefs.dancersEnabled = dancersOn
                    }
                    Chip(
                        if (fillerOn) s(TvKey.FILLER_ON) else s(TvKey.FILLER_OFF), fillerOn,
                        onFocused = { panelFocused = false },
                    ) { toggleFiller() }
                    Chip(s(TvKey.QUIT), false, onFocused = { panelFocused = false }) { confirmQuit = true }
                    Chip(s(TvKey.GO_ORDER), false, onFocused = { panelFocused = false }) { screen = Screen.BROWSE }
                }
            }
        }
    }

    /** Small print under the lyrics: player state for screenshot debugging. */
    private fun diagnosticsText(entry: QueueEntry?): String {
        val p = controller.player
        val stateName = when (p.playbackState) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "?"
        }
        return buildString {
            append("状态 ").append(stateName)
            append(" · ").append(positionMs / 1000).append('/').append(p.duration.coerceAtLeast(0L) / 1000).append('s')
            append(" · playing=").append(p.isPlaying)
            append(" · sink=").append(controller.sinkLabel)
            append(" · bpm=").append(currentBpm.toInt())
            if (entry != null) {
                val dancer = Dancers.byKey(app.prefs.dancerFor(entry.orderedBy))
                append(" · 舞者：").append(dancerDisplayName(dancer.key)).append("（")
                    .append(if (entry.isFiller) "随机" else entry.orderedBy).append("选的）")
                val lastEnd = DancerActivity.lastLyricEndMs(lyrics)
                val firstStart = DancerActivity.firstLyricStartMs(lyrics)
                when (DancerActivity.phase(positionMs, firstStart, lastEnd)) {
                    DancerActivity.Phase.INVITING -> append(" · 伴舞：前奏邀请")
                    DancerActivity.Phase.OUTRO_HOLD -> append(" · 伴舞：尾奏定格")
                    DancerActivity.Phase.STILL -> append(" · 伴舞：尾奏静止")
                    DancerActivity.Phase.DANCING -> {}
                }
            }
            if (controller.accompanimentUnavailable) append(" · 伴奏不可用（已降级普通播放）")
        }
    }

    /**
     * The always-on corner QR card: a small white rounded card with
     * the phone-ordering QR and a "扫码点歌" caption, pinned to the
     * top-right corner of every screen (v1.0.19; it sat bottom-right
     * until then, and Home used to draw a bigger copy in its header —
     * the header now embeds this same card in-flow). Deliberately
     * has no clickable/focusable modifier — D-pad focus must never
     * land on it. The QR itself stays pure black on white so it
     * scans; only the thin border is translucent dark, to blend
     * with the stage.
     */
    @Composable
    private fun CornerQrCard(ip: String, port: Int, compact: Boolean, modifier: Modifier = Modifier) {
        val qrDp = if (compact) 72.dp else 92.dp
        val bitmap = remember(ip, port) { Qr.encode("http://$ip:$port/", 220) }
        Column(
            modifier = modifier
                .border(1.dp, Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .background(Color.White, RoundedCornerShape(10.dp))
                .padding(if (compact) 5.dp else 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = s(TvKey.CORNER_QR_DESC),
                modifier = Modifier.width(qrDp).height(qrDp),
            )
            Text(s(TvKey.CORNER_QR_CAPTION), color = Color(0xFF26325E), fontSize = (if (compact) 9 else 11).sp)
        }
    }

    /**
     * The queue panel (v1.0.19): right side of the player screen,
     * below the top-right QR card ([topPad] clears it). Lists the
     * shared queue exactly as [QueuePanelModel] builds it — current
     * song with ▶ and no number, waiting songs numbered — and gives
     * the remote holder the two queue actions. It renders only the
     * model; every mutation goes through KaraokeState, so the phone
     * pages see the same queue change from the same state bump.
     */
    @Composable
    private fun QueuePanel(
        rows: List<QueuePanelModel.Row>,
        compact: Boolean,
        width: Dp,
        topPad: Dp,
        actionIndex: Int?,
        rowRequesters: MutableMap<Int, FocusRequester>,
        promoteRequester: FocusRequester,
        deleteRequester: FocusRequester,
        onRowClick: (Int) -> Unit,
        onPromote: (Int) -> Unit,
        onDelete: (Int) -> Unit,
        onPanelFocus: () -> Unit,
    ) {
        Column(
            modifier = Modifier.width(width).fillMaxHeight()
                .padding(start = if (compact) 8.dp else 12.dp, top = topPad)
                .background(cardBg.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                .padding(if (compact) 8.dp else 10.dp),
        ) {
            Text(
                s(TvKey.QUEUE_PANEL_TITLE), color = gold,
                fontSize = (if (compact) 13 else 16).sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(rows, key = { row -> if (row.isCurrent) "current" else "w${row.waitingIndex}" }) { row ->
                    if (row.isCurrent) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        ) {
                            Text(
                                "▶ ${row.entry.song.displayName}", color = gold,
                                fontSize = (if (compact) 12 else 14).sp, maxLines = 2,
                            )
                            Text(
                                // Filler current row: labelled as a
                                // random pick, never a person's name.
                                if (row.isFiller) s(TvKey.QUEUE_PANEL_RANDOM) else row.entry.orderedBy,
                                color = soft,
                                fontSize = (if (compact) 10 else 11).sp, maxLines = 1,
                            )
                        }
                    } else {
                        QueuePanelRowItem(
                            row = row,
                            compact = compact,
                            expanded = actionIndex == row.waitingIndex,
                            focusRequester = rowRequesters.getOrPut(row.waitingIndex) { FocusRequester() },
                            promoteRequester = promoteRequester,
                            deleteRequester = deleteRequester,
                            onClick = { onRowClick(row.waitingIndex) },
                            onPromote = { onPromote(row.waitingIndex) },
                            onDelete = { onDelete(row.waitingIndex) },
                            onPanelFocus = onPanelFocus,
                        )
                    }
                }
            }
            if (rows.none { !it.isCurrent }) {
                Text(
                    s(TvKey.QUEUE_PANEL_NO_WAITING), color = soft,
                    fontSize = (if (compact) 11 else 12).sp,
                )
            }
        }
    }

    /** One waiting-song row of the queue panel: number, song, who
     *  ordered it — and, once OK expands it, the two action chips.
     *  Nothing is executed from the row itself: an action runs only
     *  when its own chip takes the second OK. */
    @Composable
    private fun QueuePanelRowItem(
        row: QueuePanelModel.Row,
        compact: Boolean,
        expanded: Boolean,
        focusRequester: FocusRequester,
        promoteRequester: FocusRequester,
        deleteRequester: FocusRequester,
        onClick: () -> Unit,
        onPromote: () -> Unit,
        onDelete: () -> Unit,
        onPanelFocus: () -> Unit,
    ) {
        var focused by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier.fillMaxWidth()
                .border(2.dp, if (focused) gold else Color.Transparent, RoundedCornerShape(8.dp))
                .background(if (focused) cardBg else Color.Transparent, RoundedCornerShape(8.dp))
                .focusRequester(focusRequester)
                .focusable()
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onPanelFocus()
                }
                .clickable { onClick() }
                .padding(horizontal = 8.dp, vertical = if (compact) 4.dp else 6.dp),
        ) {
            Text(
                "${row.number}. ${row.entry.song.displayName}", color = Color.White,
                fontSize = (if (compact) 12 else 14).sp, maxLines = 2,
            )
            Text(
                row.entry.orderedBy, color = soft,
                fontSize = (if (compact) 10 else 11).sp, maxLines = 1,
            )
            if (expanded) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    if (QueuePanelModel.canPromote(row.waitingIndex)) {
                        PanelActionChip(
                            s(TvKey.QUEUE_PANEL_NEXT), compact, promoteRequester, onPanelFocus, onPromote,
                        )
                    }
                    PanelActionChip(
                        s(TvKey.QUEUE_PANEL_DELETE), compact, deleteRequester, onPanelFocus, onDelete,
                    )
                }
            }
        }
    }

    /** A gold action chip inside an expanded queue-panel row. */
    @Composable
    private fun PanelActionChip(
        label: String,
        compact: Boolean,
        requester: FocusRequester,
        onPanelFocus: () -> Unit,
        onClick: () -> Unit,
    ) {
        var focused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                .background(gold, RoundedCornerShape(12.dp))
                .focusRequester(requester)
                .focusable()
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onPanelFocus()
                }
                .clickable { onClick() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                label, color = Color.Black,
                fontSize = (if (compact) 11 else 12).sp, fontWeight = FontWeight.Bold,
            )
        }
    }

    /**
     * The v1.0.23 handover ceremony stage (see [Ceremony] for the
     * timeline): a big centred card announcing the locked next
     * song, the six dancers clapping / inviting in a row at the
     * bottom, and — from the exit segment on — the keeper alone at
     * the regular dancer position, cross-fading in while the row
     * melts away, then inviting once more on its own. Purely a
     * function of the ceremony phase + elapsed time; the driver
     * coroutine in [onSongEndedNaturally] owns the lifecycle.
     */
    @Composable
    private fun CeremonyStage(
        next: QueueEntry,
        phase: Ceremony.Phase,
        elapsedMs: Long,
        compact: Boolean,
        screenH: Dp,
        screenW: Dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // --- big next-song card, centred on the stage ---
            val remainingSec =
                ((Ceremony.TOTAL_MS - elapsedMs).coerceAtLeast(0L) + 999L) / 1000L
            Column(
                modifier = Modifier.align(Alignment.Center)
                    .background(cardBg, RoundedCornerShape(16.dp))
                    .padding(horizontal = 30.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (phase == Ceremony.Phase.CLAPPING) {
                    Text("👏", fontSize = (if (compact) 28 else 42).sp)
                }
                Text(
                    // The locked next song itself, in the banner's
                    // exact bilingual wording — just much bigger.
                    TvStrings.get(uiLang, TvKey.NEXT_UP, next.song.displayName, next.orderedBy),
                    color = gold,
                    fontSize = (if (compact) 20 else 32).sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    s(TvKey.INTERLUDE_COUNTDOWN, remainingSec.toInt()),
                    color = soft, fontSize = (if (compact) 13 else 17).sp,
                )
            }
            // --- the row of six: clapping, then inviting together,
            //     then melting away during the exit ---
            if (phase != Ceremony.Phase.SOLO_INVITE) {
                val rowAlpha = 0.95f * Ceremony.leavingAlpha(elapsedMs)
                val rowHeight = screenH * if (compact) 0.36f else 0.32f
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Dancers.ALL.forEach { dancer ->
                        when (phase) {
                            Ceremony.Phase.CLAPPING -> DancerImage(
                                dancer = dancer,
                                pose = DanceMotion.clapPose(dancer.profile, elapsedMs),
                                heightDp = rowHeight,
                                alpha = rowAlpha,
                            )
                            Ceremony.Phase.GROUP_INVITE -> DancerImage(
                                dancer = dancer,
                                pose = Ceremony.invitePose(
                                    dancer.profile,
                                    elapsedMs - Ceremony.CLAP_MS,
                                    Ceremony.GROUP_INVITE_MS,
                                ),
                                heightDp = rowHeight,
                                invite = true,
                                alpha = rowAlpha,
                            )
                            else -> DancerImage(
                                // EXITING: the invite gesture is
                                // complete (lean back at neutral);
                                // the row just melts away.
                                dancer = dancer,
                                pose = Ceremony.invitePose(
                                    dancer.profile,
                                    Ceremony.GROUP_INVITE_MS,
                                    Ceremony.GROUP_INVITE_MS,
                                ),
                                heightDp = rowHeight,
                                invite = true,
                                alpha = rowAlpha,
                            )
                        }
                    }
                }
            }
            // --- the keeper alone at the regular dancer position:
            //     cross-fades in during the exit, then invites once
            //     more — already standing exactly where normal
            //     playback rendering will put it when the song
            //     starts, so the handover has no jump. ---
            if (phase == Ceremony.Phase.EXITING || phase == Ceremony.Phase.SOLO_INVITE) {
                val keeper = Dancers.ALL[Ceremony.keeperIndex(ceremonyKeeperKey)]
                Box(
                    modifier = Modifier.fillMaxHeight().width(screenW * 0.32f),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    DancerImage(
                        dancer = keeper,
                        pose = if (phase == Ceremony.Phase.SOLO_INVITE) {
                            Ceremony.invitePose(
                                keeper.profile,
                                elapsedMs - Ceremony.CLAP_MS -
                                    Ceremony.GROUP_INVITE_MS - Ceremony.EXIT_MS,
                                Ceremony.SOLO_INVITE_MS,
                            )
                        } else {
                            Ceremony.invitePose(keeper.profile, 0L, Ceremony.SOLO_INVITE_MS)
                        },
                        heightDp = screenH * if (compact) 0.62f else 0.70f,
                        invite = true,
                        alpha = 0.95f * Ceremony.keeperSoloAlpha(elapsedMs),
                    )
                }
            }
        }
    }

    @Composable
    private fun DancerImage(
        dancer: Dancer,
        pose: DanceMotion.Pose,
        heightDp: Dp,
        modifier: Modifier = Modifier,
        frameIndex: Int = -1,
        invite: Boolean = false,
        blend: PoseCycle.Blend? = null,
        /** Overall opacity the figure is drawn at; the default is
         *  the long-standing stage alpha. The v1.0.23 ceremony
         *  drives it for its fade-out / fade-in handover. */
        alpha: Float = 0.95f,
    ) {
        // v1.0.10: while a beat's cross-fade is still running, the
        // previous pose frame is drawn under the current one with
        // complementary alphas — same rect, same whole-body
        // transform, only the alpha differs — so the frame change
        // melts instead of cutting. Once the fade completes (or no
        // blend was supplied: robot, invite/still, applause row)
        // this is exactly the old single-image draw.
        if (blend != null && !invite &&
            blend.currentAlpha < 1f &&
            blend.previousIndex != blend.currentIndex &&
            blend.previousIndex in dancer.frames.indices &&
            blend.currentIndex in dancer.frames.indices
        ) {
            Box(modifier = modifier) {
                DancerFrame(
                    resId = dancer.frames[blend.previousIndex],
                    label = dancerDisplayName(dancer.key),
                    pose = pose,
                    heightDp = heightDp,
                    alpha = alpha * (1f - blend.currentAlpha),
                )
                DancerFrame(
                    resId = dancer.frames[blend.currentIndex],
                    label = dancerDisplayName(dancer.key),
                    pose = pose,
                    heightDp = heightDp,
                    alpha = alpha * blend.currentAlpha,
                )
            }
        } else {
            DancerFrame(
                resId = when {
                    invite -> dancer.inviteResId
                    frameIndex in dancer.frames.indices -> dancer.frames[frameIndex]
                    else -> dancer.resId
                },
                label = dancerDisplayName(dancer.key),
                pose = pose,
                heightDp = heightDp,
                alpha = alpha,
                modifier = modifier,
            )
        }
    }

    /** One dancer frame with the shared whole-body [pose] transform. */
    @Composable
    private fun DancerFrame(
        @DrawableRes resId: Int,
        label: String,
        pose: DanceMotion.Pose,
        heightDp: Dp,
        alpha: Float,
        modifier: Modifier = Modifier,
    ) {
        val heightPx = with(LocalDensity.current) { heightDp.toPx() }
        Image(
            painter = painterResource(resId),
            contentDescription = label,
            modifier = modifier
                .height(heightDp)
                .graphicsLayer {
                    translationY = -heightPx * 0.055f * pose.bounceFraction +
                        heightPx * 0.30f * pose.sinkFraction
                    rotationZ = pose.rotationDeg
                    scaleX = pose.scale
                    scaleY = pose.scale
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(0.5f, 0.95f)
                },
        )
    }

    @Composable
    private fun EntryCard(label: String, compact: Boolean, onClick: () -> Unit) {
        var focused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .height(if (compact) 54.dp else 84.dp)
                .border(2.dp, if (focused) gold else Color.Transparent, RoundedCornerShape(12.dp))
                .background(cardBg, RoundedCornerShape(12.dp))
                .focusable()
                .onFocusChanged { focused = it.isFocused }
                .clickable { onClick() }
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = Color.White, fontSize = (if (compact) 15 else 20).sp, fontWeight = FontWeight.Bold)
        }
    }

    @Composable
    private fun Chip(
        label: String,
        selected: Boolean,
        modifier: Modifier = Modifier,
        onFocused: () -> Unit = {},
        onClick: () -> Unit,
    ) {
        var focused by remember { mutableStateOf(false) }
        Box(
            modifier = modifier
                .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(16.dp))
                .background(if (selected) gold else cardBg, RoundedCornerShape(16.dp))
                .focusable()
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
                .clickable { onClick() }
                // v1.0.24: chips sit a touch tighter (vertical
                // 6dp -> 4dp) so the always-on control bar takes
                // less of the stage's height; labels stay 13sp.
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(label, color = if (selected) Color.Black else Color.White, fontSize = 13.sp)
        }
    }

    @Composable
    private fun ListRow(label: String, compact: Boolean, onClick: () -> Unit) {
        var focused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, if (focused) gold else Color.Transparent, RoundedCornerShape(8.dp))
                .background(if (focused) cardBg else Color.Transparent, RoundedCornerShape(8.dp))
                .focusable()
                .onFocusChanged { focused = it.isFocused }
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = if (compact) 6.dp else 10.dp),
        ) {
            Text(label, color = Color.White, fontSize = (if (compact) 14 else 18).sp, maxLines = 1)
        }
    }
}
