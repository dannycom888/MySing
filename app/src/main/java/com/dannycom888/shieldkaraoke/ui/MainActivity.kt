package com.dannycom888.shieldkaraoke.ui

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.dannycom888.shieldkaraoke.queue.QueueEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Screen { HOME, BROWSE, PLAYER }
private enum class ListType { SONGS, SINGERS }
private enum class Special { NONE, FAVORITES, RECENTS }

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
    private var tickMs by mutableStateOf(0L)
    private var dancersOn by mutableStateOf(true)
    private var currentBpm by mutableStateOf(DEFAULT_BPM)
    private var celebrating by mutableStateOf(false)
    private var applauseStartMs by mutableStateOf(0L)
    private var celebrationToken = 0
    private var confirmQuit by mutableStateOf(false)
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
                    }
                    // Corner QR: on screen at all times so a phone can
                    // join in mid-song — except on Home, whose header
                    // already carries the big QR (never both).
                    val cornerIp = remember(app.state.version) { Qr.lanIpAddress() }
                    if (cornerIp != null && screen != Screen.HOME) {
                        CornerQrCard(
                            ip = cornerIp,
                            port = app.localServerPort(),
                            compact = compact,
                            modifier = Modifier.align(Alignment.BottomEnd)
                                .padding(
                                    end = if (compact) 12.dp else 24.dp,
                                    bottom = if (compact) 70.dp else 88.dp,
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
            withContext(Dispatchers.IO) {
                LibraryCacheStore(this@MainActivity).save(treeUri.toString(), songs)
            }
        }
    }

    // ---------- playback flow ----------

    private fun startSong(entry: QueueEntry) {
        app.state.nowPlaying = entry
        app.prefs.pushRecent(entry.song.id)
        lyrics = emptyList()
        positionMs = 0
        accompaniment = false
        semitoneLabel = 0
        celebrating = false
        celebrationToken++
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

    private fun onSongEndedNaturally() {
        if (quitting) return
        runOnUiThread {
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
                        screen = Screen.HOME
                    }
                }
            } else {
                // Applause + a few seconds for the next singer to get ready.
                app.state.interludeSeconds = 5
                lifecycleScope.launch {
                    while (app.state.interludeSeconds > 0) {
                        delay(1000)
                        app.state.interludeSeconds -= 1
                    }
                    if (celebrationToken == token) startSong(next)
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
        app.state.interludeSeconds = 0
        val next = app.state.advanceAfterQuit()
        if (next == null) {
            app.state.nowPlaying = null
            screen = Screen.HOME
        } else {
            startSong(next)
        }
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
                }
                if (ip != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val qr = remember(ip, port) { Qr.encode("http://$ip:$port/", if (compact) 110 else 170) }
                        Image(bitmap = qr.asImageBitmap(), contentDescription = s(TvKey.QR_DESC),
                            modifier = Modifier.height(if (compact) 62.dp else 110.dp).width(if (compact) 62.dp else 110.dp))
                        Text(s(TvKey.SCAN_QR), color = Color.White, fontSize = (if (compact) 11 else 14).sp)
                    }
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
        // Bottom scroll clearance so the last list row can be scrolled
        // fully clear of the always-on corner QR card (bottom offset
        // + card height + a margin); no card without a LAN address.
        val qrClearance = if (remember(app.state.version) { Qr.lanIpAddress() } != null) {
            if (compact) 186.dp else 224.dp
        } else {
            8.dp
        }
        Column(modifier = Modifier.fillMaxSize().padding(if (compact) 12.dp else 24.dp)) {
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
                    contentPadding = PaddingValues(bottom = qrClearance),
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
                    contentPadding = PaddingValues(bottom = qrClearance),
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
                tickMs = SystemClock.elapsedRealtime()
                // ~30 fps so the karaoke sweep's gold boundary
                // travels continuously through each character.
                delay(33)
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenH = maxHeight
            val screenW = maxWidth
            Column(modifier = Modifier.fillMaxSize().padding(if (compact) 12.dp else 24.dp)) {
                val nextUp = state.queue.nextUpText(uiLang)
                Box(modifier = Modifier.fillMaxWidth().height(if (compact) 26.dp else 36.dp)) {
                    if (nextUp != null) {
                        Text(nextUp, color = gold, fontSize = (if (compact) 14 else 19).sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val clapElapsed = tickMs - applauseStartMs
                    val showCelebration = celebrating && entry != null &&
                        clapElapsed in 0..(APPLAUSE_MS + 200)
                    if (state.interludeSeconds > 0) {
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
                        // Three dancer phases (DancerActivity): the
                        // intro invites (invite frame + slow bow),
                        // the sung part dances, and once the last
                        // lyric line has been sung only music
                        // remains — the dancer stands in its neutral
                        // frame, only breathing slowly. A song
                        // without an LRC has neither boundary and
                        // dances throughout.
                        val lastLyricEnd = remember(lyrics) { DancerActivity.lastLyricEndMs(lyrics) }
                        val firstLyricStart = remember(lyrics) { DancerActivity.firstLyricStartMs(lyrics) }
                        val dancerPhase = DancerActivity.phase(positionMs, firstLyricStart, lastLyricEnd)
                        // --- dancer LEFT of the lyrics, lyrics column gives
                        //     up the left zone and takes the rest ---
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (entry != null && !showCelebration && dancersOn) {
                                // The dancer belongs to whoever ordered this song.
                                val dancer = Dancers.byKey(app.prefs.dancerFor(entry.orderedBy))
                                Box(
                                    modifier = Modifier.fillMaxHeight().width(screenW * 0.32f),
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
                                            // Sung out: neutral stance, no
                                            // bounce/sway/fall — only a
                                            // slow breath (v1.0.7).
                                            DancerActivity.Phase.STILL ->
                                                DancerActivity.breathPose(positionMs, dancer.profile)
                                        },
                                        frameIndex = if (dancerPhase == DancerActivity.Phase.DANCING) {
                                            PoseCycle.frameIndex(
                                                positionMs, currentBpm, dancer.frames.size,
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
                                        blend = if (dancerPhase == DancerActivity.Phase.DANCING &&
                                            dancer.profile != DanceMotion.Profile.ROBOT
                                        ) {
                                            PoseCycle.blend(
                                                positionMs, currentBpm, dancer.frames.size,
                                                phaseOffsetBeats = Dancers.indexOf(dancer) * 0.25,
                                            )
                                        } else {
                                            null
                                        },
                                        invite = dancerPhase == DancerActivity.Phase.INVITING,
                                        heightDp = screenH * if (compact) 0.62f else 0.70f,
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                Text(
                                    entry?.let { s(TvKey.NOW_SINGING_LINE, it.song.displayName, it.orderedBy) } ?: "",
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
                // --- diagnostics: always visible; an error never auto-hides ---
                controller.lastError?.let { err ->
                    Text(s(TvKey.PLAY_ERROR, err), color = Color(0xFFFF7043), fontSize = (if (compact) 11 else 12).sp)
                }
                Text(
                    diagnosticsText(entry),
                    color = soft.copy(alpha = 0.8f), fontSize = (if (compact) 10 else 11).sp,
                )
                // --- control bar: permanently visible, never auto-hides ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    Chip(if (controller.player.isPlaying) s(TvKey.PAUSE) else s(TvKey.RESUME), false) { controller.togglePlayPause() }
                    Chip(s(TvKey.REPLAY), false) { controller.replay() }
                    Chip(if (accompaniment) s(TvKey.ACCOMP_ON) else s(TvKey.ACCOMP_OFF), accompaniment) {
                        accompaniment = !accompaniment; controller.setAccompaniment(accompaniment)
                    }
                    Chip(s(TvKey.PITCH_DOWN), false) { controller.shiftPitch(-1); semitoneLabel = controller.semitones }
                    Chip(s(TvKey.PITCH_LABEL, semitoneLabel), false) { }
                    Chip(s(TvKey.PITCH_UP), false) { controller.shiftPitch(1); semitoneLabel = controller.semitones }
                    Chip(if (dancersOn) s(TvKey.DANCERS_ON) else s(TvKey.DANCERS_OFF), dancersOn) {
                        dancersOn = !dancersOn; app.prefs.dancersEnabled = dancersOn
                    }
                    Chip(s(TvKey.QUIT), false) { confirmQuit = true }
                    Chip(s(TvKey.GO_ORDER), false) { screen = Screen.BROWSE }
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
                append(" · 舞者：").append(dancer.label).append("（").append(entry.orderedBy).append("选的）")
                val lastEnd = DancerActivity.lastLyricEndMs(lyrics)
                val firstStart = DancerActivity.firstLyricStartMs(lyrics)
                when (DancerActivity.phase(positionMs, firstStart, lastEnd)) {
                    DancerActivity.Phase.INVITING -> append(" · 伴舞：前奏邀请")
                    DancerActivity.Phase.STILL -> append(" · 伴舞：尾奏静止")
                    DancerActivity.Phase.DANCING -> {}
                }
            }
            if (controller.accompanimentUnavailable) append(" · 伴奏不可用（已降级普通播放）")
        }
    }

    /**
     * The always-on corner QR card: a small white rounded card with
     * the phone-ordering QR and a "扫码点歌" caption, parked at the
     * bottom-right above the player control bar. Deliberately has no
     * clickable/focusable modifier — D-pad focus must never land on
     * it. The QR itself stays pure black on white so it scans; only
     * the thin border is translucent dark, to blend with the stage.
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

    @Composable
    private fun DancerImage(
        dancer: Dancer,
        pose: DanceMotion.Pose,
        heightDp: Dp,
        modifier: Modifier = Modifier,
        frameIndex: Int = -1,
        invite: Boolean = false,
        blend: PoseCycle.Blend? = null,
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
                    label = dancer.label,
                    pose = pose,
                    heightDp = heightDp,
                    alpha = 0.95f * (1f - blend.currentAlpha),
                )
                DancerFrame(
                    resId = dancer.frames[blend.currentIndex],
                    label = dancer.label,
                    pose = pose,
                    heightDp = heightDp,
                    alpha = 0.95f * blend.currentAlpha,
                )
            }
        } else {
            DancerFrame(
                resId = when {
                    invite -> dancer.inviteResId
                    frameIndex in dancer.frames.indices -> dancer.frames[frameIndex]
                    else -> dancer.resId
                },
                label = dancer.label,
                pose = pose,
                heightDp = heightDp,
                alpha = 0.95f,
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
    private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
        var focused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(16.dp))
                .background(if (selected) gold else cardBg, RoundedCornerShape(16.dp))
                .focusable()
                .onFocusChanged { focused = it.isFocused }
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
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
