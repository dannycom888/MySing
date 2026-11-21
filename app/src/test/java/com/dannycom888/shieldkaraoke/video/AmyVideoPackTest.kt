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
            // <= 10.2 since v1.0.77: the canonical bookend
            // splice adds 4 frames (0.167 s) to every dance
            // clip, putting the 240-frame clips at 10.167 s.
            assertTrue("${clip.name} duration", clip.durationS > 9.0 && clip.durationS <= 10.2)
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
    fun `pool rebuild is decided by the measured tempo crossing pools`() {
        // v1.0.76: an uncached song starts dancing in the
        // slow pool (the 96 BPM default picks it); when the
        // measurement lands, the walk is rebuilt ONLY if the
        // measured tempo implies the other pool.
        val clips = shippedClips()
        val slowPool = AmyVideoPack.dancePool(clips, 96.0)
        val fastPool = AmyVideoPack.dancePool(clips, 130.0)
        // The reported case: default-tempo slow pool, the
        // song measures fast -> rebuild.
        assertTrue(AmyVideoPack.poolNeedsRebuild(clips, slowPool, 130.0))
        assertTrue(AmyVideoPack.poolNeedsRebuild(clips, slowPool, 171.4))
        // A genuinely slow song stays in its pool.
        assertTrue(!AmyVideoPack.poolNeedsRebuild(clips, slowPool, 80.0))
        assertTrue(!AmyVideoPack.poolNeedsRebuild(clips, slowPool, 96.0))
        // A fast start is not rebuilt by another fast value,
        // and IS rebuilt by a slow measurement.
        assertTrue(!AmyVideoPack.poolNeedsRebuild(clips, fastPool, 171.4))
        assertTrue(AmyVideoPack.poolNeedsRebuild(clips, fastPool, 85.7))
        // The boundary is the pool threshold itself.
        assertTrue(
            AmyVideoPack.poolNeedsRebuild(
                clips, slowPool, AmyVideoPack.FAST_POOL_MIN_BPM,
            ),
        )
        assertTrue(
            !AmyVideoPack.poolNeedsRebuild(
                clips, slowPool, AmyVideoPack.FAST_POOL_MIN_BPM - 0.1,
            ),
        )
    }

    @Test
    fun `pool rebuild never fires on a damaged pack`() {
        // With a group missing, dancePool widens to every
        // dance clip at ANY tempo — the widened pool equals
        // itself, so no rebuild is ever "needed" and the
        // walk is left alone.
        val clips = shippedClips()
        val slowOnly = clips.filter { it.group != AmyVideoPack.GROUP_FAST }
        val widened = AmyVideoPack.dancePool(slowOnly, 80.0)
        assertTrue(widened.isNotEmpty())
        assertTrue(!AmyVideoPack.poolNeedsRebuild(slowOnly, widened, 170.0))
        assertTrue(!AmyVideoPack.poolNeedsRebuild(slowOnly, widened, 70.0))
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
    fun `rotation exhausts the whole pool before any clip repeats`() {
        // v1.0.73: shuffle-with-exhaustion — the user's
        // ruling is that the group's ten clips are enough
        // for one song IF the walk actually visits them all.
        // Identity shuffle keeps the arithmetic transparent:
        // cycle 1 is the pick followed by the pool in order,
        // cycle 2 is the pool in order again.
        val clips = shippedClips()
        val pool = AmyVideoPack.dancePool(clips, 120.0)
        val pick = pool.first { it.name == "fast-03" }
        val rotation = DanceRotation(pool, pick) { it }
        assertEquals(pick.name, rotation.current().name)
        val firstCycle = mutableListOf(rotation.current().name)
        repeat(pool.size - 1) { firstCycle += rotation.advance().name }
        assertEquals(
            "cycle 1 must visit every clip exactly once",
            pool.map { it.name }.toSet(), firstCycle.toSet(),
        )
        assertEquals(pool.size, firstCycle.size)
        // The seam: cycle 2 also covers the pool, and its
        // first clip is not the one that just played.
        val secondCycle = mutableListOf(rotation.advance().name)
        assertNotEquals(firstCycle.last(), secondCycle.first())
        repeat(pool.size - 1) { secondCycle += rotation.advance().name }
        assertEquals(pool.map { it.name }.toSet(), secondCycle.toSet())
    }

    @Test
    fun `rotation peekNext always names the clip advance lands on`() {
        // The driver preloads peekNext and swaps to it at
        // the boundary — the two must never disagree,
        // including across the cycle seam.
        val clips = shippedClips()
        val pool = AmyVideoPack.dancePool(clips, 80.0)
        val rotation = DanceRotation(pool, pool[3]) { it.reversed() }
        repeat(pool.size * 2 + 1) {
            val peeked = rotation.peekNext()
            assertNotNull(peeked)
            assertEquals(peeked!!.name, rotation.advance().name)
            assertEquals(peeked.name, rotation.current().name)
        }
        // Degenerate pools: nothing to rotate to.
        val single = DanceRotation(listOf(pool[0]), pool[0]) { it }
        assertNull(single.peekNext())
        assertEquals(pool[0].name, single.advance().name)
        // Two clips simply alternate, seam included.
        val pair = DanceRotation(pool.take(2), pool[0]) { it }
        assertEquals(pool[1].name, pair.advance().name)
        assertEquals(pool[0].name, pair.advance().name)
        assertEquals(pool[1].name, pair.advance().name)
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
