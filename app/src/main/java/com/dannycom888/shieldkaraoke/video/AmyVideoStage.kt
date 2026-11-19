package com.dannycom888.shieldkaraoke.video

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.dannycom888.shieldkaraoke.ui.SpotlightGeom
import com.dannycom888.shieldkaraoke.ui.contactShadow
import com.dannycom888.shieldkaraoke.ui.spotlightPool
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.floor

/** What Amy's video is doing in a solo spot (v1.0.70 pilot). */
enum class AmyVideoMode {
    /** Beat-sliced dancing (see [BeatSliceScheduler]). */
    DANCE,

    /** The invite special, looped at natural speed. */
    INVITE,

    /** The clap special, looped at natural speed. Wired into
     *  the driver and the catalogue; the stage's applause
     *  phases themselves stay the five-figure frame row in
     *  the pilot (see BUILD_REPORT v1.0.70). */
    CLAP,

    /** The idle special (standing, breathing), looped. */
    IDLE,
}

/**
 * Live video status for the diagnostics line: written by the
 * driver on the main thread, read whenever the admin page
 * composes its diagnostics text.
 */
class AmyVideoStatus {
    @Volatile var mode: AmyVideoMode? = null
    @Volatile var clipName: String = "-"
    @Volatile var speed: Float = 1f
}

/**
 * Read the pack catalogue from the bundled assets: every
 * `*.accents.json` under [AmyVideoPack.ASSET_DIR] becomes one
 * [AmyClip]. A malformed file is skipped (a missing clip),
 * never fatal — the caller treats an empty catalogue as
 * "video unavailable" and Amy stays on frames.
 */
fun loadAmyCatalogue(context: Context): List<AmyClip> {
    val files = try {
        context.assets.list(AmyVideoPack.ASSET_DIR)?.toList() ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
    return files.filter { it.endsWith(".accents.json") }.mapNotNull { file ->
        try {
            val text = context.assets.open("${AmyVideoPack.ASSET_DIR}/$file")
                .bufferedReader().use { it.readText() }
            AmyVideoPack.parseAccentsJson(text)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Amy's video figure for her solo spots (v1.0.70 pilot): a
 * dedicated muted ExoPlayer decoding the green-screen clip
 * (the pack's MP4s are video-only streams) in hardware,
 * feeding a [GLSurfaceView] whose chroma-key shader
 * cuts the green out as it draws — figure and floor
 * reflection in the SAME GL pass off the SAME texture (never
 * a second player FOR THE SAME FIGURE — the renderer's two
 * slots are two passes of one figure, see below), inside the
 * same placement the frame system's solo figure occupies:
 * the spotlight pool and the contact shadow are the stage's
 * own Compose modifiers drawn under the surface, her feet
 * rest on the same baseline, and the reflection band below
 * them is [reflectionDepthDp] deep, fading from
 * [reflectionTopAlpha] at the baseline — StageFloor's law,
 * re-implemented in the shader's fade attribute. Inside that
 * placement the video box is sized by [VIDEO_FIGURE_SCALE]
 * (v1.0.71) and each clip is drawn at its measured
 * figureScale INSIDE the GL pass (v1.0.73 — see
 * [AmyVideoSizing]; the box itself never resizes mid-song).
 *
 * The song drives everything, as with frames: [playing]
 * mirrors the music (pause freezes the video mid-frame), and
 * in [AmyVideoMode.DANCE] the song position maps through the
 * beat-sliced pass plan — per-segment speeds are set at
 * segment boundaries, each pass re-syncs at its first beat,
 * and drift is bent back by a temporary speed nudge (never a
 * mid-pass seek, v1.0.71).
 *
 * THE SWITCH (v1.0.73). The pilot swapped ONE player's media
 * item at every pass boundary, and the device showed both
 * halves of that bargain: a flash (the load/prepare/first-
 * frame gap, compounded by the box resizing to the new
 * clip's figureScale) and passes that never swapped at all
 * (the player's own STATE_ENDED kept winning the race
 * against the song-position chain check, re-seeking the
 * same clip — the user's 重复动作）. The dance now PING-
 * PONGS: two players, two GL textures. While the front
 * player dances its pass, the back player has the NEXT
 * clip of the song's [DanceRotation] loaded, seeked to its
 * pass start and paused on its first frame. At the boundary
 * the back player simply starts and the renderer crossfades
 * the textures over [CROSSFADE_MS] — no media swap on the
 * visible player, no load gap, no clear frame, no resize.
 * The outgoing player then becomes the preload slot for the
 * following clip (reloaded only after the fade, while its
 * texture is invisible). Rotation is shuffle-with-
 * exhaustion held by the CALLER (see [DanceRotation]), so
 * the walk survives this figure entirely. The specials keep
 * the single-player path on slot 0; the second player is
 * parked whenever the mode is not DANCE.
 *
 * Any failure — a player error, or no first frame within the
 * watchdog window — reports through [onVideoError] once; the
 * caller falls the whole session back to Amy's frame
 * animation (the audio tier philosophy: degrade once, stay
 * degraded, never flap mid-party).
 */
@Composable
fun AmyVideoFigure(
    mode: AmyVideoMode,
    clip: AmyClip,
    chainPool: List<AmyClip> = emptyList(),
    danceRotation: DanceRotation? = null,
    songPositionMs: Long,
    playing: Boolean,
    beatPeriodS: Double,
    heightDp: Dp,
    reflectionDepthDp: Dp,
    reflectionTopAlpha: Float,
    status: AmyVideoStatus,
    onVideoError: () -> Unit,
    modifier: Modifier = Modifier,
    globalAlpha: Float = 1f,
    spotlightAlphaScale: Float = 1f,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val errorState = rememberUpdatedState(onVideoError)
    val playingState = rememberUpdatedState(playing)
    val positionState = rememberUpdatedState(songPositionMs)

    val glView = remember { AmyGlView(context) }
    // Slot 0's player: every mode's workhorse, and the dance's
    // first front player. Slot 1's player exists ONLY for the
    // dance ping-pong (the preload / next front).
    val playerA = remember {
        ExoPlayer.Builder(context).build().apply { volume = 0f }
    }
    val playerB = remember {
        ExoPlayer.Builder(context).build().apply { volume = 0f }
    }

    // The GL surface's two textures, once the renderer has
    // created them (recreated with the GL context — the
    // players are simply re-pointed at the fresh surfaces).
    var videoSurfaces by remember {
        mutableStateOf<List<Surface?>>(listOf(null, null))
    }
    DisposableEffect(glView) {
        glView.onTexturesReady = { textures ->
            videoSurfaces = textures.map { Surface(it) }
        }
        glView.onResume()
        onDispose {
            glView.onTexturesReady = null
            glView.onPause()
        }
    }
    LaunchedEffect(playerA, videoSurfaces[0]) {
        videoSurfaces[0]?.let { playerA.setVideoSurface(it) }
    }
    LaunchedEffect(playerB, videoSurfaces[1]) {
        videoSurfaces[1]?.let { playerB.setVideoSurface(it) }
    }
    DisposableEffect(playerA, playerB) {
        onDispose {
            playerA.release()
            playerB.release()
        }
    }

    // Errors on EITHER player fall the session back to frames.
    DisposableEffect(playerA, playerB) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                errorState.value.invoke()
            }
        }
        playerA.addListener(listener)
        playerB.addListener(listener)
        onDispose {
            playerA.removeListener(listener)
            playerB.removeListener(listener)
        }
    }

    // ---- dance ping-pong state (v1.0.73) ----
    // Keyed by the song (mode + the pick's name), never by
    // the walking clip: a chain swap must not reset the walk
    // it belongs to. The walk itself lives in the caller's
    // [DanceRotation]; [frontClip] only mirrors where it
    // currently stands, and re-initializes FROM the rotation
    // if this figure is rebuilt mid-song.
    var frontSlot by remember(mode, clip.name) { mutableStateOf(0) }
    var frontClip by remember(mode, clip.name) {
        mutableStateOf(danceRotation?.current() ?: clip)
    }
    var backClip by remember(mode, clip.name) { mutableStateOf<AmyClip?>(null) }
    var passStartMs by remember(mode, clip.name, beatPeriodS) {
        mutableStateOf<Long?>(null)
    }
    // Ticks until the post-swap preload of the following clip
    // into the (now invisible) old front player; -1 = none
    // pending. The reload waits out the crossfade so the old
    // texture's frozen last frame can finish fading.
    var backLoadTicksLeft by remember(mode, clip.name) { mutableStateOf(-1) }
    // When the back player's current load began (back
    // watchdog: a preload that never becomes READY is a
    // failure, exactly like a front that never starts).
    var backLoadedUptimeMs by remember(mode, clip.name) { mutableStateOf(0L) }

    val frontPlayer = if (frontSlot == 0) playerA else playerB
    val backPlayer = if (frontSlot == 0) playerB else playerA

    // The front clip's beat-sliced plan (the specials never
    // read it). Rebuilt when the walking clip or the song's
    // measured tempo changes.
    val frontPlan = remember(frontClip.name, beatPeriodS) {
        BeatSliceScheduler.plan(frontClip.accentsS, frontClip.durationS, beatPeriodS)
    }
    val backPlan = remember(backClip?.name, beatPeriodS) {
        backClip?.let {
            BeatSliceScheduler.plan(it.accentsS, it.durationS, beatPeriodS)
        }
    }

    // ---- specials: the single-player path (unchanged) ----
    LaunchedEffect(mode, clip.name) {
        if (mode == AmyVideoMode.DANCE) return@LaunchedEffect
        status.mode = mode
        status.clipName = clip.name
        status.speed = 1f
        glView.showSingleSlot(0)
        // The preload player has no job outside the dance.
        playerB.clearMediaItems()
        playerA.repeatMode = Player.REPEAT_MODE_ONE
        playerA.setPlaybackSpeed(1f)
        playerA.setMediaItem(MediaItem.fromUri("asset:///${clip.assetPath}"))
        playerA.prepare()
        if (playingState.value) playerA.play()
    }

    // ---- dance entry: front on slot 0, next clip preloaded ----
    LaunchedEffect(mode, clip.name) {
        if (mode != AmyVideoMode.DANCE) return@LaunchedEffect
        frontSlot = 0
        val start = danceRotation?.current() ?: clip
        frontClip = start
        passStartMs = null
        backLoadTicksLeft = -1
        glView.showSingleSlot(0)
        status.mode = mode
        status.clipName = start.name
        glView.setSlotScale(0, AmyVideoSizing.slotScale(start))
        glView.setSlotFeet(0, AmyVideoSizing.slotFeetV(start))
        playerA.repeatMode = Player.REPEAT_MODE_OFF
        playerA.setMediaItem(MediaItem.fromUri("asset:///${start.assetPath}"))
        playerA.prepare()
        val next = danceRotation?.peekNext()
            ?: AmyVideoPack.chainNext(chainPool, start.name)
        backClip = next
        if (next != null) {
            val plan = BeatSliceScheduler.plan(
                next.accentsS, next.durationS, beatPeriodS,
            )
            glView.setSlotScale(1, AmyVideoSizing.slotScale(next))
            glView.setSlotFeet(1, AmyVideoSizing.slotFeetV(next))
            playerB.repeatMode = Player.REPEAT_MODE_OFF
            playerB.setMediaItem(MediaItem.fromUri("asset:///${next.assetPath}"))
            playerB.prepare()
            if (plan.segments.isNotEmpty()) {
                playerB.seekTo((plan.segments.first().clipStartS * 1000).toLong())
            }
            playerB.pause()
            backLoadedUptimeMs = SystemClock.uptimeMillis()
        } else {
            backLoadedUptimeMs = 0L
        }
    }

    // Slot 1 draws only while the dance ping-pong owns it;
    // the specials' slot-0 scale rides the clip (the dance
    // sets its slots' scales at load time, while they are
    // invisible or already correct).
    LaunchedEffect(mode, clip.name) {
        glView.setSlotEnabled(1, mode == AmyVideoMode.DANCE)
        if (mode != AmyVideoMode.DANCE) {
            glView.setSlotScale(0, AmyVideoSizing.slotScale(clip))
            glView.setSlotFeet(0, AmyVideoSizing.slotFeetV(clip))
        }
    }

    // First-frame watchdog on slot 0's player: a local asset
    // that cannot produce a frame in this window never will
    // — fall back. (The dance's later fronts are covered by
    // the steering loop's stall check and the back watchdog.)
    LaunchedEffect(mode, clip.name) {
        var waitedMs = 0L
        while (true) {
            val state = playerA.playbackState
            if (state == Player.STATE_READY || state == Player.STATE_ENDED) break
            if (waitedMs >= FIRST_FRAME_WATCHDOG_MS) {
                errorState.value.invoke()
                break
            }
            kotlinx.coroutines.delay(250)
            waitedMs += 250
        }
    }

    // Play / pause rides the music — the dance's FRONT player,
    // or slot 0's player for the specials. The back player is
    // always paused except for the moment a swap starts it.
    LaunchedEffect(mode, frontSlot, playing) {
        val target = if (mode == AmyVideoMode.DANCE) {
            if (frontSlot == 0) playerA else playerB
        } else {
            playerA
        }
        if (playing) target.play() else target.pause()
    }

    // ---- dance steering: the front player dances the plan;
    // at each pass boundary the preloaded back player takes
    // over under a crossfade (v1.0.73). ----
    LaunchedEffect(mode, frontSlot, frontClip.name, beatPeriodS) {
        if (mode != AmyVideoMode.DANCE) return@LaunchedEffect
        var lastSongPosMs = -1L
        var lastVideoPosMs = -1L
        var frozenSongAdvanceMs = 0L
        while (true) {
            // The post-swap preload: once the crossfade has
            // run out, the old front's texture is invisible —
            // only then may its player be reloaded with the
            // FOLLOWING clip (the rotation's new peek).
            if (backLoadTicksLeft > 0) {
                backLoadTicksLeft--
                if (backLoadTicksLeft == 0) {
                    val following = backClip
                    if (following != null) {
                        val plan = BeatSliceScheduler.plan(
                            following.accentsS, following.durationS, beatPeriodS,
                        )
                        glView.setSlotScale(
                            1 - frontSlot, AmyVideoSizing.slotScale(following),
                        )
                        glView.setSlotFeet(
                            1 - frontSlot, AmyVideoSizing.slotFeetV(following),
                        )
                        backPlayer.setMediaItem(
                            MediaItem.fromUri("asset:///${following.assetPath}"),
                        )
                        backPlayer.prepare()
                        if (plan.segments.isNotEmpty()) {
                            backPlayer.seekTo(
                                (plan.segments.first().clipStartS * 1000).toLong(),
                            )
                        }
                        backPlayer.pause()
                        backLoadedUptimeMs = SystemClock.uptimeMillis()
                    }
                }
            }
            // Back watchdog: a preload that never becomes
            // READY is a stuck decoder — fall back rather
            // than swap to a black texture at the boundary.
            if (backClip != null && backLoadedUptimeMs > 0L &&
                backPlayer.playbackState != Player.STATE_READY &&
                SystemClock.uptimeMillis() - backLoadedUptimeMs >
                FIRST_FRAME_WATCHDOG_MS
            ) {
                errorState.value.invoke()
                break
            }
            if (playingState.value && frontPlan.segments.isNotEmpty()) {
                val posMs = positionState.value
                val periodMs = beatPeriodS * 1000.0
                val passDurationMs = frontPlan.durationS(beatPeriodS) * 1000.0
                val started = passStartMs
                val passEnded = started != null && posMs >= started + passDurationMs
                val frontEnded =
                    frontPlayer.playbackState == Player.STATE_ENDED
                if (started != null && (passEnded || frontEnded)) {
                    // THE BOUNDARY. The next clip is already
                    // loaded on the back player: start it and
                    // crossfade — the visible player is never
                    // asked to load anything mid-song.
                    val incoming = backClip
                    if (incoming != null &&
                        backPlayer.playbackState == Player.STATE_READY
                    ) {
                        frontPlayer.pause()
                        val inPlan = backPlan
                        if (inPlan != null && inPlan.segments.isNotEmpty()) {
                            backPlayer.setPlaybackSpeed(
                                inPlan.segments.first().speed.toFloat(),
                            )
                        }
                        if (playingState.value) backPlayer.play()
                        glView.beginCrossfade(1 - frontSlot)
                        danceRotation?.advance()
                        passStartMs = if (passEnded) {
                            (started + passDurationMs).toLong()
                        } else {
                            (floor(posMs / periodMs) * periodMs).toLong()
                        }
                        frontClip = incoming
                        frontSlot = 1 - frontSlot
                        status.clipName = incoming.name
                        backClip = danceRotation?.peekNext()
                            ?: AmyVideoPack.chainNext(chainPool, incoming.name)
                        backLoadedUptimeMs = 0L
                        backLoadTicksLeft =
                            if (backClip != null) BACK_LOAD_DELAY_TICKS else -1
                        break
                    }
                    // No ready back (a one-clip pool, or a
                    // preload still preparing): loop the front
                    // pass, as the pilot did — the song never
                    // waits on a load.
                    passStartMs = if (passEnded) {
                        (started + passDurationMs).toLong()
                    } else {
                        (floor(posMs / periodMs) * periodMs).toLong()
                    }
                    frontPlayer.seekTo(
                        (frontPlan.segments.first().clipStartS * 1000).toLong(),
                    )
                    if (playingState.value) frontPlayer.play()
                }
                var start = passStartMs
                if (start == null || posMs < start - PASS_RESYNC_TOLERANCE_MS) {
                    // (Re)start the pass on the song's beat
                    // grid — beats fall on whole multiples of
                    // the period from position 0, the same
                    // grid PoseCycle dances to — so accents
                    // land on beats from the very first one.
                    start = (floor(posMs / periodMs) * periodMs).toLong()
                    passStartMs = start
                    frontPlayer.seekTo(
                        (frontPlan.segments.first().clipStartS * 1000).toLong(),
                    )
                }
                val located = frontPlan.locate((posMs - start) / 1000.0, beatPeriodS)
                if (located != null) {
                    // One speed write per actual change: the
                    // segment's sliced speed, bent by the
                    // drift nudge while the video sits off
                    // the plan. v1.0.71: the nudge REPLACES
                    // the pilot's mid-pass seek — on a weak
                    // decoder a seek is a flush plus a
                    // keyframe hunt (a visible stutter),
                    // while a few percent of speed for a
                    // second is invisible and closes the
                    // same drift. Seeks now happen only at
                    // pass starts and on the preload slot,
                    // where they are invisible either way.
                    val segmentSpeed = located.segment.speed.toFloat()
                    val expectedMs = (located.clipPositionS * 1000).toLong()
                    val driftMs = frontPlayer.currentPosition - expectedMs
                    val nudge = when {
                        driftMs > DRIFT_NUDGE_DEADBAND_MS -> 1f - DRIFT_NUDGE_FRACTION
                        driftMs < -DRIFT_NUDGE_DEADBAND_MS -> 1f + DRIFT_NUDGE_FRACTION
                        else -> 1f
                    }
                    val appliedSpeed = segmentSpeed * nudge
                    if (abs(frontPlayer.playbackParameters.speed - appliedSpeed) > 0.01f) {
                        frontPlayer.setPlaybackSpeed(appliedSpeed)
                    }
                    status.speed = appliedSpeed
                    // Stall check: the song is advancing but
                    // the FRONT video position is frozen solid
                    // while the player claims to be ready —
                    // treat it as a decoder stall and fall
                    // back. (The back player is intentionally
                    // paused and never stall-checked.)
                    if (frontPlayer.playbackState == Player.STATE_READY &&
                        frontPlayer.isPlaying
                    ) {
                        if (lastSongPosMs >= 0 && posMs > lastSongPosMs &&
                            abs(frontPlayer.currentPosition - lastVideoPosMs) <=
                            STALL_VIDEO_EPSILON_MS
                        ) {
                            frozenSongAdvanceMs += posMs - lastSongPosMs
                            if (frozenSongAdvanceMs >= STALL_SONG_ADVANCE_MS) {
                                errorState.value.invoke()
                            }
                        } else {
                            frozenSongAdvanceMs = 0L
                        }
                        lastSongPosMs = posMs
                        lastVideoPosMs = frontPlayer.currentPosition
                    }
                }
            }
            kotlinx.coroutines.delay(SYNC_TICK_MS)
        }
    }

    // Render parameters ride composition (the invite step
    // grows the figure; the ceremony fades it).
    glView.setRenderParams(
        reflectionDepthFraction =
            if (heightDp.value > 0f) reflectionDepthDp.value / heightDp.value else 0f,
        reflectionTopAlpha = reflectionTopAlpha,
        globalAlpha = globalAlpha,
    )

    val frameWidthDp = SpotlightGeom.dancerWidthDp(heightDp.value).dp
    // v1.0.73: the video box NEVER resizes mid-song — it is
    // the scale-1 box (VIDEO_FIGURE_SCALE off the caller's
    // height, the wider VIDEO_CANVAS_ASPECT so a scaled-up
    // clip's widest gesture still fits inside the canvas),
    // and each clip's figureScale is applied to its texture
    // inside the GL pass (see AmyVideoSizing), feet on the
    // same baseline via the same offset. In v1.0.72 the
    // per-clip factor multiplied THIS box, so every chain
    // switch relaid out the AndroidView (and churned its
    // surface) at the very moment the eye was watching. The
    // outer box (the zone's reservation) and the pool/shadow
    // inside it keep the unscaled geometry — they key off
    // the zone, not the figure.
    val videoHeightDp = heightDp * VIDEO_FIGURE_SCALE
    val videoWidthDp = (videoHeightDp.value * VIDEO_CANVAS_ASPECT).dp
    val videoReflectionDepthDp = reflectionDepthDp * VIDEO_FIGURE_SCALE
    Box(
        modifier = modifier.width(frameWidthDp).height(heightDp + reflectionDepthDp),
    ) {
        // The pool and the contact shadow: the frame system's
        // own draw modifiers, in its own order (light, then
        // the dark the feet press into it), under the surface
        // — the keyed-out pixels let them show through.
        Box(
            modifier = Modifier
                .spotlightPool(alphaScale = globalAlpha * spotlightAlphaScale)
                .contactShadow(alphaScale = globalAlpha)
                .width(frameWidthDp)
                .height(heightDp)
                .align(Alignment.TopCenter),
        )
        AndroidView(
            factory = { glView },
            modifier = Modifier
                .width(videoWidthDp)
                .height(videoHeightDp + videoReflectionDepthDp)
                .align(Alignment.TopCenter)
                .offset(y = heightDp - videoHeightDp),
        )
    }
}

/**
 * The video figure's size relative to the frame figure
 * (v1.0.71): chosen so Amy's STANDING height on stage is
 * the same on video as on frames. Measured off the shipped
 * art: her standing frame PNG's figure fills 502/600 =
 * 0.837 of its box height, while in the video frame her
 * standing figure (the idle clip — the pack's unified
 * baseline) fills ≈0.879 of the frame height: the wide-shot
 * clips carry more headroom, so the video box runs at
 * 0.952 of the frame box to land her at the same on-stage
 * height. (A first flat guess of 0.88 overshot — she read
 * visibly smaller than frame Amy.)
 * Applied inside [AmyVideoFigure] to the figure box only:
 * the height scales by this factor, the GL reflection
 * scales in the same unit, and the figure is offset so her
 * feet stay on the zone's foot baseline. The frame dancers
 * keep their size exactly, and the spotlight pool / contact
 * shadow geometry is NOT scaled (they key off the zone).
 * Tune this number after a look on the real stage.
 *
 * v1.0.72 multiplied each clip's own measured figureScale
 * into this box as well; v1.0.73 keeps this constant as the
 * only box factor and moves the per-clip factor into the
 * GL draw (see [AmyVideoSizing]) — same rendered figure
 * heights, but the box (and its surface) no longer resizes
 * at chain switches.
 */
const val VIDEO_FIGURE_SCALE = 0.952f

/** The runtime clips' pixel size (v1.0.71): they ship
 *  downscaled to this from the 704×1104 pack masters — the
 *  stage displays her at roughly this size, and the smaller
 *  frames are what the weak tablet decoder can chew. The
 *  masters on disk stay untouched. */
const val VIDEO_CLIP_WIDTH_PX = 512
const val VIDEO_CLIP_HEIGHT_PX = 800

/** The runtime clips' aspect, [VIDEO_CLIP_WIDTH_PX] /
 *  [VIDEO_CLIP_HEIGHT_PX]. */
const val VIDEO_CLIP_ASPECT = 512f / 800f

/**
 * The GL canvas's aspect (v1.0.73): wider than the clips'
 * own aspect, so a clip drawn at its figureScale (up to
 * 1.242 for slow-08) keeps even its widest gesture inside
 * the canvas — measured off the shipped assets, the worst
 * case (fast-01: figure extent 0.922 of the frame width ×
 * scale 1.163 = 1.072) stays inside the canvas's 1.125
 * headroom (0.72 ÷ 0.64). The canvas still fits the dancer
 * zone: 0.952 × 0.72 ≈ 0.685 of the figure height, under
 * the zone's 0.78. The texture is drawn centred in the
 * canvas; the side margins are keyed-out transparency.
 */
const val VIDEO_CANVAS_ASPECT = 0.72f

/** The dance switch's crossfade (v1.0.73): long enough to
 *  hide the choreography cut, short enough to stay a cut. */
const val CROSSFADE_MS = 150L

/** Ticks of the 50 ms sync loop the outgoing player waits
 *  after a swap before it is reloaded as the next preload
 *  (5 ticks = 250 ms > the 150 ms crossfade). */
private const val BACK_LOAD_DELAY_TICKS = 5

/** How long the first frame may take before the session
 *  falls back to frames (local assets: generous). */
private const val FIRST_FRAME_WATCHDOG_MS = 8_000L

/** The dance sync loop's tick. */
private const val SYNC_TICK_MS = 50L

/** Drift inside this deadband is left alone; past it the
 *  speed nudge takes over (v1.0.71 — there is no mid-pass
 *  seek any more). */
private const val DRIFT_NUDGE_DEADBAND_MS = 100L

/** How hard the drift nudge bends the segment speed
 *  (8%: closes ~80ms of drift per second, invisibly). */
private const val DRIFT_NUDGE_FRACTION = 0.08f

/** A song position this far BEFORE the pass start means the
 *  pass plan no longer applies (a seek backwards). */
private const val PASS_RESYNC_TOLERANCE_MS = 300L

/** Video movement at or below this counts as frozen for the
 *  stall check. */
private const val STALL_VIDEO_EPSILON_MS = 20L

/** Song advance with a frozen video that counts as a stall. */
private const val STALL_SONG_ADVANCE_MS = 1_500L

/**
 * The keying surface: a [GLSurfaceView] with an alpha-capable
 * config, sitting ON TOP of the window's own surface
 * (z-order on top) so the Compose-drawn pool and shadow
 * beneath the dancer zone show through wherever the shader
 * keys the green away. The renderer draws figure + reflection
 * in one pass per texture slot; the surface's pixels outside
 * the figure are fully transparent.
 *
 * The z-order choice is load-bearing (v1.0.71): the pilot
 * shipped with the media-overlay z-order call, which moves
 * the surface into the compositor's media-overlay layer —
 * on the user's Note10+ that layer is a hardware overlay
 * plane that composites WITHOUT per-pixel alpha, so every
 * keyed pixel displayed as its raw RGB (the clear colour:
 * opaque black) and Amy danced inside a solid black
 * rectangle. Z-order on top keeps the surface in the normal
 * blended layer stack, where the compositor honours the
 * per-pixel alpha the shader writes. (The default z-order
 * would be wrong the other way: the surface would sit
 * BEHIND the window behind a punched hole, and the pool and
 * shadow drawn into the window inside that hole would be
 * erased by the punch.) The rest of the transparency chain
 * was already correct and is unchanged: an RGBA8888 EGL
 * config, a TRANSLUCENT holder format, a (0,0,0,0) clear,
 * and the shader writing a real alpha per pixel.
 */
class AmyGlView(context: Context) : GLSurfaceView(context) {

    private val renderer = ChromaKeyRenderer { textures ->
        onTexturesReady?.invoke(textures)
    }

    /** Fires (on the GL thread) whenever the renderer has
     *  fresh SurfaceTextures for the players to draw into:
     *  index 0 is the specials / first-front slot, index 1
     *  the dance preload slot. */
    var onTexturesReady: ((Array<SurfaceTexture>) -> Unit)? = null

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setRenderParams(
        reflectionDepthFraction: Float,
        reflectionTopAlpha: Float,
        globalAlpha: Float,
    ) {
        renderer.reflectionDepthFraction = reflectionDepthFraction
        renderer.reflectionTopAlpha = reflectionTopAlpha
        renderer.globalAlpha = globalAlpha
    }

    /** The GL draw scale for one texture slot (the clip's
     *  measured figureScale, via [AmyVideoSizing]). Set when
     *  a clip is loaded into the slot — while it is
     *  invisible, or already correct. Queued onto the GL
     *  thread: the renderer reads the plain arrays there. */
    fun setSlotScale(slot: Int, scale: Float) {
        queueEvent { renderer.setSlotScale(slot, scale) }
    }

    /** The foot-baseline v for one texture slot (the
     *  clip's measured feetV, via [AmyVideoSizing]): the
     *  renderer anchors the slot's sampling on the feet
     *  row so the boots meet the stage baseline and their
     *  reflection. Set with the slot's scale, at load. */
    fun setSlotFeet(slot: Int, feetV: Float) {
        queueEvent { renderer.setSlotFeet(slot, feetV) }
    }

    /** Whether a slot may be drawn at all (slot 1 only
     *  exists while the dance ping-pong owns it). */
    fun setSlotEnabled(slot: Int, enabled: Boolean) {
        queueEvent { renderer.setSlotEnabled(slot, enabled) }
    }

    /** Show exactly one slot, cancelling any crossfade. */
    fun showSingleSlot(slot: Int) {
        renderer.showSingleSlot(slot)
    }

    /** Begin the [CROSSFADE_MS] texture crossfade to [slot]
     *  (v1.0.73): the dance's pass-boundary switch. */
    fun beginCrossfade(slot: Int) {
        renderer.beginCrossfade(slot)
    }
}

/**
 * The chroma-key renderer: samples the video textures, cuts
 * the green screen by chroma distance (luma-independent, so
 * shadows on the backdrop don't tear), despills the fringe,
 * and draws each figure's mirror under the foot baseline in
 * the same pass — the reflection is the same texture sampled
 * back upward, faded by a per-vertex alpha that melts from
 * [reflectionTopAlpha] at the baseline to nothing at the
 * band's bottom edge (StageFloor's reflection law).
 *
 * v1.0.73: TWO texture slots for the dance ping-pong. Each
 * slot has its own SurfaceTexture, transform matrix and
 * draw scale (its clip's figureScale — the figure is scaled
 * around the foot baseline inside the draw, so the view
 * itself never resizes). A crossfade blends the outgoing
 * slot down and the incoming slot up over [CROSSFADE_MS];
 * outside a fade exactly one slot is visible.
 */
private class ChromaKeyRenderer(
    private val onTexturesReady: (Array<SurfaceTexture>) -> Unit,
) : GLSurfaceView.Renderer {

    @Volatile var reflectionDepthFraction: Float = 0f
    @Volatile var reflectionTopAlpha: Float = 0.75f
    @Volatile var globalAlpha: Float = 1f

    private val slotScales = floatArrayOf(1f, 1f)
    // Per-slot foot baselines (texture v of the feet row):
    // the wide-shot clips keep a green margin below the
    // boots, so each slot's sampling is anchored on its
    // own clip's measured feet row (v1.0.73). Defaults to
    // the pack median until a clip load sets it.
    private val slotFeet = floatArrayOf(
        AmyVideoSizing.DEFAULT_FEET_V.toFloat(),
        AmyVideoSizing.DEFAULT_FEET_V.toFloat(),
    )
    // Slot 0 is the workhorse (specials + first dance front)
    // and always draws; slot 1 exists only while the dance
    // ping-pong owns it.
    private val slotEnabled = booleanArrayOf(true, false)

    // Crossfade state (touched from the main thread via the
    // setters, resolved on the GL thread each frame).
    @Volatile private var frontSlot = 0
    @Volatile private var fadeToSlot = -1
    @Volatile private var fadeStartUptimeMs = 0L

    fun setSlotScale(slot: Int, scale: Float) {
        slotScales[slot] = scale
    }

    fun setSlotFeet(slot: Int, feetV: Float) {
        slotFeet[slot] = feetV
    }

    fun setSlotEnabled(slot: Int, enabled: Boolean) {
        slotEnabled[slot] = enabled
    }

    fun showSingleSlot(slot: Int) {
        frontSlot = slot
        fadeToSlot = -1
    }

    fun beginCrossfade(toSlot: Int) {
        if (toSlot == frontSlot || toSlot == fadeToSlot) return
        fadeToSlot = toSlot
        fadeStartUptimeMs = SystemClock.uptimeMillis()
    }

    /** This frame's alpha for [slot], resolving (and
     *  completing) any running crossfade. GL thread only. */
    private fun slotAlpha(slot: Int, nowMs: Long): Float {
        val fadeTo = fadeToSlot
        if (fadeTo < 0) return if (slot == frontSlot) 1f else 0f
        val t = ((nowMs - fadeStartUptimeMs).toFloat() / CROSSFADE_MS)
            .coerceIn(0f, 1f)
        if (t >= 1f) {
            frontSlot = fadeTo
            fadeToSlot = -1
            return if (slot == frontSlot) 1f else 0f
        }
        return when (slot) {
            fadeTo -> t
            frontSlot -> 1f - t
            else -> 0f
        }
    }

    private var program = 0
    private val textureIds = IntArray(SLOT_COUNT)
    private val surfaceTextures = arrayOfNulls<SurfaceTexture>(SLOT_COUNT)
    private val textureMatrices = Array(SLOT_COUNT) { FloatArray(16) }
    private val vertices: FloatBuffer = ByteBuffer
        .allocateDirect(VERTEX_COUNT * FLOATS_PER_VERTEX * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    // v1.0.71: the vertex data lives in a preallocated array
    // (the pilot allocated a fresh FloatArray every frame —
    // pure GC churn on the weak tablet), and the shader
    // locations are looked up ONCE when the program is built
    // (the pilot re-queried all seven every frame).
    private val vertexData = FloatArray(VERTEX_COUNT * FLOATS_PER_VERTEX)
    private var uTexLoc = -1
    private var uTexMatrixLoc = -1
    private var uKeyColorLoc = -1
    private var uGlobalAlphaLoc = -1
    private var aPosLoc = -1
    private var aTexLoc = -1
    private var aFadeLoc = -1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        uTexLoc = GLES20.glGetUniformLocation(program, "uTex")
        uTexMatrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uKeyColorLoc = GLES20.glGetUniformLocation(program, "uKeyColor")
        uGlobalAlphaLoc = GLES20.glGetUniformLocation(program, "uGlobalAlpha")
        aPosLoc = GLES20.glGetAttribLocation(program, "aPos")
        aTexLoc = GLES20.glGetAttribLocation(program, "aTex")
        aFadeLoc = GLES20.glGetAttribLocation(program, "aFade")
        val textures = IntArray(SLOT_COUNT)
        GLES20.glGenTextures(SLOT_COUNT, textures, 0)
        val ready = Array(SLOT_COUNT) { slot ->
            textureIds[slot] = textures[slot]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureIds[slot])
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR,
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR,
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE,
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE,
            )
            surfaceTextures[slot]?.release()
            val texture = SurfaceTexture(textureIds[slot])
            texture.setDefaultBufferSize(CLIP_WIDTH_PX, CLIP_HEIGHT_PX)
            surfaceTextures[slot] = texture
            texture
        }
        onTexturesReady(ready)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return
        val nowMs = SystemClock.uptimeMillis()
        // Outgoing slot first, incoming over it — during a
        // crossfade both are mid-blend; outside one, exactly
        // one slot has alpha 1 and the other is skipped.
        val drawOrder = if (fadeToSlot >= 0) {
            listOf(frontSlot, fadeToSlot)
        } else {
            listOf(frontSlot, 1 - frontSlot)
        }
        for (slot in drawOrder) {
            if (!slotEnabled[slot]) continue
            val alpha = slotAlpha(slot, nowMs)
            if (alpha <= 0f) continue
            val texture = surfaceTextures[slot] ?: continue
            drawSlot(slot, texture, alpha)
        }
    }

    /** Draw one slot's figure + reflection, keyed, at the
     *  slot's own figure scale around the foot baseline. */
    private fun drawSlot(slot: Int, texture: SurfaceTexture, slotAlpha: Float) {
        texture.updateTexImage()
        texture.getTransformMatrix(textureMatrices[slot])

        // Geometry in NDC: the view is figure height +
        // reflection depth tall; the figure fills the top
        // figure-height share at scale 1. The slot's scale
        // grows/shrinks the figure around the foot baseline
        // (its bottom edge), and the reflection mirrors the
        // SCALED figure into the fixed band below the
        // baseline — it samples proportionally less of the
        // texture (depthFraction ÷ scale), so its fade still
        // melts to zero exactly at the band's bottom edge.
        val depthFraction = reflectionDepthFraction.coerceAtLeast(0f)
        val figureShare = 1f / (1f + depthFraction)
        val baselineY = 1f - 2f * figureShare
        val topAlpha = reflectionTopAlpha
        val scale = slotScales[slot]
        // Feet anchoring (v1.0.73): the clip's feet sit
        // slotFeet[slot] above the texture's bottom edge
        // (green margin below the boots), so the sampling
        // window shifts up by that much — the feet row
        // lands exactly ON the baseline, and the reflection
        // mirrors from the same row instead of from the
        // texture's bottom. The window's span is unchanged
        // (v runs feet..feet+1; past 1 clamps to the top
        // backdrop row, which keys out), so the figure's
        // on-stage size does not move — she only stops
        // floating above her own reflection.
        val feet = slotFeet[slot]
        // The texture is drawn centred in the wider canvas:
        // its half-extent in NDC is the aspect ratio of
        // canvas to texture, times the slot scale.
        val halfExtent = (VIDEO_CLIP_ASPECT / VIDEO_CANVAS_ASPECT) * scale
        val figureTop = baselineY + 2f * figureShare * scale
        val bandBottom = baselineY - 2f * (1f - figureShare)
        val reflectionV = if (scale > 0f) depthFraction / scale else depthFraction
        // (x, y, u, v, fade) per vertex, triangle strips:
        // figure first (fade 1 throughout), then its mirror —
        // v sampled back up from the FEET row at the
        // baseline, alpha melting to zero at the band's
        // bottom edge.
        val data = vertexData
        var i = 0
        // figure: BL, BR, TL, TR
        data[i++] = -halfExtent; data[i++] = baselineY; data[i++] = 0f; data[i++] = feet; data[i++] = 1f
        data[i++] = halfExtent; data[i++] = baselineY; data[i++] = 1f; data[i++] = feet; data[i++] = 1f
        data[i++] = -halfExtent; data[i++] = figureTop; data[i++] = 0f; data[i++] = feet + 1f; data[i++] = 1f
        data[i++] = halfExtent; data[i++] = figureTop; data[i++] = 1f; data[i++] = feet + 1f; data[i++] = 1f
        // reflection: BL, BR, TL, TR (TL/TR sit ON the
        // baseline, at the figure's FEET row)
        data[i++] = -halfExtent; data[i++] = bandBottom; data[i++] = 0f; data[i++] = feet + reflectionV; data[i++] = 0f
        data[i++] = halfExtent; data[i++] = bandBottom; data[i++] = 1f; data[i++] = feet + reflectionV; data[i++] = 0f
        data[i++] = -halfExtent; data[i++] = baselineY; data[i++] = 0f; data[i++] = feet; data[i++] = topAlpha
        data[i++] = halfExtent; data[i++] = baselineY; data[i++] = 1f; data[i++] = feet; data[i++] = topAlpha
        vertices.position(0)
        vertices.put(data)
        vertices.position(0)

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureIds[slot])
        GLES20.glUniform1i(uTexLoc, 0)
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, textureMatrices[slot], 0)
        GLES20.glUniform3f(uKeyColorLoc, KEY_RED, KEY_GREEN, KEY_BLUE)
        GLES20.glUniform1f(uGlobalAlphaLoc, globalAlpha * slotAlpha)
        val stride = FLOATS_PER_VERTEX * 4
        GLES20.glEnableVertexAttribArray(aPosLoc)
        GLES20.glVertexAttribPointer(aPosLoc, 2, GLES20.GL_FLOAT, false, stride, vertices)
        vertices.position(2)
        GLES20.glEnableVertexAttribArray(aTexLoc)
        GLES20.glVertexAttribPointer(aTexLoc, 2, GLES20.GL_FLOAT, false, stride, vertices)
        vertices.position(4)
        GLES20.glEnableVertexAttribArray(aFadeLoc)
        GLES20.glVertexAttribPointer(aFadeLoc, 1, GLES20.GL_FLOAT, false, stride, vertices)
        vertices.position(0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        if (depthFraction > 0f) {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 4, 4)
        }
        GLES20.glDisableVertexAttribArray(aPosLoc)
        GLES20.glDisableVertexAttribArray(aTexLoc)
        GLES20.glDisableVertexAttribArray(aFadeLoc)
    }

    private fun buildProgram(vertexSource: String, fragmentSource: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            return shader
        }
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, vertexSource))
        GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource))
        GLES20.glLinkProgram(program)
        return program
    }

    companion object {
        /** The renderer's texture slots: 0 = specials /
         *  first dance front, 1 = dance preload / next
         *  front (v1.0.73). */
        const val SLOT_COUNT = 2

        /** The runtime frame size (every clip ships at
         *  [VIDEO_CLIP_WIDTH_PX]×[VIDEO_CLIP_HEIGHT_PX] —
         *  downscaled from the 704×1104 pack masters,
         *  v1.0.71). */
        const val CLIP_WIDTH_PX = VIDEO_CLIP_WIDTH_PX
        const val CLIP_HEIGHT_PX = VIDEO_CLIP_HEIGHT_PX

        /** The packaged green, #0af20a, per channel — the
         *  single source is [ChromaKeyMath], whose port of
         *  this shader's math the key gate test runs against
         *  the shipped assets (v1.0.72). */
        const val KEY_RED = ChromaKeyMath.KEY_RED
        const val KEY_GREEN = ChromaKeyMath.KEY_GREEN
        const val KEY_BLUE = ChromaKeyMath.KEY_BLUE

        const val FLOATS_PER_VERTEX = 5
        const val VERTEX_COUNT = 8

        const val VERTEX_SHADER = """
            attribute vec2 aPos;
            attribute vec2 aTex;
            attribute float aFade;
            uniform mat4 uTexMatrix;
            varying vec2 vTex;
            varying float vFade;
            void main() {
                vTex = (uTexMatrix * vec4(aTex, 0.0, 1.0)).xy;
                vFade = aFade;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTex;
            varying float vFade;
            uniform samplerExternalOES uTex;
            uniform vec3 uKeyColor;
            uniform float uGlobalAlpha;
            vec2 chroma(vec3 c) {
                float y = dot(c, vec3(0.299, 0.587, 0.114));
                return vec2((c.b - y) * 0.564, (c.r - y) * 0.713);
            }
            void main() {
                vec4 texel = texture2D(uTex, vTex);
                vec3 c = texel.rgb;
                float d = distance(chroma(c), chroma(uKeyColor));
                float a = smoothstep(0.20, 0.34, d);
                float excess = c.g - max(c.r, c.b);
                if (excess > 0.0) {
                    c.g -= excess * 0.7 * (1.0 - a * 0.6);
                }
                gl_FragColor = vec4(c, a * vFade * uGlobalAlpha);
            }
        """
    }
}
