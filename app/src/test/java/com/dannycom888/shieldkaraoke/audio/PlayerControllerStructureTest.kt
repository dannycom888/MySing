package com.dannycom888.shieldkaraoke.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.0.38 structural pin: EVERY ExoPlayer build in
 * PlayerController must go through the renderers factory that
 * carries the shared vocal processor — first attempt and plain
 * retry alike. Pre-v1.0.38 the retry was built with the bare
 * default builder (no processor), so accompaniment silently
 * stopped working on every song that fell back; the user's
 * "原唱和伴奏没什么区别" report traced to exactly that.
 * PlayerController needs an Android Context, so the mounting
 * invariant is pinned on the source, the same file-reading
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
    fun everyPlayerBuildCarriesTheVocalProcessor() {
        val src = source()
        assertTrue(
            "the single player build must inject the shared vocal processor",
            src.contains("ExoPlayer.Builder(context, KaraokeRenderersFactory(context, vocalProcessor))"),
        )
        assertEquals(
            "exactly one ExoPlayer.Builder call site — no second, processor-less build may sneak back",
            1,
            src.split("ExoPlayer.Builder(").size - 1,
        )
        assertFalse(
            "no bare default-builder player may exist",
            src.contains("ExoPlayer.Builder(context).build()"),
        )
    }

    @Test
    fun accompanimentDegradationKeysOnProcessorTruth() {
        val src = source()
        assertTrue(
            "degradation must be computed from the processor's configured/active state",
            src.contains("vocalProcessor.configured && !vocalProcessor.isActive()"),
        )
        assertFalse(
            "the old path-based pretence (plain retry = no processor) must stay gone",
            src.contains("Plain retry sink has no vocal processor"),
        )
    }
}
