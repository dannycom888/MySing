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
        // v1.0.71: the video figure (and its GL reflection,
        // same unit) is sized so her standing height equals
        // the frame figure's (0.837 / 0.879 ≈ 0.952 — the
        // derivation lives on the constant), offset so her
        // feet stay on the zone baseline; her width follows
        // the clip's own aspect; the pool/shadow are not
        // scaled.
        assertTrue(src.contains("const val VIDEO_FIGURE_SCALE = 0.952f"))
        assertTrue(src.contains("heightDp * VIDEO_FIGURE_SCALE"))
        assertTrue(src.contains("reflectionDepthDp * VIDEO_FIGURE_SCALE"))
        assertTrue(src.contains("videoHeightDp.value * VIDEO_CLIP_ASPECT"))
        assertTrue(src.contains("offset(y = heightDp - videoHeightDp)"))
    }

    @Test
    fun `the gl stage keys the packaged green off one texture`() {
        val src = videoStage()
        assertTrue(src.contains("GL_TEXTURE_EXTERNAL_OES"))
        assertTrue(src.contains("updateTexImage()"))
        assertTrue(src.contains("setVideoSurface"))
        // One player, released with the composition.
        assertTrue(src.contains("ExoPlayer.Builder(context)"))
        assertTrue(src.contains("player.release()"))
        // The key colour is the packaged green #0af20a.
        assertTrue(src.contains("KEY_RED = 0.039f"))
        assertTrue(src.contains("KEY_GREEN = 0.949f"))
        assertTrue(src.contains("KEY_BLUE = 0.039f"))
        assertTrue(src.contains("smoothstep(0.20, 0.34, d)"))
        // The reflection is the same texture in the same
        // pass — never a second player.
        assertTrue(src.contains("reflectionTopAlpha"))
        assertEqualsCount(src, "ExoPlayer.Builder", 1)
    }

    @Test
    fun `the dance is steered by the beat-sliced plan with a session fallback`() {
        val src = videoStage()
        // v1.0.71: the plan is built on the ACTIVE clip (the
        // song's pick first, then the chain's) — the pin's
        // needle follows the rename.
        assertTrue(src.contains("BeatSliceScheduler.plan(activeClip.accentsS, activeClip.durationS, beatPeriodS)"))
        assertTrue(src.contains("override fun onPlayerError"))
        assertTrue(src.contains("FIRST_FRAME_WATCHDOG_MS"))
        assertTrue(src.contains("Player.REPEAT_MODE_ONE"))
        // The pool and shadow are the stage's own modifiers.
        assertTrue(src.contains(".spotlightPool("))
        assertTrue(src.contains(".contactShadow("))
    }

    @Test
    fun `the dance chains clips at pass boundaries and never seeks mid-pass`() {
        val src = videoStage()
        val main = mainActivity()
        // v1.0.71: the song's tempo pool is stored at pick
        // time and handed to the solo figure…
        assertTrue(main.contains("amyDancePool = pool"))
        assertTrue(main.contains("chainPool = amyDancePool"))
        // …which swaps the active clip when a pass completes.
        assertTrue(src.contains("AmyVideoPack.chainNext(chainPool, activeClip.name)"))
        assertTrue(src.contains("activeClip = chained"))
        // Drift is a speed nudge now; the ONLY seek left in
        // the driver is the pass-start seek.
        assertTrue(src.contains("DRIFT_NUDGE_FRACTION"))
        assertTrue(src.contains("segmentSpeed * nudge"))
        assertEqualsCount(src, "player.seekTo(", 1)
    }

    private fun assertEqualsCount(text: String, needle: String, expected: Int) {
        val count = text.split(needle).size - 1
        assertTrue("expected $expected × \"$needle\", found $count", count == expected)
    }
}
