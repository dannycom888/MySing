package com.dannycom888.shieldkaraoke.video

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
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
 * Amy's video figure for her solo spots (v1.0.70 pilot): one
 * dedicated ExoPlayer decoding the green-screen clip (the
 * pack's MP4s are video-only streams) in hardware, feeding a
 * [GLSurfaceView] whose chroma-key shader
 * cuts the green out as it draws — figure and floor
 * reflection in the SAME GL pass off the SAME texture (never
 * a second player), inside the same placement the frame
 * system's solo figure occupies: the spotlight pool and the
 * contact shadow are the stage's own Compose modifiers drawn
 * under the surface, her feet rest on the same baseline, and
 * the reflection band below them is [reflectionDepthDp]
 * deep, fading from [reflectionTopAlpha] at the baseline —
 * StageFloor's law, re-implemented in the shader's fade
 * attribute. Inside that placement the video is sized by
 * [VIDEO_FIGURE_SCALE] (v1.0.71).
 *
 * The song drives everything, as with frames: [playing]
 * mirrors the music (pause freezes the video mid-frame), and
 * in [AmyVideoMode.DANCE] the song position maps through the
 * beat-sliced pass plan — per-segment speeds are set at
 * segment boundaries, each pass re-syncs at its first beat,
 * drift is bent back by a temporary speed nudge (never a
 * mid-pass seek, v1.0.71), and at each pass boundary the
 * dance CHAINS to the next clip of the song's tempo pool
 * (v1.0.71) instead of looping one clip all song long. The
 * specials simply loop at natural speed.
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
    val player = remember {
        ExoPlayer.Builder(context).build().apply { volume = 0f }
    }

    // v1.0.71: the clip actually dancing. In DANCE mode the
    // song starts on the caller's pick, then CHAINS through
    // [chainPool] — the sync loop below swaps this at every
    // pass boundary (never the same clip twice in a row), so
    // a 3–5 minute song sees varied choreography instead of
    // one short loop. The specials never chain (nothing ever
    // assigns this outside the dance steering).
    var activeClip by remember(clip.name) { mutableStateOf(clip) }

    // The GL surface, once the renderer has a SurfaceTexture.
    var videoSurface by remember { mutableStateOf<Surface?>(null) }
    DisposableEffect(glView) {
        glView.onSurfaceTextureReady = { texture -> videoSurface = Surface(texture) }
        glView.onResume()
        onDispose {
            glView.onSurfaceTextureReady = null
            glView.onPause()
        }
    }
    LaunchedEffect(player, videoSurface) {
        videoSurface?.let { player.setVideoSurface(it) }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // Errors fall the session back to frames.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                errorState.value.invoke()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Load the active clip for this mode. Dance passes are
    // steered by the sync loop below; the specials loop
    // natively. A chain swap re-runs this for the new clip.
    LaunchedEffect(player, mode, activeClip.name) {
        status.mode = mode
        status.clipName = activeClip.name
        player.repeatMode =
            if (mode == AmyVideoMode.DANCE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
        if (mode != AmyVideoMode.DANCE) {
            player.setPlaybackSpeed(1f)
            status.speed = 1f
        }
        player.setMediaItem(MediaItem.fromUri("asset:///${activeClip.assetPath}"))
        player.prepare()
        if (playingState.value) player.play()
    }

    // First-frame watchdog: a local asset that cannot produce
    // a frame in this window never will — fall back.
    LaunchedEffect(player, activeClip.name) {
        var waitedMs = 0L
        while (true) {
            val state = player.playbackState
            if (state == Player.STATE_READY || state == Player.STATE_ENDED) break
            if (waitedMs >= FIRST_FRAME_WATCHDOG_MS) {
                errorState.value.invoke()
                break
            }
            kotlinx.coroutines.delay(250)
            waitedMs += 250
        }
    }

    // Play / pause rides the music.
    LaunchedEffect(player, playing) {
        if (playing) player.play() else player.pause()
    }

    // Beat-sliced steering (dance mode only): keep the video
    // on the pass plan the song position dictates.
    val plan = remember(activeClip.name, beatPeriodS) {
        BeatSliceScheduler.plan(activeClip.accentsS, activeClip.durationS, beatPeriodS)
    }
    var passStartMs by remember(activeClip.name, beatPeriodS) { mutableStateOf<Long?>(null) }
    LaunchedEffect(player, mode, plan) {
        if (mode != AmyVideoMode.DANCE) return@LaunchedEffect
        var lastSongPosMs = -1L
        var lastVideoPosMs = -1L
        var frozenSongAdvanceMs = 0L
        while (true) {
            if (playingState.value && plan.segments.isNotEmpty()) {
                val posMs = positionState.value
                val periodMs = beatPeriodS * 1000.0
                val passDurationMs = plan.durationS(beatPeriodS) * 1000.0
                // v1.0.71: a completed pass CHAINS the next
                // clip of the song's tempo pool instead of
                // looping this one. Swapping the active clip
                // re-runs the load effect and rebuilds the
                // plan; the new clip's first pass starts on
                // the beat grid like every pass (below).
                val chained = passStartMs?.let { started ->
                    if (posMs >= started + passDurationMs) {
                        AmyVideoPack.chainNext(chainPool, activeClip.name)
                    } else {
                        null
                    }
                }
                if (chained != null) {
                    activeClip = chained
                    break
                }
                var start = passStartMs
                if (start == null || posMs < start - PASS_RESYNC_TOLERANCE_MS ||
                    posMs >= start + passDurationMs ||
                    player.playbackState == Player.STATE_ENDED
                ) {
                    // (Re)start the pass on the song's beat
                    // grid — beats fall on whole multiples of
                    // the period from position 0, the same
                    // grid PoseCycle dances to — so accents
                    // land on beats from the very first one.
                    start = (floor(posMs / periodMs) * periodMs).toLong()
                    passStartMs = start
                    player.seekTo(
                        (plan.segments.first().clipStartS * 1000).toLong(),
                    )
                }
                val located = plan.locate((posMs - start) / 1000.0, beatPeriodS)
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
                    // pass starts (the restart branch above).
                    val segmentSpeed = located.segment.speed.toFloat()
                    val expectedMs = (located.clipPositionS * 1000).toLong()
                    val driftMs = player.currentPosition - expectedMs
                    val nudge = when {
                        driftMs > DRIFT_NUDGE_DEADBAND_MS -> 1f - DRIFT_NUDGE_FRACTION
                        driftMs < -DRIFT_NUDGE_DEADBAND_MS -> 1f + DRIFT_NUDGE_FRACTION
                        else -> 1f
                    }
                    val appliedSpeed = segmentSpeed * nudge
                    if (abs(player.playbackParameters.speed - appliedSpeed) > 0.01f) {
                        player.setPlaybackSpeed(appliedSpeed)
                    }
                    status.speed = appliedSpeed
                    // Stall check: the song is advancing but
                    // the video position is frozen solid while
                    // the player claims to be ready — treat it
                    // as a decoder stall and fall back.
                    if (player.playbackState == Player.STATE_READY && player.isPlaying) {
                        if (lastSongPosMs >= 0 && posMs > lastSongPosMs &&
                            abs(player.currentPosition - lastVideoPosMs) <= STALL_VIDEO_EPSILON_MS
                        ) {
                            frozenSongAdvanceMs += posMs - lastSongPosMs
                            if (frozenSongAdvanceMs >= STALL_SONG_ADVANCE_MS) {
                                errorState.value.invoke()
                            }
                        } else {
                            frozenSongAdvanceMs = 0L
                        }
                        lastSongPosMs = posMs
                        lastVideoPosMs = player.currentPosition
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
    // The video figure is sized by VIDEO_FIGURE_SCALE (her
    // standing height lands exactly where the frame figure's
    // stands — see the constant), the GL reflection scales
    // in the same unit, and the figure is dropped by exactly
    // the height difference so her feet stay on the zone's
    // foot baseline. Her width follows the CLIP's own aspect
    // — the frame box's wider FRAME_ASPECT would stretch the
    // texture sideways; the frame system Fits its PNGs
    // undistorted, and so does the video now. The outer box
    // (the zone's reservation) and the pool/shadow inside it
    // keep the unscaled geometry — they key off the zone,
    // not the figure.
    val videoHeightDp = heightDp * VIDEO_FIGURE_SCALE
    val videoWidthDp = (videoHeightDp.value * VIDEO_CLIP_ASPECT).dp
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
 * 0.837 / 0.879 ≈ 0.952 of the frame box to land her at the
 * same on-stage height. (A first flat guess of 0.88
 * overshot — she read visibly smaller than frame Amy.)
 * Applied inside [AmyVideoFigure] to the figure box only:
 * the height scales by this factor, the width follows the
 * clip's own aspect, the GL reflection scales in the same
 * unit, and the figure is offset so her feet stay on the
 * zone's foot baseline. The frame dancers keep their size
 * exactly, and the spotlight pool / contact shadow geometry
 * is NOT scaled (they key off the zone). Tune this number
 * after a look on the real stage.
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
 * in one pass; the surface's pixels outside the figure are
 * fully transparent.
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

    private val renderer = ChromaKeyRenderer { texture ->
        onSurfaceTextureReady?.invoke(texture)
    }

    /** Fires (on the GL thread) whenever the renderer has a
     *  fresh SurfaceTexture for the player to draw into. */
    var onSurfaceTextureReady: ((SurfaceTexture) -> Unit)? = null

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
}

/**
 * The chroma-key renderer: samples the video texture, cuts
 * the green screen by chroma distance (luma-independent, so
 * shadows on the backdrop don't tear), despills the fringe,
 * and draws the figure's mirror under the foot baseline in
 * the same pass — the reflection is the same texture sampled
 * back upward, faded by a per-vertex alpha that melts from
 * [reflectionTopAlpha] at the baseline to nothing at the
 * band's bottom edge (StageFloor's reflection law).
 */
private class ChromaKeyRenderer(
    private val onTextureReady: (SurfaceTexture) -> Unit,
) : GLSurfaceView.Renderer {

    @Volatile var reflectionDepthFraction: Float = 0f
    @Volatile var reflectionTopAlpha: Float = 0.75f
    @Volatile var globalAlpha: Float = 1f

    private var program = 0
    private var textureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private val textureMatrix = FloatArray(16)
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
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
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
        surfaceTexture?.release()
        val texture = SurfaceTexture(textureId)
        texture.setDefaultBufferSize(CLIP_WIDTH_PX, CLIP_HEIGHT_PX)
        surfaceTexture = texture
        onTextureReady(texture)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val texture = surfaceTexture ?: return
        if (program == 0) return
        texture.updateTexImage()
        texture.getTransformMatrix(textureMatrix)

        // Geometry in NDC: the figure fills the view's width
        // and the top figure-height share of its height; the
        // reflection band sits under the foot baseline (the
        // view is figure height + reflection depth tall).
        val depthFraction = reflectionDepthFraction.coerceAtLeast(0f)
        val figureShare = 1f / (1f + depthFraction)
        val baselineY = 1f - 2f * figureShare
        val topAlpha = reflectionTopAlpha
        // (x, y, u, v, fade) per vertex, triangle strips:
        // figure first (fade 1 throughout), then its mirror —
        // v sampled back up from the baseline by the depth
        // fraction of the texture, alpha melting to zero.
        val data = vertexData
        var i = 0
        // figure: BL, BR, TL, TR
        data[i++] = -1f; data[i++] = baselineY; data[i++] = 0f; data[i++] = 0f; data[i++] = 1f
        data[i++] = 1f; data[i++] = baselineY; data[i++] = 1f; data[i++] = 0f; data[i++] = 1f
        data[i++] = -1f; data[i++] = 1f; data[i++] = 0f; data[i++] = 1f; data[i++] = 1f
        data[i++] = 1f; data[i++] = 1f; data[i++] = 1f; data[i++] = 1f; data[i++] = 1f
        // reflection: BL, BR, TL, TR (TL/TR sit ON the
        // baseline, at the figure's own bottom edge)
        data[i++] = -1f; data[i++] = -1f; data[i++] = 0f; data[i++] = depthFraction; data[i++] = 0f
        data[i++] = 1f; data[i++] = -1f; data[i++] = 1f; data[i++] = depthFraction; data[i++] = 0f
        data[i++] = -1f; data[i++] = baselineY; data[i++] = 0f; data[i++] = 0f; data[i++] = topAlpha
        data[i++] = 1f; data[i++] = baselineY; data[i++] = 1f; data[i++] = 0f; data[i++] = topAlpha
        vertices.position(0)
        vertices.put(data)
        vertices.position(0)

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(uTexLoc, 0)
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, textureMatrix, 0)
        GLES20.glUniform3f(uKeyColorLoc, KEY_RED, KEY_GREEN, KEY_BLUE)
        GLES20.glUniform1f(uGlobalAlphaLoc, globalAlpha)
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
        /** The runtime frame size (every clip ships at
         *  [VIDEO_CLIP_WIDTH_PX]×[VIDEO_CLIP_HEIGHT_PX] —
         *  downscaled from the 704×1104 pack masters,
         *  v1.0.71). */
        const val CLIP_WIDTH_PX = VIDEO_CLIP_WIDTH_PX
        const val CLIP_HEIGHT_PX = VIDEO_CLIP_HEIGHT_PX

        /** The packaged green, #0af20a, per channel. */
        const val KEY_RED = 0.039f
        const val KEY_GREEN = 0.949f
        const val KEY_BLUE = 0.039f

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
