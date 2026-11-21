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
    fun `the dance pool is the ratio-eligible set across both groups`() {
        // v1.0.79 (the user's ruling): no hard fast/slow
        // split — a clip serves a song when the speed it
        // would play at (song tempo over the clip's implied
        // tempo) sits inside the scheduler's 0.7–1.6 guard.
        val clips = shippedClips()
        // Every member of every pool satisfies the ratio,
        // and pools genuinely mix the groups at mid tempos:
        // at 120 BPM, 16 of the 18 rotatable clips qualify,
        // drawn from BOTH groups (only slow-06 and slow-07,
        // implied ≈53/50, would need >1.6× and sit out).
        val mid = AmyVideoPack.dancePool(clips, 120.0)
        assertEquals(16, mid.size)
        assertTrue(mid.all { AmyVideoPack.tempoEligible(it, 120.0) })
        assertTrue(mid.any { it.group == AmyVideoPack.GROUP_FAST })
        assertTrue(mid.any { it.group == AmyVideoPack.GROUP_SLOW })
        assertTrue(mid.none { it.name == "slow-06" || it.name == "slow-07" })
        // The guard band edges are inclusive: a clip whose
        // implied tempo makes the ratio exactly 0.7 or
        // exactly 1.6 still serves.
        fun synthetic(name: String, implied: Double) =
            AmyClip(name, "fast", "amy_video/$name.mp4", 10.0, listOf(1.0), implied)
        val edge = synthetic("edge", 100.0)
        assertTrue(AmyVideoPack.tempoEligible(edge, 160.0))
        assertTrue(AmyVideoPack.tempoEligible(edge, 70.0))
        assertTrue(!AmyVideoPack.tempoEligible(edge, 161.0))
        assertTrue(!AmyVideoPack.tempoEligible(edge, 69.0))
        // A clip with no measured tempo never qualifies on
        // ratio (it can still dance via the widened pool).
        assertTrue(!AmyVideoPack.tempoEligible(synthetic("unmeasured", 0.0), 120.0))
    }

    @Test
    fun `a 171 bpm song excludes the out-of-ratio slow clips`() {
        // The 《惊雷》 pin: at its measured 171.4, every
        // slow-group clip whose implied tempo puts it out
        // of the guard band sits out — slow-07 (implied
        // 49.6, ratio 3.5) first among them.
        val clips = shippedClips()
        val pool = AmyVideoPack.dancePool(clips, 171.4)
        val outOfRatioSlow = listOf(
            "slow-01", "slow-02", "slow-03", "slow-04",
            "slow-06", "slow-07", "slow-08", "slow-10",
        )
        assertTrue(pool.none { it.name in outOfRatioSlow })
        // The rule cuts the other way too: fast-04 (implied
        // 96) would need 1.79× at 171.4 and sits out despite
        // its fast-group label.
        assertTrue(pool.none { it.name == "fast-04" })
        // Two slow-group clips DO serve: slow-05 and
        // slow-09's accents genuinely pace 143.9 / 131.0,
        // so their ratios (1.19 / 1.31) are in band — the
        // rule judges measured pace, not the group label.
        assertTrue(pool.any { it.name == "slow-05" })
        assertTrue(pool.any { it.name == "slow-09" })
        // The rule itself, independent of today's pack: a
        // slow-group clip at implied 75 needs 2.28× at
        // 171.4 — out.
        val slow75 = AmyClip("slow-75", "slow", "amy_video/slow-75.mp4", 10.0, listOf(1.0), 75.0)
        assertTrue(!AmyVideoPack.tempoEligible(slow75, 171.4))
    }

    @Test
    fun `the punch-audit clips are never chosen at any tempo`() {
        // v1.0.79 (the user's ruling): fast-03 and fast-07
        // stay in the pack but out of every pool — the
        // eligible set AND the widened fallback alike.
        val clips = shippedClips()
        assertTrue(clips.any { it.name == "fast-03" })
        assertTrue(clips.any { it.name == "fast-07" })
        var bpm = 40.0
        while (bpm <= 300.0) {
            val pool = AmyVideoPack.dancePool(clips, bpm)
            assertTrue(
                "excluded clip in the pool at $bpm",
                pool.none { it.name in AmyVideoPack.EXCLUDED_FROM_ROTATION },
            )
            bpm += 5.0
        }
        // The denylist, not the ratio, does this: at 131
        // fast-03's own ratio is 1.0, yet it is not served.
        val fast03 = clips.first { it.name == "fast-03" }
        assertTrue(AmyVideoPack.tempoEligible(fast03, 131.0))
        assertTrue(AmyVideoPack.dancePool(clips, 131.0).none { it.name == "fast-03" })
        // A pack of nothing but excluded clips has no pool.
        val onlyExcluded = clips.filter { it.name in AmyVideoPack.EXCLUDED_FROM_ROTATION }
        assertTrue(AmyVideoPack.dancePool(onlyExcluded, 120.0).isEmpty())
    }

    @Test
    fun `pool rebuild is decided by the measured tempo changing the eligible set`() {
        // v1.0.76: an uncached song starts dancing in the
        // set the 96 BPM default implies; when the
        // measurement lands, the walk is rebuilt ONLY if
        // the measured tempo's eligible set differs.
        val clips = shippedClips()
        val defaultPool = AmyVideoPack.dancePool(clips, 96.0)
        // The reported case: default-tempo set, the song
        // measures fast -> rebuild.
        assertTrue(AmyVideoPack.poolNeedsRebuild(clips, defaultPool, 171.4))
        // A measurement that implies the SAME set (96.8
        // qualifies exactly the clips 96 does) rebuilds
        // nothing.
        assertTrue(!AmyVideoPack.poolNeedsRebuild(clips, defaultPool, 96.8))
        assertTrue(!AmyVideoPack.poolNeedsRebuild(clips, defaultPool, 96.0))
        // A fast start is not rebuilt by another value with
        // the same set, and IS rebuilt by a slow one.
        val fastPool = AmyVideoPack.dancePool(clips, 171.4)
        assertTrue(!AmyVideoPack.poolNeedsRebuild(clips, fastPool, 171.4))
        assertTrue(AmyVideoPack.poolNeedsRebuild(clips, fastPool, 85.7))
    }

    @Test
    fun `pool widens instead of emptying and a widened pool rebuilds nothing`() {
        // At a tempo no clip can reach, dancePool widens
        // to every non-excluded dance clip rather than
        // serving an empty stage; the widened pool equals
        // itself at any other unreachable tempo, so no
        // spurious rebuild follows.
        val clips = shippedClips()
        val widened = AmyVideoPack.dancePool(clips, 300.0)
        assertEquals(18, widened.size)
        assertTrue(widened.none { it.name in AmyVideoPack.EXCLUDED_FROM_ROTATION })
        assertTrue(!AmyVideoPack.poolNeedsRebuild(clips, widened, 310.0))
        // With no dance clips at all there is nothing to
        // rebuild to.
        val specialsOnly = clips.filter { it.group == AmyVideoPack.GROUP_SPECIAL }
        assertTrue(AmyVideoPack.dancePool(specialsOnly, 120.0).isEmpty())
        assertTrue(!AmyVideoPack.poolNeedsRebuild(specialsOnly, emptyList(), 171.4))
    }

    @Test
    fun `rebuild leads with a fresh pick while the intro is still showing`() {
        // v1.0.79: when the measured tempo lands during the
        // intro, nothing from the old walk has been loaded —
        // the rebuilt rotation must start from the new pool,
        // not from the old walk's next clip (the v1.0.76
        // anchor), or the song's first dance pass is
        // guaranteed to come from the old pool.
        fun synthetic(name: String, group: String, implied: Double) =
            AmyClip(name, group, "amy_video/$name.mp4", 10.0, listOf(1.0), implied)
        val oldPool = listOf(
            synthetic("s1", "slow", 50.0),
            synthetic("s2", "slow", 55.0),
            synthetic("s3", "slow", 60.0),
        )
        val newPool = listOf(
            synthetic("f1", "fast", 170.0),
            synthetic("f2", "fast", 150.0),
        )
        val oldWalk = DanceRotation(oldPool, oldPool[0]) { it }
        val first = AmyVideoPack.rebuildFirstClip(
            newPool, oldWalk, introShowing = true,
            measuredBpm = 171.4, lastClipName = null, rotation = 0,
        )!!
        // The fresh pick is the new pool's tempo fit (f1,
        // implied 170), NOT the old walk's next clip (s2).
        assertEquals("f1", first.name)
        assertNotEquals(oldWalk.peekNext()!!.name, first.name)
    }

    @Test
    fun `rebuild keeps the loaded handoff clip once the dance has entered`() {
        // Mid-dance, the old walk's next clip is already on
        // the preload player and plays at the next boundary
        // whatever the rotation says — the rebuilt rotation
        // leads with it so the bookkeeping stays aligned.
        fun synthetic(name: String, group: String, implied: Double) =
            AmyClip(name, group, "amy_video/$name.mp4", 10.0, listOf(1.0), implied)
        val oldPool = listOf(
            synthetic("s1", "slow", 50.0),
            synthetic("s2", "slow", 55.0),
            synthetic("s3", "slow", 60.0),
        )
        val newPool = listOf(
            synthetic("f1", "fast", 170.0),
            synthetic("f2", "fast", 150.0),
        )
        val oldWalk = DanceRotation(oldPool, oldPool[0]) { it }
        val first = AmyVideoPack.rebuildFirstClip(
            newPool, oldWalk, introShowing = false,
            measuredBpm = 171.4, lastClipName = null, rotation = 0,
        )!!
        assertEquals(oldWalk.peekNext()!!.name, first.name)
        // A one-clip walk has no peek: the handoff is its
        // current clip.
        val single = DanceRotation(listOf(oldPool[0]), oldPool[0]) { it }
        val firstSingle = AmyVideoPack.rebuildFirstClip(
            newPool, single, introShowing = false,
            measuredBpm = 171.4, lastClipName = null, rotation = 0,
        )!!
        assertEquals(oldPool[0].name, firstSingle.name)
        // Intro with an empty new pool falls back to the
        // handoff rather than rebuilding to nothing.
        val firstFallback = AmyVideoPack.rebuildFirstClip(
            emptyList(), oldWalk, introShowing = true,
            measuredBpm = 171.4, lastClipName = null, rotation = 0,
        )!!
        assertEquals(oldWalk.peekNext()!!.name, firstFallback.name)
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
        // ruling is that the pool's clips are enough for
        // one song IF the walk actually visits them all.
        // Identity shuffle keeps the arithmetic transparent:
        // cycle 1 is the pick followed by the pool in order,
        // cycle 2 is the pool in order again.
        val clips = shippedClips()
        val pool = AmyVideoPack.dancePool(clips, 120.0)
        val pick = pool.first { it.name == "fast-02" }
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
