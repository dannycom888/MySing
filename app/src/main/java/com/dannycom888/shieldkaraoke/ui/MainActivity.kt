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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import com.dannycom888.shieldkaraoke.KaraokeApp
import com.dannycom888.shieldkaraoke.R
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
import com.dannycom888.shieldkaraoke.queue.AccompanimentPolicy
import com.dannycom888.shieldkaraoke.queue.EntrySource
import com.dannycom888.shieldkaraoke.queue.FillerPicker
import com.dannycom888.shieldkaraoke.queue.FillerPolicy
import com.dannycom888.shieldkaraoke.queue.FillerSession
import com.dannycom888.shieldkaraoke.queue.QueueEntry
import com.dannycom888.shieldkaraoke.state.DancerNames
import com.dannycom888.shieldkaraoke.state.SweepColor
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Screen { BROWSE, PLAYER, ADMIN }
private enum class ListType { SONGS, SINGERS }
private enum class Special { NONE, FAVORITES, RECENTS }

/** Where the player queue panel's focus plumbing moves D-pad focus
 *  next (v1.0.19); set by panel / control-bar events, consumed once
 *  by a LaunchedEffect in PlayerScreen. */
private sealed interface PanelFocusTarget {
    object None : PanelFocusTarget
    data class Row(val index: Int) : PanelFocusTarget
    object PlayNowAction : PanelFocusTarget
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

    /** The stage is the home now (v1.0.31: the old home screen is
     *  retired) — the app opens on the player screen, or straight
     *  into the admin page when no library folder was ever chosen
     *  (decided in onCreate, once prefs are readable). */
    private var screen by mutableStateOf(Screen.PLAYER)
    /** Where Back from the admin page returns to. The admin page
     *  has two gear doors — the player's (v1.0.29) and the browse
     *  page's (v1.0.31) — and each door leads back where it came
     *  from, so checking a setting mid-song lands you back on the
     *  stage, not wherever else you happened to come from. */
    private var adminReturnTo by mutableStateOf(Screen.PLAYER)
    private var lyrics by mutableStateOf<List<LyricLine>>(emptyList())
    private var positionMs by mutableStateOf(0L)
    private var durationMs by mutableStateOf(-1L)
    /** Red error banner text, refreshed by the player poll loop
     *  from [PlayerController.visibleError] (v1.0.15). */
    private var errorBanner by mutableStateOf<String?>(null)
    private var tickMs by mutableStateOf(0L)
    private var dancersOn by mutableStateOf(true)
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
    /** Karaoke sweep colour (v1.0.26), chosen on the admin page
     *  and persisted in prefs. Held as activity state so a change
     *  in the admin page recomposes the player at once — the new
     *  colour is on the lyrics from the very next frame, mid-song
     *  included. */
    private var sweepColor by mutableStateOf(SweepColor.DEFAULT)
    private var accompaniment by mutableStateOf(false)
    private var semitoneLabel by mutableStateOf(0)
    private var scanning by mutableStateOf(false)
    private var quitting = false

    /** TV interface language (v1.0.13), switched on the player
     *  banner's language chips (v1.0.31 — they lived on the home
     *  screen until it retired) and persisted in prefs. Every
     *  user-facing string on
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
        sweepColor = app.prefs.sweepColor
        customDancerNames = app.prefs.customDancerNames()
        uiLang = UiLang.parse(app.prefs.uiLangCode)
        controller = PlayerController(this)
        controller.onEnded = { onSongEndedNaturally() }
        app.state.onShouldStart = { entry ->
            runOnUiThread { startSong(entry); screen = Screen.PLAYER }
        }
        // First run (v1.0.31): with no library folder ever chosen
        // there is nothing to sing and nothing to fill with — land
        // on the admin page so the host picks the folder first.
        // Back from there returns to the (idle) stage.
        if (app.prefs.libraryTreeUri == null) screen = Screen.ADMIN
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
                    // Ambient bubbles behind every screen (v1.0.25):
                    // the first child of the root, so all content —
                    // screens, corner QR, dialogs — draws over the
                    // drifting field on the shared navy base.
                    BubbleBackground(compact, Modifier.fillMaxSize())
                    when (screen) {
                        Screen.BROWSE -> BrowseScreen(compact)
                        Screen.PLAYER -> PlayerScreen(compact)
                        Screen.ADMIN -> AdminScreen(compact)
                    }
                    // Corner QR: pinned to the TOP-RIGHT corner at
                    // all times so a phone can join in mid-song
                    // (v1.0.19 — it lived bottom-right before; every
                    // screen uses this one card in this one corner).
                    // v1.0.42: the address comes from a slow poll
                    // (rememberLanIpAddress), not from the queue
                    // state's version — during long random playback
                    // the version never bumped, so a card lost to a
                    // late/dropped WiFi never came back. With no
                    // address the corner shows a same-size waiting
                    // placeholder instead of silently vanishing,
                    // and the real QR returns on its own once the
                    // network does.
                    val cornerIp = rememberLanIpAddress()
                    val cornerModifier = Modifier.align(Alignment.TopEnd)
                        .padding(
                            end = if (compact) 12.dp else 24.dp,
                            top = if (compact) 12.dp else 24.dp,
                        )
                    if (cornerIp != null) {
                        CornerQrCard(
                            ip = cornerIp,
                            port = app.localServerPort(),
                            compact = compact,
                            modifier = cornerModifier,
                        )
                    } else {
                        CornerQrPlaceholderCard(
                            compact = compact,
                            modifier = cornerModifier,
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

    /**
     * A folder picked in the SAF tree picker (v1.0.26: the picker
     * is launched from the admin page — the handling is exactly
     * what the home screen's button always did): keep read
     * permission across restarts where the provider allows it,
     * remember the tree, load it.
     */
    private fun onLibraryFolderPicked(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (e: Exception) { /* still usable this session */ }
        app.prefs.libraryTreeUri = uri.toString()
        loadLibrary(uri)
    }

    /** The installed build's real versionName, read from the
     *  package manager (v1.0.26 admin page footer). Deliberately
     *  not a constant anywhere: it follows the build by itself. */
    private fun installedVersionName(): String? = try {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionName
    } catch (e: Exception) {
        null
    }

    /** Display label for the current library folder on the admin
     *  page: the folder's own name where SAF reports one, the
     *  decoded tree path otherwise. Null = never chosen. */
    private fun libraryFolderLabel(): String? {
        val raw = app.prefs.libraryTreeUri ?: return null
        val uri = Uri.parse(raw)
        val name = try {
            DocumentFile.fromTreeUri(this, uri)?.name
        } catch (e: Exception) {
            null
        }
        return name ?: Uri.decode(uri.lastPathSegment ?: raw)
    }

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
        // v1.0.44: the accompaniment chip starts from the
        // per-song default — ordered songs on accompaniment,
        // filler on the original vocal (AccompanimentPolicy);
        // a manual toggle from here rules for this song only.
        accompaniment = AccompanimentPolicy.defaultOnStart(entry.source)
        semitoneLabel = 0
        celebrating = false
        celebrationToken++
        clearCeremony()
        val uri = Uri.parse(entry.song.audioRef)
        val cachedGain = app.prefs.loudnessDb(entry.song.id)
        controller.load(uri, AudioMath.normalizationGainDb(cachedGain), accompaniment)
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
     * library becomes ready and when the real queue drains
     * (naturally or by quit). There is no switch (v1.0.31): nobody
     * orders, filler simply starts.
     *
     * Screen rule (v1.0.24): the first filler song of a session —
     * the [FillerSession] entry edge — brings the player screen
     * forward from wherever the TV is (Browse, Admin), so a
     * filler session can never sing invisibly behind another
     * screen. Rollovers inside the session never touch the screen:
     * a host who backed out to Browse on purpose stays there, and
     * opens the player screen to find the filler song's full
     * picture — lyrics, dancer, title — because [startSong]
     * loaded it exactly like an ordered song. (Before v1.0.24 the
     * entry edge fired only from the old home screen and rollovers
     * never fired at all, which is how filler ended up singing
     * with no picture and no lyrics.)
     */
    private fun maybeStartFiller(): Boolean {
        val canStart = FillerPolicy.shouldAutoStart(
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
     *  countdown — straight to the next random pick. When no next
     *  pick can start (the library went away), the session is over
     *  and the stage simply idles where it is. */
    private fun startNextFillerOrIdle() {
        app.state.nowPlaying = null
        if (!maybeStartFiller()) {
            fillerSession.onStopped()
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
                startNextFillerOrIdle()
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
                // applause has played out, so the celebration is
                // never swallowed early.
                lifecycleScope.launch {
                    delay(APPLAUSE_MS + 300)
                    if (celebrationToken == token && celebrating) {
                        celebrating = false
                        app.state.nowPlaying = null
                        // The applause belonged to that last ordered
                        // song; with the real queue drained, the idle
                        // filler takes over from here (v1.0.20
                        // trigger b). If no filler can start, the
                        // stage simply idles on the player screen —
                        // it IS the home screen now (v1.0.31).
                        maybeStartFiller()
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
                // bump: quit, a jump-cut…) simply ends it.
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
                                    // Idle: filler takes over if it
                                    // can; otherwise the stage just
                                    // waits (v1.0.31 — no home
                                    // screen to fall back to).
                                    maybeStartFiller()
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
            startNextFillerOrIdle()
            return
        }
        val next = app.state.advanceAfterQuit()
        if (next == null) {
            app.state.nowPlaying = null
            // The stage is idle: the filler takes over (v1.0.24 —
            // quitting the last ordered song used to strand the TV
            // on a silent screen, unlike the natural-end drain).
            // If no filler can start (library empty), the stage
            // simply idles here (v1.0.31).
            maybeStartFiller()
        } else {
            startSong(next)
        }
    }

    /** The queue panel's "现在唱" (v1.0.27): the remote holder cuts
     *  straight to a waiting song. The state change happens first —
     *  if the row has already vanished (a phone deleted that song),
     *  nothing is touched and the current song plays on. Otherwise
     *  the cut song gets exactly the 不唱了 treatment — stopped
     *  dead, no applause, no handover ceremony — and the chosen
     *  song starts at once, with its own orderer's dancer (the
     *  plain [startSong] path resolves it). A filler song on the
     *  stage is cut the same way: it never entered the queue, so
     *  there is nothing of it to clean up, and [startSong] ends
     *  its filler session like any ordered start. */
    private fun jumpToWaiting(index: Int) {
        val entry = app.state.jumpWaitingAt(index) ?: return
        quitting = true
        controller.stop()
        quitting = false
        celebrating = false
        celebrationToken++
        clearCeremony()
        app.state.interludeSeconds = 0
        startSong(entry)
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

    // ---------- admin: settings behind the gear ----------

    /**
     * The TV admin page — everything settable lives here since
     * v1.0.26 (the user's layout: 选文件夹、改名、改颜色进后台，
     * 改中英在前台）: the library folder and rescan, the sweep
     * colour, and the dancer renames (v1.0.21). Renaming is
     * TV-only: the phone server reads the names for its picker
     * cards but has no write path. Rows carry the dancer's
     * standing frame so the host recognises who is who even after
     * a rename; a renamed row also shows the default it would
     * fall back to.
     *
     * Two doors lead here (the player screen's gear since
     * v1.0.29, and the browse page's gear since v1.0.31); Back
     * returns through whichever door was used ([adminReturnTo]).
     * Coming here never touches playback — the song on stage
     * keeps singing behind the settings. The page footer carries
     * the version number and, under it, the credit line that used
     * to sit at the retired home screen's bottom (v1.0.31); since
     * v1.0.35 the diagnostics line lives just above the version —
     * it moved off the stage, where it only ever served debugging.
     */
    @Composable
    private fun AdminScreen(compact: Boolean) {
        BackHandler { screen = adminReturnTo }
        val state = app.state
        // v1.0.35: the diagnostics section at this page's foot
        // must stay live, but the stage's 33 ms ticker stops with
        // the player screen's composition — so while this page is
        // up, a slower local ticker keeps the same positionMs /
        // durationMs state fresh from the (still singing) player.
        var diagTick by remember { mutableStateOf(0L) }
        LaunchedEffect(Unit) {
            while (true) {
                positionMs = try { controller.player.currentPosition } catch (e: Exception) { 0L }
                durationMs = try { controller.player.duration } catch (e: Exception) { -1L }
                diagTick = SystemClock.elapsedRealtime()
                delay(250)
            }
        }
        // The corner card overlay is pinned top-right here too;
        // reserve its width so no row slides under it (same
        // pattern as BrowseScreen). Since v1.0.42 the corner is
        // always occupied — the QR, or its waiting-for-network
        // placeholder when there is no address — so the reserve
        // is unconditional (it also keeps rows from jumping
        // sideways when the network comes and goes).
        val qrEndReserve = if (compact) 88.dp else 112.dp
        // The library folder picker lives here now (v1.0.26);
        // the picked tree goes through the same handling the
        // home button always used.
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) onLibraryFolderPicked(uri)
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(
                start = if (compact) 14.dp else 28.dp,
                top = if (compact) 14.dp else 28.dp,
                bottom = if (compact) 14.dp else 28.dp,
                end = (if (compact) 14.dp else 28.dp) + qrEndReserve,
            ).verticalScroll(rememberScrollState()),
        ) {
            Text(s(TvKey.ADMIN_TITLE), color = gold, fontSize = (if (compact) 22 else 32).sp, fontWeight = FontWeight.Bold)
            // --- library (v1.0.26: moved in from the home screen,
            // together with the scan status and the server-down
            // warning that used to sit under the home title) ---
            Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
            AdminSectionTitle(s(TvKey.ADMIN_LIBRARY_TITLE), compact)
            val folderLabel = libraryFolderLabel()
            Text(
                if (folderLabel != null) s(TvKey.ADMIN_LIBRARY_CURRENT, folderLabel)
                else s(TvKey.ADMIN_LIBRARY_NONE),
                color = soft, fontSize = (if (compact) 13 else 16).sp,
            )
            Text(
                when {
                    scanning -> s(TvKey.SCANNING)
                    state.catalog.songs.isEmpty() -> s(TvKey.PICK_FOLDER)
                    else -> s(TvKey.LIBRARY_COUNT, state.catalog.songs.size)
                },
                color = soft, fontSize = (if (compact) 13 else 16).sp,
            )
            if (!app.serverAlive) {
                Text(
                    s(TvKey.SERVER_DOWN) +
                        (app.serverError?.let { "（$it）" } ?: ""),
                    color = Color(0xFFFF7043), fontSize = (if (compact) 12 else 15).sp,
                )
            }
            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EntryCard(s(TvKey.PICK_FOLDER_BTN), compact) { picker.launch(null) }
                EntryCard(s(TvKey.RESCAN), compact) {
                    app.prefs.libraryTreeUri?.let { rescan(Uri.parse(it)) }
                }
            }
            // --- sweep colour (v1.0.26, new): gold by default,
            // light green / light pink the softer alternatives;
            // the pick is persisted and recolours the player from
            // the next frame on, even mid-song ---
            Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
            AdminSectionTitle(s(TvKey.ADMIN_SWEEP_TITLE), compact)
            Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(s(TvKey.SWEEP_GOLD), sweepColor == SweepColor.GOLD) {
                    sweepColor = SweepColor.GOLD
                    app.prefs.sweepColor = SweepColor.GOLD
                }
                Chip(s(TvKey.SWEEP_GREEN), sweepColor == SweepColor.LIGHT_GREEN) {
                    sweepColor = SweepColor.LIGHT_GREEN
                    app.prefs.sweepColor = SweepColor.LIGHT_GREEN
                }
                Chip(s(TvKey.SWEEP_PINK), sweepColor == SweepColor.LIGHT_PINK) {
                    sweepColor = SweepColor.LIGHT_PINK
                    app.prefs.sweepColor = SweepColor.LIGHT_PINK
                }
            }
            // --- rename the dancers (v1.0.21, unchanged) ---
            Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
            Text(s(TvKey.ADMIN_HINT), color = soft, fontSize = (if (compact) 13 else 16).sp)
            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Dancers.ALL.forEach { dancer ->
                    AdminDancerRow(dancer, compact) { showRenameDialog(dancer) }
                }
            }
            Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EntryCard(s(TvKey.ADMIN_RESTORE), compact) { confirmRestoreNames = true }
                EntryCard(s(TvKey.ADMIN_BACK), compact) { screen = adminReturnTo }
            }
            // --- diagnostics (v1.0.35): the small-print line that
            // sat above the stage's control bar until v1.0.34 lives
            // here now, just above the version footer — same
            // assembly (diagnosticsText), kept live by this page's
            // own ticker. Reading diagTick subscribes this section
            // to the ticker, so the line refreshes even while the
            // position stands still (paused / ended). ---
            Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
            AdminSectionTitle(s(TvKey.ADMIN_DIAG_TITLE), compact)
            Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
            if (diagTick >= 0L) {
                Text(
                    diagnosticsText(state.nowPlaying),
                    color = soft.copy(alpha = 0.8f),
                    fontSize = (if (compact) 11 else 12).sp,
                )
            }
            // Version footer (v1.0.26): the host kept forgetting
            // which build was installed, so the admin page states
            // it — the real versionName off the installed package
            // (see installedVersionName), never a typed-in string
            // someone would have to remember to bump.
            installedVersionName()?.let { version ->
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                Text(
                    s(TvKey.ADMIN_VERSION, version),
                    color = soft.copy(alpha = 0.6f),
                    fontSize = (if (compact) 11 else 13).sp,
                )
            }
            // Credit line (v1.0.31): it lived at the bottom of the
            // home screen until that screen retired; now it closes
            // the admin page, under the version number.
            Spacer(Modifier.height(4.dp))
            Text(s(TvKey.CREDIT), color = soft.copy(alpha = 0.6f), fontSize = 11.sp)
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
        BackHandler { screen = Screen.PLAYER }
        @Suppress("UNUSED_VARIABLE") val versionTick = app.state.version
        val catalog = app.state.catalog
        // The corner card is pinned top-right (v1.0.19): browse content
        // reserves the card's width on the right so neither the
        // filter chips nor the list rows slide under it. Since
        // v1.0.42 the corner is always occupied — the QR, or its
        // waiting-for-network placeholder — so the reserve is
        // unconditional.
        val qrEndReserve = if (compact) 88.dp else 112.dp
        Column(
            modifier = Modifier.fillMaxSize().padding(
                start = if (compact) 12.dp else 24.dp,
                top = if (compact) 12.dp else 24.dp,
                bottom = if (compact) 12.dp else 24.dp,
                end = (if (compact) 12.dp else 24.dp) + qrEndReserve,
            ),
        ) {
            // v1.0.31: the retired home screen's furniture moved
            // here — its library status line (small print at the
            // top, with the server-down warning under the old
            // title's wording), a gear door to the admin page, and
            // the six big entries. The entries are shortcuts into
            // THIS page's own state: they set the same listType /
            // filterLang / special the chips below reflect, so
            // there is one navigation system, not two — tapping
            // 「中文」 above lights the 中文 chip below.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when {
                            scanning -> s(TvKey.SCANNING)
                            catalog.songs.isEmpty() -> s(TvKey.PICK_FOLDER)
                            else -> s(TvKey.LIBRARY_COUNT, catalog.songs.size)
                        },
                        color = soft, fontSize = (if (compact) 12 else 15).sp,
                    )
                    if (!app.serverAlive) {
                        Text(
                            s(TvKey.SERVER_DOWN) +
                                (app.serverError?.let { "（$it）" } ?: ""),
                            color = Color(0xFFFF7043), fontSize = (if (compact) 11 else 13).sp,
                        )
                    }
                }
                GearButton(compact) { adminReturnTo = Screen.BROWSE; screen = Screen.ADMIN }
            }
            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
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
            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
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

    /**
     * The brand logo (v1.0.36 artwork: the user's own wordmarks —
     * brand_logo_zh in Chinese mode, brand_logo_en in English,
     * swapped by the same uiLang the chips switch), with the
     * v1.0.44 white halo: both logos must glow outward along
     * their own silhouette on the navy stage (the user's ruling),
     * not sit on a rectangular backplate. The halo is the same
     * artwork tinted solid white (alpha preserved, so it IS the
     * glyph outline), drawn underneath in a 12-direction ring of
     * small offsets plus one slightly enlarged centred copy —
     * the overlaps accumulate into a soft outward bloom. Drawn
     * with offsets and a graphicsLayer scale only, so the box
     * measures exactly as the plain logo did (banner height
     * 36/46dp, width by aspect) and the glow can never push the
     * language chips. (Modifier.blur was rejected: it is a
     * silent no-op below API 31 and the Shield runs API 30.)
     * Display only — never focusable.
     */
    @Composable
    private fun BrandLogo(compact: Boolean) {
        val painter = painterResource(
            if (uiLang == UiLang.ZH) R.drawable.brand_logo_zh else R.drawable.brand_logo_en,
        )
        val glowRadius = if (compact) 2.2f else 3.0f
        val ring = listOf(
            1f to 0f, 0.87f to 0.5f, 0.5f to 0.87f, 0f to 1f,
            -0.5f to 0.87f, -0.87f to 0.5f, -1f to 0f, -0.87f to -0.5f,
            -0.5f to -0.87f, 0f to -1f, 0.5f to -0.87f, 0.87f to -0.5f,
        )
        Box(
            modifier = Modifier.height(if (compact) 36.dp else 46.dp)
                .aspectRatio(painter.intrinsicSize.width / painter.intrinsicSize.height),
        ) {
            // Enlarged centred silhouette: the halo's soft core.
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer { scaleX = 1.07f; scaleY = 1.07f; alpha = 0.35f },
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(Color.White),
            )
            // Offset ring: pushes the bloom outward along the
            // outline in every direction.
            ring.forEach { (dx, dy) ->
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                        .offset(x = (glowRadius * dx).dp, y = (glowRadius * dy).dp)
                        .graphicsLayer { alpha = 0.16f },
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(Color.White),
                )
            }
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }

    @Composable
    private fun PlayerScreen(compact: Boolean) {
        // No BackHandler here (v1.0.31): the stage IS the home
        // screen now — Back at the stage leaves the app, the
        // platform default. (The queue panel's own two-stage Back
        // below still owns Back while the panel has focus.)
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
        // The corner card is always present since v1.0.42 (the QR,
        // or its waiting-for-network placeholder), so the two
        // qrPresent paddings below are unconditional now.
        var actionIndex by remember { mutableStateOf<Int?>(null) }
        var panelFocused by remember { mutableStateOf(false) }
        var focusTarget by remember { mutableStateOf<PanelFocusTarget>(PanelFocusTarget.None) }
        val rowRequesters = remember { mutableStateMapOf<Int, FocusRequester>() }
        val playNowRequester = remember { FocusRequester() }
        val promoteRequester = remember { FocusRequester() }
        val deleteRequester = remember { FocusRequester() }
        val controlRequester = remember { FocusRequester() }
        // The stage is the launch screen now (v1.0.31): whenever
        // it is entered, D-pad focus starts on the control bar —
        // the pause chip — never on the banner's gear or title.
        LaunchedEffect(Unit) {
            try {
                controlRequester.requestFocus()
            } catch (e: Exception) {
                // Control bar not composed yet; the first D-pad
                // press resolves focus spatially instead.
            }
        }
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
                        PanelFocusTarget.PlayNowAction -> playNowRequester.requestFocus()
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
                // Banner row (v1.0.29): the settings gear sits at
                // its far left. The host lives on this screen (the
                // filler keeps the stage up for hours), so the
                // door to the settings belongs here; the v1.0.26
                // home-only gear was effectively invisible to him.
                // v1.0.31: the retired home screen's two front-row
                // items join the gear here — the brand title right
                // of the gear (gold, the home title's own colour;
                // 一起唱卡拉OK in Chinese, MySing in English) and
                // (v1.0.36: that text title is now the user's logo
                // artwork — brand_logo_zh / brand_logo_en — still
                // language-switched, at the row's full height) and
                // the language chips, still switchable in the
                // front as the user ruled. The row is exactly
                // gear-tall (36/46dp) so nothing overlaps the
                // stage below; the next-up text runs after the
                // chips, vertically centred, with its right end
                // still clear of the QR card, and the lyrics band
                // below is untouched. Entering the admin page from
                // here never stops the song, and Back returns to
                // this screen (adminReturnTo).
                Row(
                    modifier = Modifier.fillMaxWidth().height(if (compact) 36.dp else 46.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GearButton(compact) { adminReturnTo = Screen.PLAYER; screen = Screen.ADMIN }
                    Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
                    // Brand logo (v1.0.36 artwork, v1.0.44
                    // silhouette halo): see BrandLogo — display
                    // only, banner-row height, width by aspect.
                    BrandLogo(compact)
                    Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
                    // Language switch (v1.0.13, moved here in
                    // v1.0.31): the two chips label themselves in
                    // their own language, so the switch is findable
                    // from either side; the choice persists.
                    Chip("中文", uiLang == UiLang.ZH, compact = compact) { switchUiLang(UiLang.ZH) }
                    Spacer(Modifier.width(4.dp))
                    Chip("EN", uiLang == UiLang.EN, compact = compact) { switchUiLang(UiLang.EN) }
                    Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
                    Box(
                        modifier = Modifier.weight(1f)
                            // The banner runs along the top: keep its
                            // right end clear of the top-right QR card.
                            .padding(end = if (compact) 90.dp else 114.dp),
                    ) {
                        // During the v1.0.23 ceremony the banner yields
                        // to the big centred next-song card on the stage
                        // (never the same line in two places at once).
                        if (nextUp != null && ceremonyNext == null) {
                            Text(nextUp, color = gold, fontSize = (if (compact) 14 else 19).sp, fontWeight = FontWeight.Bold)
                        }
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
                        // v1.0.30: the intro boundary is the first
                        // SUNG line — the LRC's header block (title
                        // card + 词/曲 credit lines at 0–2 s) used to
                        // pose as it and the invite never appeared.
                        val firstLyricStart = remember(lyrics, entry?.song?.id) {
                            DancerActivity.firstSungStartMs(
                                lyrics, entry?.song?.artist, entry?.song?.title,
                            )
                        }
                        val dancerPhase = DancerActivity.phase(
                            positionMs, firstLyricStart, lastLyricEnd,
                            durationMs.takeIf { it > 0 },
                        )
                        // --- dancer LEFT of the lyrics, in three
                        // fixed zones (v1.0.28, see LyricsBand):
                        // dancer zone | gap | lyrics band | reserve
                        // band. The lyrics band's edges are CONSTANT
                        // — the dancer zone is reserved even while
                        // no dancer shows, and the reserve band on
                        // the right (queue panel + margin wide) is
                        // never given to the lyrics, so the floating
                        // panel and the corner QR physically cannot
                        // cover a lyric character again. ---
                        val stagePad = if (compact) 12.dp else 24.dp
                        val band = LyricsBand.compute(
                            stageWidthDp = (screenW - stagePad * 2).value,
                            screenWidthDp = screenW.value,
                        )
                        Row(modifier = Modifier.fillMaxSize()) {
                          Box(modifier = Modifier.fillMaxHeight().width(band.dancerWidthDp.dp)) {
                            if (entry != null && !showCelebration && dancersOn) {
                                // The dancer belongs to whoever ordered this song.
                                val dancer = Dancers.byKey(app.prefs.dancerFor(entry.orderedBy))
                                // v1.0.25: the zone measures ITSELF —
                                // the dancer's height is a fraction
                                // of the stage's real height (it used
                                // to be a fraction of the full screen
                                // height, silently coerced down by
                                // whatever the stage actually got).
                                BoxWithConstraints(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    // v1.0.32: during the intro invite
                                    // she takes one big step toward
                                    // the audience — her LAYOUT height
                                    // grows from the dancing height
                                    // (0.80 of the stage, compact
                                    // 0.72) to 0.95 (compact 0.88)
                                    // across the intro's first ~0.7 s,
                                    // holds while she invites, and
                                    // steps back across the singing's
                                    // first ~0.7 s (DancerActivity
                                    // .stepProgress — a pure function
                                    // of position; the robot's
                                    // envelope is quantised into hard
                                    // steps). Layout height, not a
                                    // layer scale, so the spotlight
                                    // pool inside her wrapper — sized
                                    // off her own rect — grows with
                                    // her, and BottomCenter keeps her
                                    // feet on the stage floor while
                                    // she grows upward.
                                    val stepP = DancerActivity.stepProgress(
                                        positionMs, firstLyricStart, dancer.profile,
                                    )
                                    val baseDancerH = maxHeight * if (compact) 0.72f else 0.80f
                                    val bigDancerH = maxHeight * if (compact) 0.88f else 0.95f
                                    val dancerH = baseDancerH + (bigDancerH - baseDancerH) * stepP
                                    // Lyrics-band guard (v1.0.28's
                                    // invariant, kept): her width
                                    // grows with her height; if the
                                    // grown width would pass the
                                    // dancer zone's right edge — the
                                    // lyrics band starts one gap
                                    // beyond it — shift her left by
                                    // exactly the centred overflow,
                                    // so she grows leftward over the
                                    // bubble backdrop and her right
                                    // edge never reaches a lyric.
                                    val stepOverflowDp =
                                        (SpotlightGeom.dancerWidthDp(dancerH.value) - maxWidth.value) / 2f
                                    DancerImage(
                                        dancer = dancer,
                                        modifier = if (stepOverflowDp > 0f) {
                                            Modifier.offset(x = (-stepOverflowDp).dp)
                                        } else {
                                            Modifier
                                        },
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
                                        // v1.0.25: of the STAGE height
                                        // (this zone's own height), feet
                                        // on the stage floor, spotlight
                                        // pool under them. v1.0.32:
                                        // dancerH carries the intro
                                        // step-forward envelope computed
                                        // above (== the base height
                                        // whenever no step is running).
                                        heightDp = dancerH,
                                        spotlight = true,
                                    )
                                }
                            }
                          }
                            Spacer(Modifier.width(LyricsBand.GAP_DP.dp))
                            Column(modifier = Modifier.width(band.widthDp.dp).fillMaxHeight()) {
                                Text(
                                    // A filler song has no orderer to
                                    // credit: just its name (v1.0.20).
                                    entry?.let {
                                        if (it.isFiller) it.song.displayName
                                        else s(TvKey.NOW_SINGING_LINE, it.song.displayName, it.orderedBy)
                                    } ?: "",
                                    color = soft, fontSize = (if (compact) 12 else 15).sp,
                                )
                                LyricsView(
                                    lyrics, positionMs, compact,
                                    modifier = Modifier.weight(1f),
                                    // v1.0.26: the admin page's sweep
                                    // colour; read from activity state
                                    // so a mid-song change recolours
                                    // the very next frame.
                                    sweepColor = Color(sweepColor.argb),
                                )
                            }
                        }
                    }
                    if (showCelebration) {
                        // Song finished: all six come out and clap together.
                        // v1.0.45: same enlarged, tightened row as
                        // the ceremony — one "six come out" scene,
                        // one size, one pitch.
                        SixInRow(
                            heightDp = SpotlightGeom.sixRowHeightDp(
                                screenW.value, screenH.value,
                            ).dp,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        ) { dancer, h ->
                            DancerImage(
                                dancer = dancer,
                                pose = DanceMotion.clapPose(dancer.profile, clapElapsed),
                                heightDp = h,
                                // v1.0.25: each of the six stands
                                // in their own spotlight pool.
                                spotlight = true,
                            )
                        }
                    }
                  }
                    // --- queue panel: a floating, semi-transparent
                    // overlay on the stage's right edge, under the QR
                    // card (v1.0.24 — it was a fixed 23% column before
                    // that). Since v1.0.28 it floats over the stage's
                    // RESERVE BAND only (see LyricsBand): the lyrics
                    // band ends a gap short of the panel's left edge
                    // whether the panel is showing or not, so the
                    // overlay can no longer cover lyric text. Focus
                    // and actions are unchanged: explicit requesters
                    // and the control bar's D-pad Up handler never
                    // cared where in the tree the panel sits. ---
                    if (panelVisible) {
                      Box(modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight()) {
                        QueuePanel(
                            rows = panelRows,
                            compact = compact,
                            width = screenW * 0.19f,
                            topPad = if (compact) 70.dp else 88.dp,
                            actionIndex = actionIndex,
                            rowRequesters = rowRequesters,
                            playNowRequester = playNowRequester,
                            promoteRequester = promoteRequester,
                            deleteRequester = deleteRequester,
                            onRowClick = { idx ->
                                if (actionIndex == idx) {
                                    actionIndex = null
                                    focusTarget = PanelFocusTarget.Row(idx)
                                } else {
                                    actionIndex = idx
                                    // "现在唱" leads the action row and
                                    // is offered for every waiting song
                                    // (v1.0.27), so expansion always
                                    // lands focus on it; D-pad Right
                                    // walks on to 下一首唱 / 删除.
                                    focusTarget = PanelFocusTarget.PlayNowAction
                                }
                            },
                            onPlayNow = { idx ->
                                jumpToWaiting(idx)
                                actionIndex = null
                                // The chosen song is singing now; the
                                // panel's business is done — hand focus
                                // back to the control bar.
                                focusTarget = PanelFocusTarget.ControlBar
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
                // --- error banner: a real failure's banner stays
                // up; a self-healed one clears once the plain retry
                // is confirmed playing (v1.0.15, see
                // ErrorDisplayPolicy). This is a fault signal, not
                // the diagnostics line — v1.0.35 moved the small
                // print to the admin page, the banner stays here. ---
                errorBanner?.let { err ->
                    Text(s(TvKey.PLAY_ERROR, err), color = Color(0xFFFF7043), fontSize = (if (compact) 11 else 12).sp)
                }
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
                    // v1.0.25: tighter spacing + compact chips, so
                    // the chips fit the compact landscape width
                    // in one row and the bar hugs the screen bottom
                    // (the old overflow is the v1.0.25 root cause).
                    horizontalArrangement = Arrangement.spacedBy(
                        if (compact) 6.dp else 8.dp, Alignment.CenterHorizontally,
                    ),
                ) {
                    Chip(
                        if (controller.player.isPlaying) s(TvKey.PAUSE) else s(TvKey.RESUME), false,
                        modifier = Modifier.focusRequester(controlRequester),
                        onFocused = { panelFocused = false },
                        compact = compact,
                    ) { controller.togglePlayPause() }
                    Chip(s(TvKey.REPLAY), false, onFocused = { panelFocused = false }, compact = compact) { controller.replay() }
                    Chip(
                        if (accompaniment) s(TvKey.ACCOMP_ON) else s(TvKey.ACCOMP_OFF), accompaniment,
                        onFocused = { panelFocused = false },
                        compact = compact,
                    ) {
                        accompaniment = !accompaniment; controller.setAccompaniment(accompaniment)
                    }
                    Chip(s(TvKey.PITCH_DOWN), false, onFocused = { panelFocused = false }, compact = compact) { controller.shiftPitch(-1); semitoneLabel = controller.semitones }
                    Chip(s(TvKey.PITCH_LABEL, semitoneLabel), false, onFocused = { panelFocused = false }, compact = compact) { }
                    Chip(s(TvKey.PITCH_UP), false, onFocused = { panelFocused = false }, compact = compact) { controller.shiftPitch(1); semitoneLabel = controller.semitones }
                    Chip(
                        if (dancersOn) s(TvKey.DANCERS_ON) else s(TvKey.DANCERS_OFF), dancersOn,
                        onFocused = { panelFocused = false },
                        compact = compact,
                    ) {
                        dancersOn = !dancersOn; app.prefs.dancersEnabled = dancersOn
                    }
                    // v1.0.31: no 🎲 filler chip any more — filler
                    // is unconditional (nobody orders, it starts).
                    Chip(s(TvKey.QUIT), false, onFocused = { panelFocused = false }, compact = compact) { confirmQuit = true }
                    Chip(s(TvKey.GO_ORDER), false, onFocused = { panelFocused = false }, compact = compact) { screen = Screen.BROWSE }
                }
            }
        }
    }

    /**
     * Player state small print for debugging. It sat under the
     * lyrics on the stage until v1.0.34; since v1.0.35 it lives in
     * the admin page's diagnostics section (the user: the stage is
     * for singing, the small print goes backstage). v1.0.40: the
     * accompaniment note names its real cause again, and the last
     * playback error is appended at the end of the line.
     */
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
                val firstStart = DancerActivity.firstSungStartMs(
                    lyrics, entry.song.artist, entry.song.title,
                )
                when (DancerActivity.phase(positionMs, firstStart, lastEnd)) {
                    DancerActivity.Phase.INVITING -> append(" · 伴舞：前奏邀请")
                    DancerActivity.Phase.OUTRO_HOLD -> append(" · 伴舞：尾奏定格")
                    DancerActivity.Phase.STILL -> append(" · 伴舞：尾奏静止")
                    DancerActivity.Phase.DANCING -> {}
                }
            }
            if (controller.accompanimentUnavailable) {
                // Name the actual cause (v1.0.40): on the custom
                // path the processor rejected this song's format;
                // on any other path the live build is the bare
                // retry, which has no vocal processor at all.
                append(" · 伴奏不可用（")
                append(if (controller.sinkLabel == "custom") "此歌格式不支持消音" else "当前通道无消音")
                append("）")
            }
            // v1.0.40: the last playback error (its text leads
            // with the error code name) rides at the very end of
            // the line, so one admin screenshot names the fault.
            val lastErr = controller.lastError
            if (!lastErr.isNullOrEmpty()) append(" · 错误 ").append(lastErr)
        }
    }

    /**
     * The always-on corner QR card: a small white rounded card with
     * the phone-ordering QR and a "扫码点歌" caption, pinned to the
     * top-right corner of every screen (v1.0.19; it sat bottom-right
     * until then). Deliberately has no clickable/focusable modifier
     * — D-pad focus must never land on it. The QR itself stays pure
     * black on white so it scans; only the thin border is
     * translucent dark, to blend with the stage.
     *
     * v1.0.31: the card shrank a full size step (the user: the
     * white card was too big). What shrank is only the card's own
     * white trim — the QR bitmap keeps ZXing's built-in 4-module
     * quiet zone untouched inside the image, so the code still
     * scans as reliably as before; the caption stays.
     *
     * v1.0.36 (the user's final word on this card, 2026-11-15):
     * ONLY the white trim shrinks — the card padding is down to
     * 2dp and the QR to 56/44dp, so the whole card is notably
     * smaller than the v1.0.31 76/60dp card; the caption stays
     * BELOW the code, hugging the card's bottom edge. The QR
     * bitmap itself is pixel-for-pixel what Qr.encode produces
     * (quiet zone included) — nothing is drawn into it, so
     * scanning reliability is unchanged.
     */
    @Composable
    private fun CornerQrCard(ip: String, port: Int, compact: Boolean, modifier: Modifier = Modifier) {
        val qrDp = if (compact) 44.dp else 56.dp
        val bitmap = remember(ip, port) { Qr.encode("http://$ip:$port/", 220) }
        Column(
            modifier = modifier
                .border(1.dp, Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .background(Color.White, RoundedCornerShape(10.dp))
                .padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = s(TvKey.CORNER_QR_DESC),
                modifier = Modifier.width(qrDp).height(qrDp),
            )
            Text(s(TvKey.CORNER_QR_CAPTION), color = Color(0xFF26325E), fontSize = (if (compact) 8 else 10).sp)
        }
    }

    /**
     * The corner card's no-network form (v1.0.42): the exact same
     * card geometry as [CornerQrCard] — same white card, border,
     * code-sized square and caption line — but dimmed, with the
     * waiting-for-network line where the code would be. Before
     * this, a missing LAN address made the corner silently empty
     * (and, the address being re-read only on queue-state bumps,
     * it could stay empty for a whole session); now the corner
     * always explains itself, and the poll in
     * [rememberLanIpAddress] swaps the real QR back in by itself
     * when the network returns. Pure display: no click, no focus.
     */
    @Composable
    private fun CornerQrPlaceholderCard(compact: Boolean, modifier: Modifier = Modifier) {
        val qrDp = if (compact) 44.dp else 56.dp
        val ink = Color(0xFF26325E).copy(alpha = 0.72f)
        Column(
            modifier = modifier
                .border(1.dp, Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.width(qrDp).height(qrDp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    s(TvKey.CORNER_QR_WAITING),
                    color = ink,
                    fontSize = (if (compact) 8 else 9).sp,
                    textAlign = TextAlign.Center,
                )
            }
            Text(s(TvKey.CORNER_QR_CAPTION), color = ink, fontSize = (if (compact) 8 else 10).sp)
        }
    }

    /**
     * The queue panel (v1.0.19): right side of the player screen,
     * below the top-right QR card ([topPad] clears it). Lists the
     * shared queue exactly as [QueuePanelModel] builds it — current
     * song with ▶ and no number, waiting songs numbered — and gives
     * the remote holder the queue actions （现在唱 since v1.0.27,
     * plus 下一首唱 / 删除）. It renders only the model; every
     * mutation goes through KaraokeState, so the phone pages see
     * the same queue change from the same state bump.
     */
    @Composable
    private fun QueuePanel(
        rows: List<QueuePanelModel.Row>,
        compact: Boolean,
        width: Dp,
        topPad: Dp,
        actionIndex: Int?,
        rowRequesters: MutableMap<Int, FocusRequester>,
        playNowRequester: FocusRequester,
        promoteRequester: FocusRequester,
        deleteRequester: FocusRequester,
        onRowClick: (Int) -> Unit,
        onPlayNow: (Int) -> Unit,
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
                            playNowRequester = playNowRequester,
                            promoteRequester = promoteRequester,
                            deleteRequester = deleteRequester,
                            onClick = { onRowClick(row.waitingIndex) },
                            onPlayNow = { onPlayNow(row.waitingIndex) },
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
     *  ordered it — and, once OK expands it, the action chips
     *  （现在唱 / 下一首唱 / 删除）. Nothing is executed from the
     *  row itself: an action runs only when its own chip takes
     *  the second OK. */
    @Composable
    private fun QueuePanelRowItem(
        row: QueuePanelModel.Row,
        compact: Boolean,
        expanded: Boolean,
        focusRequester: FocusRequester,
        playNowRequester: FocusRequester,
        promoteRequester: FocusRequester,
        deleteRequester: FocusRequester,
        onClick: () -> Unit,
        onPlayNow: () -> Unit,
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
                    if (QueuePanelModel.canPlayNow(row.waitingIndex)) {
                        PanelActionChip(
                            s(TvKey.QUEUE_PANEL_PLAY_NOW), compact, playNowRequester, onPanelFocus, onPlayNow,
                        )
                    }
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
     * timeline): a big card announcing the locked next song —
     * raised to the upper stage in v1.0.45 so the enlarged row of
     * six below never crowds it — the six dancers clapping /
     * inviting in a row at the bottom, and — from the exit
     * segment on — the keeper alone at
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
            // --- big next-song card, upper-centre on the stage
            //     (v1.0.45 — the user: "将歌曲文件名提高"): it was
            //     dead centre until the row of six grew; now its
            //     top edge sits 12% of the screen height below the
            //     stage top (the stage itself starts under the
            //     banner row), clear of the banner above and the
            //     enlarged dancers below, in every phase. ---
            val remainingSec =
                ((Ceremony.TOTAL_MS - elapsedMs).coerceAtLeast(0L) + 999L) / 1000L
            Column(
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = screenH * 0.12f)
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
                // v1.0.45: as tall as fits — target 0.46 of the
                // screen height, capped by the row's total width
                // (see SpotlightGeom.sixRowHeightDp).
                val rowHeight = SpotlightGeom.sixRowHeightDp(screenW.value, screenH.value).dp
                SixInRow(
                    heightDp = rowHeight,
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) { dancer, h ->
                    when (phase) {
                        Ceremony.Phase.CLAPPING -> DancerImage(
                            dancer = dancer,
                            pose = DanceMotion.clapPose(dancer.profile, elapsedMs),
                            heightDp = h,
                            alpha = rowAlpha,
                            // v1.0.25: a spotlight pool each;
                            // it melts with the row's alpha.
                            spotlight = true,
                        )
                        Ceremony.Phase.GROUP_INVITE -> DancerImage(
                            dancer = dancer,
                            pose = Ceremony.invitePose(
                                dancer.profile,
                                elapsedMs - Ceremony.CLAP_MS,
                                Ceremony.GROUP_INVITE_MS,
                            ),
                            heightDp = h,
                            invite = true,
                            alpha = rowAlpha,
                            spotlight = true,
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
                            heightDp = h,
                            invite = true,
                            alpha = rowAlpha,
                            spotlight = true,
                        )
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
                // v1.0.25: the keeper's zone now matches the
                // playback dancer zone EXACTLY (width 0.36 of the
                // screen, height 0.72/0.80 of the stage's own
                // height — it was 0.32 wide and 0.62/0.70 of the
                // screen height, so the handover had a small
                // geometry step after all). Her spotlight pool
                // stays lit through the solo invite.
                BoxWithConstraints(
                    modifier = Modifier.fillMaxHeight().width(screenW * 0.36f),
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
                        heightDp = maxHeight * if (compact) 0.72f else 0.80f,
                        invite = true,
                        alpha = 0.95f * Ceremony.keeperSoloAlpha(elapsedMs),
                        spotlight = true,
                    )
                }
            }
        }
    }

    /**
     * The row of six (v1.0.45): all six dancers standing close,
     * group-photo style. Each frame is [SpotlightGeom.FRAME_ASPECT]
     * × height wide but the figure inside is centred with
     * transparent margins on both sides, so an edge-to-edge Row
     * read as people standing far apart (the user: "六人可以靠近
     * 一些，现在分得太开"). Here every dancer stands at a centre
     * pitch of [SpotlightGeom.SIX_ROW_PITCH_FRACTION] × height —
     * the transparent margins overlap, wide-arm poses may touch
     * slightly (intended), and the total span is exactly
     * [SpotlightGeom.SIX_ROW_SPAN_PER_HEIGHT] × height, the span
     * the height formula budgets for. Positions are explicit
     * offsets inside a fixed-span box, so the pitch holds by
     * construction, not by an arrangement's spacing arithmetic.
     */
    @Composable
    private fun SixInRow(
        heightDp: Dp,
        modifier: Modifier = Modifier,
        content: @Composable (dancer: Dancer, heightDp: Dp) -> Unit,
    ) {
        val pitch = heightDp * SpotlightGeom.SIX_ROW_PITCH_FRACTION
        val span = heightDp * SpotlightGeom.SIX_ROW_SPAN_PER_HEIGHT
        Box(modifier = modifier.width(span).height(heightDp)) {
            Dancers.ALL.forEachIndexed { index, dancer ->
                Box(
                    modifier = Modifier.align(Alignment.BottomStart)
                        .offset(x = pitch * index),
                ) {
                    content(dancer, heightDp)
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
        /** v1.0.25: the stage spotlight pool under the feet,
         *  drawn behind the figure inside this wrapper (the
         *  wrapper IS the figure's rect, so the pool follows its
         *  position and size, and fades with [alpha]). */
        spotlight: Boolean = false,
    ) {
        // v1.0.10: while a beat's cross-fade is still running, the
        // previous pose frame is drawn under the current one with
        // complementary alphas — same rect, same whole-body
        // transform, only the alpha differs — so the frame change
        // melts instead of cutting. Once the fade completes (or no
        // blend was supplied: robot, invite/still, applause row)
        // this is exactly the old single-image draw.
        //
        // v1.0.42: the frame box is a FIXED rect — width =
        // heightDp × SpotlightGeom.FRAME_ASPECT via
        // [SpotlightGeom.dancerWidthDp] (the nominal frame aspect
        // the rest of the stage geometry, the spotlight pool and
        // the v1.0.32 step-overflow guard already assume; the
        // widest real frame is exactly 0.78, so nothing
        // overflows) — and both cross-fade layers are
        // BottomCenter-aligned inside it. Before this the box
        // sized itself to its widest child and TopStart-aligned
        // the layers: frame canvas aspects run 0.195–0.78, so
        // through every beat's cross-fade (35–50% of each beat)
        // the narrower frame sat (wide − narrow)/2 off its solo
        // position and snapped back to centre when the fade
        // ended — the whole figure jumped sideways and back once
        // per beat ("整个身体左右闪", the user on v1.0.41). That
        // lived in the layout, not the art: v1.0.41's in-image
        // pelvis alignment is complementary and stays, but this
        // fixed rect is what stops the whole-body flash. The
        // spotlight pool rides this same box (its drawBehind
        // sits outside the size modifiers, so it sees the fixed
        // rect), making the pool constant-size too.
        Box(
            modifier = (if (spotlight) modifier.spotlightPool(alphaScale = alpha) else modifier)
                .width(SpotlightGeom.dancerWidthDp(heightDp.value).dp)
                .height(heightDp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (blend != null && !invite &&
                blend.currentAlpha < 1f &&
                blend.previousIndex != blend.currentIndex &&
                blend.previousIndex in dancer.frames.indices &&
                blend.currentIndex in dancer.frames.indices
            ) {
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
                )
            }
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

    /** A section heading inside the admin page (v1.0.26). */
    @Composable
    private fun AdminSectionTitle(label: String, compact: Boolean) {
        Text(
            label,
            color = gold,
            fontSize = (if (compact) 16 else 21).sp,
            fontWeight = FontWeight.Bold,
        )
    }

    /**
     * The admin entry (v1.0.26 on the old home screen, v1.0.29
     * also on the player screen, v1.0.31 also on the browse
     * page): just a small gear in a circle — the user's ruling
     * was "a little gear in the front, the settings behind it".
     * Same component everywhere, so the doors can never drift
     * apart. Same focus language as every
     * other control (a ring when focused, tap/OK to open); its
     * content description names it ("Settings") in both UI
     * languages, since the glyph itself carries no text.
     */
    @Composable
    private fun GearButton(compact: Boolean, onClick: () -> Unit) {
        var focused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .size(if (compact) 36.dp else 46.dp)
                .border(2.dp, if (focused) gold else Color.Transparent, CircleShape)
                .background(cardBg, CircleShape)
                .focusable()
                .onFocusChanged { focused = it.isFocused }
                .clickable { onClick() }
                .semantics { contentDescription = s(TvKey.ADMIN_GEAR_DESC) },
            contentAlignment = Alignment.Center,
        ) {
            Text("⚙", color = Color.White, fontSize = (if (compact) 18 else 24).sp)
        }
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
        /** v1.0.25: the player control bar passes this — slimmer
         *  padding and type so all ten chips fit a compact
         *  landscape's width in ONE row (see the v1.0.25 chapter:
         *  the old fit overflowed and the last chip wrapped
         *  vertically, inflating the whole bar). */
        compact: Boolean = false,
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
                // less of the stage's height.
                .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 4.dp),
        ) {
            // maxLines = 1 is a hard rule, not a style choice: a
            // chip label must never wrap character-by-character
            // and blow up its row's height again (v1.0.25 root
            // cause) — on an impossibly narrow window it clips
            // instead.
            Text(
                label,
                color = if (selected) Color.Black else Color.White,
                fontSize = (if (compact) 12 else 13).sp,
                maxLines = 1,
            )
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
