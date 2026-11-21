package com.dannycom888.shieldkaraoke.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.0.70: source pins for the Amy video pilot's wiring, in
 * the house style — the pilot's safety properties live in
 * Android / Compose / GL code the JVM tests cannot run, so
 * they are pinned on the source instead:
 *
 *  - the video figure exists ONLY behind the Amy gate
 *    ([MainActivity.amyVideoLiveFor] — Amy's key, pack
 *    loaded, no session fallback), at her three solo spots
 *    (song stage, ceremony keeper, idle stage);
 *  - any video error sets the session fallback;
 *  - the diagnostics line carries the video= field;
 *  - the GL stage keys the packaged green off a
 *    SurfaceTexture with the reflection drawn in the same
 *    pass, floating over the window in the blended layer
 *    stack (z-order on top — NEVER the media overlay, whose
 *    hardware plane drops per-pixel alpha and showed Amy in
 *    a black rectangle on the user's phone, v1.0.71);
 *  - the dance is steered by the beat-sliced plan.
 */
class AmyVideoWiringTest {

    private fun readMain(path: String): String {
        val candidates = listOf(File("src/main/$path"), File("app/src/main/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("$path not found; tried ${candidates.map { it.absolutePath }}")
        return file.readText()
    }

    private fun mainActivity(): String =
        readMain("java/com/dannycom888/shieldkaraoke/ui/MainActivity.kt")

    private fun videoStage(): String =
        readMain("java/com/dannycom888/shieldkaraoke/video/AmyVideoStage.kt")

    @Test
    fun `the video gate is amy-only and falls back for the session`() {
        val src = mainActivity()
        assertTrue(src.contains("private fun amyVideoLiveFor(dancerKey: String?): Boolean"))
        assertTrue(src.contains("dancerKey == DancerKeys.MODERN && !amyVideoFallback"))
        assertTrue(src.contains("amyVideoCatalogue.isNotEmpty()"))
        // Every video spot hands its errors to the one
        // session fallback flag.
        assertTrue(src.contains("onVideoError = { amyVideoFallback = true }"))
        assertEqualsCount(src, "AmyVideoFigure(", 3)
    }

    @Test
    fun `the catalogue loads once in onCreate and the clip pick lives in startSong`() {
        val src = mainActivity()
        assertTrue(src.contains("amyVideoCatalogue = try {"))
        assertTrue(src.contains("loadAmyCatalogue(this)"))
        assertTrue(src.contains("AmyVideoPack.pickNext("))
        assertTrue(src.contains("AmyVideoPack.dancePool(amyVideoCatalogue, currentBpm)"))
        assertTrue(src.contains("videoLastClipName = pick.name"))
        assertTrue(src.contains("videoRotation++"))
    }

    @Test
    fun `the solo stage maps phases to video modes`() {
        val src = mainActivity()
        assertTrue(src.contains("DancerActivity.Phase.INVITING -> AmyVideoMode.INVITE"))
        assertTrue(src.contains("DancerActivity.Phase.DANCING -> AmyVideoMode.DANCE"))
        assertTrue(src.contains("DancerActivity.Phase.OUTRO_HOLD -> AmyVideoMode.DANCE"))
        assertTrue(src.contains("DancerActivity.Phase.STILL -> AmyVideoMode.IDLE"))
        // The outro hold freezes the dance on its frame.
        assertTrue(
            src.contains("dancerPhase != DancerActivity.Phase.OUTRO_HOLD"),
        )
        // The keeper invites / idles; the idle stage idles.
        assertTrue(src.contains("Ceremony.Phase.SOLO_INVITE"))
        assertTrue(src.contains("AmyVideoMode.IDLE"))
    }

    @Test
    fun `diagnostics carry the video field`() {
        val src = mainActivity()
        assertTrue(src.contains("append(\" · video=\")"))
        assertTrue(src.contains("amyVideoFallback -> \"fallback\""))
        assertTrue(src.contains("amyVideoCatalogue.isEmpty() -> \"off\""))
        assertTrue(src.contains("amyVideoStatus.clipName"))
        assertTrue(src.contains("amyVideoStatus.speed"))
    }

    @Test
    fun `the video surface is set up for per-pixel transparency`() {
        val src = videoStage()
        // The full chain, every link required (v1.0.71): an
        // RGBA8888 EGL config, a TRANSLUCENT holder, a clear
        // to alpha 0, the shader writing a real per-pixel
        // alpha — and the surface in the BLENDED layer stack
        // (z-order on top). The media overlay is forbidden:
        // its hardware plane composites without per-pixel
        // alpha, which is exactly how v1.0.70 showed Amy
        // inside a solid black rectangle on the user's phone.
        assertTrue(src.contains("setEGLConfigChooser(8, 8, 8, 8, 16, 0)"))
        assertTrue(src.contains("PixelFormat.TRANSLUCENT"))
        assertTrue(src.contains("setZOrderOnTop(true)"))
        assertFalse(src.contains("setZOrderMediaOverlay"))
        assertTrue(src.contains("glClearColor(0f, 0f, 0f, 0f)"))
        assertTrue(src.contains("vec4(c, a * vFade * uGlobalAlpha)"))
    }

    @Test
    fun `the video figure matches the frame figure's standing height`() {
        val src = videoStage()
        // v1.0.71: the video box is sized so her standing
        // height equals the frame figure's (0.837 / 0.879 ≈
        // 0.952 — the derivation lives on the constant),
        // offset so her feet stay on the zone baseline; the
        // pool/shadow are not scaled.
        // v1.0.73: the box is the SCALE-1 box on the wider
        // canvas aspect and never resizes mid-song — the
        // per-clip factor moved into the GL draw (see the
        // slot-scale test below), so this pin no longer
        // expects the clip aspect or a per-clip multiplier
        // in the box math.
        assertTrue(src.contains("const val VIDEO_FIGURE_SCALE = 0.952f"))
        assertTrue(src.contains("heightDp * VIDEO_FIGURE_SCALE"))
        assertTrue(src.contains("reflectionDepthDp * VIDEO_FIGURE_SCALE"))
        assertTrue(src.contains("videoHeightDp.value * VIDEO_CANVAS_ASPECT"))
        assertTrue(src.contains("offset(y = heightDp - videoHeightDp)"))
        assertFalse(src.contains("VIDEO_FIGURE_SCALE * clipFigureScale"))
    }

    @Test
    fun `the gl stage keys the packaged green off the slot textures`() {
        val src = videoStage()
        assertTrue(src.contains("GL_TEXTURE_EXTERNAL_OES"))
        assertTrue(src.contains("updateTexImage()"))
        assertTrue(src.contains("setVideoSurface"))
        // v1.0.73: TWO players exist, but only for the dance
        // ping-pong — each figure still renders off its own
        // single texture in a single pass; the second player
        // is the NEXT clip preloading, never a second view
        // of the same figure. Both released with the
        // composition.
        assertTrue(src.contains("ExoPlayer.Builder(context)"))
        assertTrue(src.contains("playerA.release()"))
        assertTrue(src.contains("playerB.release()"))
        // The key colour is the packaged green #0af20a,
        // sourced from ChromaKeyMath (v1.0.72) so the JVM
        // port the key gate runs and the shader uniform can
        // never disagree about it.
        assertTrue(src.contains("KEY_RED = ChromaKeyMath.KEY_RED"))
        assertTrue(src.contains("KEY_GREEN = ChromaKeyMath.KEY_GREEN"))
        assertTrue(src.contains("KEY_BLUE = ChromaKeyMath.KEY_BLUE"))
        assertTrue(src.contains("smoothstep(0.20, 0.34, d)"))
        // The reflection is the same texture in the same
        // pass as its figure.
        assertTrue(src.contains("reflectionTopAlpha"))
        assertEqualsCount(src, "ExoPlayer.Builder", 2)
    }

    @Test
    fun `the dance is steered by the beat-sliced plan with a session fallback`() {
        val src = videoStage()
        // v1.0.73: the plan is built on the FRONT clip (the
        // rotation's current — the song's pick first, then
        // the walk's) — the pin's needle follows the rename.
        assertTrue(src.contains("BeatSliceScheduler.plan(frontClip.accentsS, frontClip.durationS, beatPeriodS)"))
        assertTrue(src.contains("override fun onPlayerError"))
        assertTrue(src.contains("FIRST_FRAME_WATCHDOG_MS"))
        assertTrue(src.contains("Player.REPEAT_MODE_ONE"))
        // The pool and shadow are the stage's own modifiers.
        assertTrue(src.contains(".spotlightPool("))
        assertTrue(src.contains(".contactShadow("))
    }

    @Test
    fun `the dance rotates by shuffle-with-exhaustion and swaps by ping-pong crossfade`() {
        val src = videoStage()
        val main = mainActivity()
        // The song's tempo pool and its rotation are created
        // at pick time in startSong and handed to the solo
        // figure — the walk lives in MainActivity state, not
        // in the figure's remembered state (v1.0.73).
        assertTrue(main.contains("amyDancePool = pool"))
        assertTrue(main.contains("chainPool = amyDancePool"))
        assertTrue(main.contains("amyDanceRotation = DanceRotation(pool, pick)"))
        assertTrue(main.contains("danceRotation = amyDanceRotation"))
        // The driver consults the rotation: the front starts
        // on its current, the preload is its peek, and the
        // boundary advances it.
        assertTrue(src.contains("danceRotation?.current() ?: clip"))
        assertTrue(src.contains("danceRotation?.peekNext()"))
        assertTrue(src.contains("danceRotation?.advance()"))
        // The switch itself: the preloaded back player is
        // started and the renderer crossfades the textures —
        // the visible player never swaps its media item at a
        // boundary (setMediaItem on the front happens only
        // at dance entry, keyed by the song).
        assertTrue(src.contains("glView.beginCrossfade(1 - frontSlot)"))
        assertTrue(src.contains("const val CROSSFADE_MS = 150L"))
        assertTrue(src.contains("backPlayer.playbackState == Player.STATE_READY"))
        // Drift is a speed nudge; seeks exist ONLY at pass
        // starts (front) and on the preload slot (back /
        // entry) — never mid-pass on the visible player.
        assertTrue(src.contains("DRIFT_NUDGE_FRACTION"))
        assertTrue(src.contains("segmentSpeed * nudge"))
        assertEqualsCount(src, "frontPlayer.seekTo(", 2)
        assertEqualsCount(src, "backPlayer.seekTo(", 1)
        assertEqualsCount(src, "playerB.seekTo(", 1)
        assertEqualsCount(src, "player.seekTo(", 0)
    }

    @Test
    fun `the measured tempo rebuilds the dance pool mid-song`() {
        val main = mainActivity()
        // v1.0.76: the tempo callback in startSong — the one
        // place a measured BPM lands — rebuilds the pool and
        // the rotation when the measurement implies the
        // other pool, anchored on the old walk's peek (the
        // clip loaded for the next boundary).
        assertTrue(main.contains("AmyVideoPack.poolNeedsRebuild("))
        assertTrue(main.contains("walk.peekNext() ?: walk.current()"))
        assertTrue(main.contains("amyDanceRotation = DanceRotation(amyDancePool, anchor)"))
        // The rebuild stays inside the measurement callback:
        // startSong's birth sites plus exactly this one
        // rebuild site — no other place rewrites the walk.
        assertEqualsCount(main, "amyDancePool = AmyVideoPack.dancePool(", 1)
        assertEqualsCount(main, "amyDanceRotation = DanceRotation(", 2)
    }

    @Test
    fun `a slot is drawn only after its current load rendered a first frame`() {
        val src = videoStage()
        // v1.0.78: the black box (cold open ~2s, every song
        // start ~1s) — the draw loop sampled the front
        // slot's SurfaceTexture from the surface's first
        // frame, well before the player's first real frame
        // landed; an unsampled texture reads black, whose
        // chroma is far from the key green, so the shader
        // painted the quad opaque. The gate is PER LOAD,
        // not per texture: after a media swap the texture
        // can still hold the previous clip's last frame, so
        // the driver closes the gate at EVERY load (the
        // specials' setMediaItem, the dance entry's front
        // and preload loads, and the post-swap preload
        // reload — four sites), and only the new item's
        // first rendered frame opens it: the player's
        // onRenderedFirstFrame, with the texture's
        // frame-available callback as the second signal.
        // The draw loop multiplies the slot alpha by the
        // pure gate factor (SlotFrameGate — 0 before the
        // first frame, fading in after it); one loop serves
        // the specials' single slot and the dance's
        // ping-pong slots alike.
        assertTrue(src.contains("setOnFrameAvailableListener"))
        assertTrue(src.contains("slotFirstFrameUptimeMs"))
        assertTrue(src.contains("override fun onRenderedFirstFrame()"))
        assertTrue(src.contains("glView.markSlotFrameRendered(slot)"))
        assertTrue(src.contains("SlotFrameGate.alphaFactor("))
        assertTrue(src.contains("drawSlot(slot, texture, alpha * gate)"))
        assertEqualsCount(src, "glView.resetSlotFrameGate(", 4)
    }

    @Test
    fun `the key gate port matches the shader it mirrors`() {
        // v1.0.72: ChromaKeyMath is the shader's JVM twin —
        // its constants must stay the shader's constants, or
        // AmyVideoKeyGateTest gates on the wrong math.
        val math = readMain("java/com/dannycom888/shieldkaraoke/video/ChromaKeyMath.kt")
        assertTrue(math.contains("const val KEY_RED = 0.039f"))
        assertTrue(math.contains("const val KEY_GREEN = 0.949f"))
        assertTrue(math.contains("const val KEY_BLUE = 0.039f"))
        assertTrue(math.contains("const val RAMP_LOW = 0.20f"))
        assertTrue(math.contains("const val RAMP_HIGH = 0.34f"))
        // The GLSL ramp in the stage is the same 0.20–0.34.
        assertTrue(videoStage().contains("smoothstep(0.20, 0.34, d)"))
    }

    @Test
    fun `each texture slot is drawn at its clip's measured factor`() {
        val src = videoStage()
        // v1.0.73: the per-clip factor is applied INSIDE the
        // GL draw, per texture slot, through the production
        // sizing computation (AmyVideoSizing — the same one
        // AmyVideoSizingTest exercises): the dance front at
        // entry, the preload at entry and at its post-swap
        // reload, and the specials' slot. The renderer scales
        // the figure around the foot baseline by the slot's
        // factor, in the wider canvas.
        assertTrue(src.contains("AmyVideoSizing.slotScale(start)"))
        assertTrue(src.contains("AmyVideoSizing.slotScale(next)"))
        assertTrue(src.contains("AmyVideoSizing.slotScale(following)"))
        assertTrue(src.contains("AmyVideoSizing.slotScale(clip)"))
        assertTrue(src.contains("VIDEO_CLIP_ASPECT / VIDEO_CANVAS_ASPECT"))
        assertTrue(src.contains("depthFraction / scale"))
        // The v1.0.72 Compose-box multiplication is gone —
        // the box no longer resizes at a chain switch.
        assertFalse(src.contains("clipFigureScale"))
        // v1.0.73 feet anchoring rides the same four load
        // sites: each slot's foot baseline is set with its
        // scale, and the draw anchors both the figure and
        // its reflection on that row (feet meet reflection
        // at the baseline — no float gap).
        assertTrue(src.contains("AmyVideoSizing.slotFeetV(start)"))
        assertTrue(src.contains("AmyVideoSizing.slotFeetV(next)"))
        assertTrue(src.contains("AmyVideoSizing.slotFeetV(following)"))
        assertTrue(src.contains("AmyVideoSizing.slotFeetV(clip)"))
        assertTrue(src.contains("slotFeet[slot]"))
        assertTrue(src.contains("feet + 1f"))
        assertTrue(src.contains("feet + reflectionV"))
    }

    private fun assertEqualsCount(text: String, needle: String, expected: Int) {
        val count = text.split(needle).size - 1
        assertTrue("expected $expected × \"$needle\", found $count", count == expected)
    }
}
