package com.dannycom888.shieldkaraoke.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.0.40 structural pin (rewrites the v1.0.38 pin, whose
 * "every build carries the processor" invariant WAS the
 * regression): PlayerController must keep TWO different player
 * builds — the first attempt through the renderers factory that
 * carries the shared vocal processor, the error retry a stock
 * bare build (DefaultRenderersFactory, no custom sink, no
 * processor). On the user's device the custom-sink build fails
 * first-try, so the bare retry is the only reason songs played
 * at all; v1.0.38 unified the builds and playback died outright
 * (sink=plain-failed on a real device). PlayerController needs
 * an Android Context, so the mounting invariant is pinned on
 * the source, the same file-reading style as the asset tests.
 */
class PlayerControllerStructureTest {

    private fun source(): String {
        val candidates = listOf(
            File("src/main/java/com/dannycom888/shieldkaraoke/audio/PlayerController.kt"),
            File("app/src/main/java/com/dannycom888/shieldkaraoke/audio/PlayerController.kt"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("PlayerController.kt not found; tried ${candidates.map { it.absolutePath }}")
        return file.readText()
    }

    @Test
    fun firstBuildCarriesProcessorRetryBuildIsBare() {
        val src = source()
        assertTrue(
            "the first-attempt build must inject the shared vocal processor via the custom factory",
            src.contains("ExoPlayer.Builder(context, KaraokeRenderersFactory(context, vocalProcessor))"),
        )
        assertTrue(
            "the retry build must be a stock bare DefaultRenderersFactory build (no custom sink, no processor)",
            src.contains("ExoPlayer.Builder(context, DefaultRenderersFactory(context)).build()"),
        )
        assertEquals(
            "exactly two ExoPlayer.Builder call sites — the custom first build and the bare retry, no third build may sneak in",
            2,
            src.split("ExoPlayer.Builder(").size - 1,
        )
        assertTrue(
            "the build dispatch must key on customSink",
            src.contains("buildPlayer(customSink: Boolean)"),
        )
        assertTrue(
            "a fresh load must return to the custom build",
            src.contains("rebuildPlayer(customSink = true)"),
        )
        assertTrue(
            "the error path must rebuild bare",
            src.contains("rebuildPlayer(customSink = false)"),
        )
        assertFalse(
            "the v1.0.38 single-build unification must stay gone",
            src.contains("One build for both paths") || src.contains("no processor-less build any more"),
        )
    }

    @Test
    fun accompanimentDegradationIsPathOrProcessorTruth() {
        val src = source()
        assertTrue(
            "degradation must count the bare retry path as a real cause (its chain has no processor)",
            src.contains("fallback.path != FallbackStateMachine.Path.CUSTOM"),
        )
        assertTrue(
            "degradation must also key on the processor's configured/active truth for custom-path format rejections",
            src.contains("vocalProcessor.configured && !vocalProcessor.isActive()"),
        )
    }
}
