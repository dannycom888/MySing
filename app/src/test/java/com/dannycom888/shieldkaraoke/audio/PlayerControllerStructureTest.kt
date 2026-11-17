package com.dannycom888.shieldkaraoke.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.0.40 structural pin, extended in v1.0.58 (it rewrites the
 * v1.0.38 pin, whose "every build carries the processor"
 * invariant WAS the regression): PlayerController keeps a ladder
 * of DIFFERENT player builds — the first attempt through the
 * renderers factory that carries the shared vocal processor,
 * the plain retry a stock bare build (DefaultRenderersFactory,
 * no custom sink, no processor), and since v1.0.58 a third rung,
 * BARE: a stock build with float output off and no
 * LoudnessEnhancer. On the user's devices the custom-sink build
 * fails first-try, so the plainer rungs are the only reason
 * songs play at all; v1.0.38 unified the builds and playback
 * died outright (sink=plain-failed on a real device).
 * PlayerController needs an Android Context, so the mounting
 * invariants are pinned on the source, the same file-reading
 * style as the asset tests.
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
    fun ladderBuildsKeepTheirDistinctCompositions() {
        val src = source()
        assertTrue(
            "the custom build must inject the shared vocal processor via the custom factory",
            src.contains("ExoPlayer.Builder(context, KaraokeRenderersFactory(context, vocalProcessor))"),
        )
        assertTrue(
            "the plain retry build must stay the stock bare DefaultRenderersFactory build (no custom sink, no processor) — its v1.0.40 composition is not to be touched",
            src.contains("ExoPlayer.Builder(context, DefaultRenderersFactory(context)).build()"),
        )
        assertTrue(
            "the BARE build goes through its own factory",
            src.contains("ExoPlayer.Builder(context, BareRenderersFactory(context)).build()"),
        )
        assertEquals(
            "exactly three ExoPlayer.Builder call sites — one per ladder rung, no fourth build may sneak in",
            3,
            src.split("ExoPlayer.Builder(").size - 1,
        )
        assertTrue(
            "the build dispatch must key on the ladder path",
            src.contains("buildPlayer(path: FallbackStateMachine.Path)"),
        )
        assertFalse(
            "the v1.0.38 single-build unification must stay gone",
            src.contains("One build for both paths") || src.contains("no processor-less build any more"),
        )
    }

    @Test
    fun bareFactoryForcesFloatOffAndMountsNoProcessor() {
        val src = source()
        val bareFactory = src.substringAfter("private class BareRenderersFactory")
            .substringBefore("private val fallback")
        assertTrue(
            "BARE must force float output off — the float/AudioTrack layer is a prime tablet suspect",
            bareFactory.contains(".setEnableFloatOutput(false)"),
        )
        assertFalse(
            "BARE must never mount audio processors",
            bareFactory.contains("setAudioProcessors"),
        )
        assertFalse(
            "BARE must never see the vocal processor",
            bareFactory.contains("vocalProcessor"),
        )
    }

    @Test
    fun barePathNeverAttachesTheLoudnessEnhancer() {
        val src = source()
        val applyGain = src.substringAfter("private fun applyGain(")
            .substringBefore("/** Apply a freshly measured loudness gain")
        assertTrue(
            "applyGain must branch on the BARE path before any enhancer work (the enhancer hangs off load, retry and async re-measure alike)",
            applyGain.contains("if (fallback.path == FallbackStateMachine.Path.BARE)"),
        )
        assertTrue(
            "the BARE branch comes first: no LoudnessEnhancer may be constructed before it returns",
            applyGain.indexOf("Path.BARE") < applyGain.indexOf("LoudnessEnhancer(sessionId)"),
        )
    }

    @Test
    fun stickyLadderWiring() {
        val src = source()
        assertTrue(
            "a fresh load starts at the state machine's sticky floor and rebuilds only when the live build differs",
            src.contains("val startPath = fallback.onNewLoad()") &&
                src.contains("if (builtPath != startPath)"),
        )
        assertTrue(
            "the error handler rebuilds at the rung the state machine stepped down to",
            src.contains("rebuildPlayer(fallback.path)"),
        )
        assertFalse(
            "the pre-v1.0.58 climb-back-to-custom on every load must stay gone",
            src.contains("rebuildPlayer(customSink = true)") ||
                src.contains("rebuildPlayer(customSink = false)"),
        )
    }

    @Test
    fun lastErrorKeepsTheCauseChain() {
        val src = source()
        assertTrue(
            "handleError must render the error through PlaybackErrorText so the cause chain survives into the diagnostics line",
            src.contains("PlaybackErrorText.describe(error.errorCodeName, error)"),
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
