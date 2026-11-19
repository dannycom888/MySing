package com.dannycom888.shieldkaraoke.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.0.70: the Amy video pack catalogue — accent-JSON
 * parsing, the shipped pack's completeness, tempo pools and
 * the no-immediate-repeat rotation. The completeness test
 * reads the SHIPPED assets, so a re-pack that drops or
 * breaks a clip fails here before it can fail on the TV.
 */
class AmyVideoPackTest {

    private fun assetDir(): File {
        val candidates = listOf(
            File("src/main/assets/${AmyVideoPack.ASSET_DIR}"),
            File("app/src/main/assets/${AmyVideoPack.ASSET_DIR}"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: throw AssertionError("amy_video assets not found; tried ${candidates.map { it.absolutePath }}")
    }

    private fun shippedClips(): List<AmyClip> =
        assetDir().listFiles { f -> f.name.endsWith(".accents.json") }
            .orEmpty()
            .map { AmyVideoPack.parseAccentsJson(it.readText()) }
            .sortedBy { it.name }

    @Test
    fun `parses the packaged accent json shape`() {
        // fast-01's packaged JSON, verbatim shape (the
        // pipeline's own fields, extras included).
        val clip = AmyVideoPack.parseAccentsJson(
            """
            {
             "clip": "fast-01",
             "group": "fast",
             "fps": 24,
             "frames": 236,
             "duration_s": 9.833,
             "accents": [1.0, 1.458, 2.0],
             "accent_count": 3,
             "implied_bpm": 115.3,
             "method": "frame-difference energy"
            }
            """.trimIndent(),
        )
        assertEquals("fast-01", clip.name)
        assertEquals("fast", clip.group)
        assertEquals("amy_video/fast-01.mp4", clip.assetPath)
        assertEquals(9.833, clip.durationS, 1e-9)
        assertEquals(listOf(1.0, 1.458, 2.0), clip.accentsS)
        assertEquals(115.3, clip.impliedBpm, 1e-9)
        // A JSON without the v1.0.72 figure fields parses
        // with the neutral defaults (no measured height,
        // no scaling).
        assertEquals(0.0, clip.figureHeight, 1e-9)
        assertEquals(1.0, clip.figureScale, 1e-9)
    }

    @Test
    fun `every clip carries a figure scale landing on the common anchor`() {
        // v1.0.72: the pack's clips were shot at figure
        // heights spanning 0.70–0.88 of the frame (measured,
        // tools/measure_figure_scales.py). Each packaged
        // JSON carries its measured height and the playback
        // factor that normalizes it; height × scale must be
        // the pack's ONE anchor (idle's height — idle is the
        // clip VIDEO_FIGURE_SCALE was tuned against, so its
        // factor is exactly 1) within 1% for every dance
        // clip, or chaining changes Amy's size mid-song.
        val clips = shippedClips()
        val idle = clips.first { it.name == AmyVideoPack.SPECIAL_IDLE }
        assertTrue("idle figure height missing", idle.figureHeight > 0.0)
        assertEquals("idle is the anchor clip", 1.0, idle.figureScale, 1e-9)
        val anchor = idle.figureHeight * idle.figureScale
        for (clip in clips) {
            assertTrue("${clip.name} figure height missing", clip.figureHeight > 0.0)
            assertTrue(
                "${clip.name} figure scale out of range: ${clip.figureScale}",
                clip.figureScale in 0.5..1.5,
            )
            val implied = clip.figureHeight * clip.figureScale
            assertTrue(
                "${clip.name} implied on-stage height $implied vs anchor $anchor (>1% off)",
                kotlin.math.abs(implied - anchor) <= 0.01 * anchor,
            )
        }
        assertEquals(20, clips.count { it.group != AmyVideoPack.GROUP_SPECIAL })
    }

    @Test
    fun `the shipped pack is complete - 24 clips in their groups`() {
        val clips = shippedClips()
        assertEquals(24, clips.size)
        assertEquals(10, clips.count { it.group == AmyVideoPack.GROUP_FAST })
        assertEquals(10, clips.count { it.group == AmyVideoPack.GROUP_SLOW })
        assertEquals(4, clips.count { it.group == AmyVideoPack.GROUP_SPECIAL })
        for (special in listOf("invite", "clap", "idle", "combo")) {
            assertNotNull("missing special $special", AmyVideoPack.special(clips, special))
        }
        for (clip in clips) {
            assertTrue("${clip.name} duration", clip.durationS > 9.0 && clip.durationS <= 10.1)
            assertTrue(
                "${clip.name} mp4 missing",
                File(assetDir(), "${clip.name}.mp4").isFile,
            )
            val sorted = clip.accentsS.sorted()
            assertEquals("${clip.name} accents not ascending", sorted, clip.accentsS)
            for (accent in clip.accentsS) {
                assertTrue("${clip.name} accent $accent outside the clip", accent > 0.0 && accent < clip.durationS)
            }
            if (clip.group != AmyVideoPack.GROUP_SPECIAL) {
                assertTrue("${clip.name} has too few accents", clip.accentsS.size >= 5)
                assertTrue("${clip.name} has no implied tempo", clip.impliedBpm > 0.0)
            }
        }
    }

    @Test
    fun `the dance pool follows the song tempo`() {
        val clips = shippedClips()
        val fastPool = AmyVideoPack.dancePool(clips, 130.0)
        assertEquals(10, fastPool.size)
        assertTrue(fastPool.all { it.group == AmyVideoPack.GROUP_FAST })
        val slowPool = AmyVideoPack.dancePool(clips, 80.0)
        assertEquals(10, slowPool.size)
        assertTrue(slowPool.all { it.group == AmyVideoPack.GROUP_SLOW })
        // The boundary sits at FAST_POOL_MIN_BPM.
        assertTrue(
            AmyVideoPack.dancePool(clips, AmyVideoPack.FAST_POOL_MIN_BPM)
                .all { it.group == AmyVideoPack.GROUP_FAST },
        )
    }

    @Test
    fun `pick rotates through the pool and never repeats the last clip`() {
        val clips = shippedClips()
        val pool = AmyVideoPack.dancePool(clips, 120.0)
        val picks = (0 until 10).map { rotation ->
            AmyVideoPack.pickNext(pool, 120.0, null, rotation)!!.name
        }
        assertTrue("rotation barely moved: $picks", picks.toSet().size >= 3)
        // Rotation 0 takes a best-fitting clip (implied tempo
        // closest to the song's).
        val first = AmyVideoPack.pickNext(pool, 120.0, null, 0)!!
        val bestDistance = pool.minOf { kotlin.math.abs(it.impliedBpm - 120.0) }
        assertEquals(bestDistance, kotlin.math.abs(first.impliedBpm - 120.0), 1e-9)
        // The same rotation with the last clip named moves on.
        val next = AmyVideoPack.pickNext(pool, 120.0, first.name, 0)!!
        assertNotEquals(first.name, next.name)
        // Degenerate pools behave.
        assertNull(AmyVideoPack.pickNext(emptyList(), 120.0, null, 0))
        val single = AmyVideoPack.pickNext(listOf(first), 120.0, first.name, 0)
        assertEquals(first.name, single!!.name)
    }

    @Test
    fun `chain walks the pool in order wraps and never repeats`() {
        // v1.0.71: inside a song the dance chains through
        // the pool at pass boundaries.
        fun clip(name: String) = AmyClip(name, "fast", "amy_video/$name.mp4", 10.0, listOf(1.0), 120.0)
        val pool = listOf(clip("a"), clip("b"), clip("c"))
        assertEquals("b", AmyVideoPack.chainNext(pool, "a")!!.name)
        assertEquals("c", AmyVideoPack.chainNext(pool, "b")!!.name)
        // Wraps at the end — a long song cycles the group.
        assertEquals("a", AmyVideoPack.chainNext(pool, "c")!!.name)
        // A full cycle visits every clip and never lands on
        // the clip that just played.
        var current = "a"
        val visited = mutableListOf<String>()
        repeat(6) {
            val next = AmyVideoPack.chainNext(pool, current)!!
            assertNotEquals(current, next.name)
            visited += next.name
            current = next.name
        }
        assertEquals(setOf("a", "b", "c"), visited.toSet())
        // Degenerate pools: nothing to chain to.
        assertNull(AmyVideoPack.chainNext(listOf(clip("a")), "a"))
        assertNull(AmyVideoPack.chainNext(emptyList(), "a"))
        assertNull(AmyVideoPack.chainNext(pool, "missing"))
    }

    @Test
    fun `special finds clips by name and nothing else`() {
        val clips = shippedClips()
        assertEquals("invite", AmyVideoPack.special(clips, "invite")!!.name)
        assertEquals("clap", AmyVideoPack.special(clips, "clap")!!.name)
        assertEquals("idle", AmyVideoPack.special(clips, "idle")!!.name)
        assertNull(AmyVideoPack.special(clips, "fast-01"))
        assertNull(AmyVideoPack.special(clips, "missing"))
    }
}
