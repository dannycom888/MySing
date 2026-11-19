package com.dannycom888.shieldkaraoke.video

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
 *    pass, floating over the window as a media overlay;
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
    fun `the gl stage keys the packaged green off one texture`() {
        val src = videoStage()
        assertTrue(src.contains("GL_TEXTURE_EXTERNAL_OES"))
        assertTrue(src.contains("setZOrderMediaOverlay(true)"))
        assertTrue(src.contains("PixelFormat.TRANSLUCENT"))
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
        assertTrue(src.contains("BeatSliceScheduler.plan(clip.accentsS, clip.durationS, beatPeriodS)"))
        assertTrue(src.contains("override fun onPlayerError"))
        assertTrue(src.contains("FIRST_FRAME_WATCHDOG_MS"))
        assertTrue(src.contains("player.setPlaybackSpeed(speed)"))
        assertTrue(src.contains("Player.REPEAT_MODE_ONE"))
        // The pool and shadow are the stage's own modifiers.
        assertTrue(src.contains(".spotlightPool("))
        assertTrue(src.contains(".contactShadow("))
    }

    private fun assertEqualsCount(text: String, needle: String, expected: Int) {
        val count = text.split(needle).size - 1
        assertTrue("expected $expected × \"$needle\", found $count", count == expected)
    }
}
