package com.dannycom888.shieldkaraoke.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * v1.0.70: the beat-sliced scheduler （切片对拍） — the pure
 * plan that maps a clip's movement accents onto whole song
 * beats. The contract the stage relies on:
 *
 *  - every segment plays inside the [BeatSliceScheduler
 *    .MIN_SPEED, MAX_SPEED] clamp (0.7–1.6×);
 *  - a pass lasts EXACTLY totalBeats × period, so accents
 *    land on beat boundaries and passes re-sync for free;
 *  - segment boundaries are the clip's accents (plus a
 *    one-beat lead-in and the tail), except accents absorbed
 *    when a too-short segment is glued to its neighbour.
 *
 * The clip data is the SHIPPED pack (read from the app
 * assets), so these tests fail if a re-pack ever ships a
 * clip the scheduler cannot keep inside the clamp.
 */
class BeatSliceSchedulerTest {

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

    private fun fast01(): AmyClip = shippedClips().first { it.name == "fast-01" }

    @Test
    fun `fast-01 at 127 bpm - accents land on whole beats`() {
        val clip = fast01()
        val period = 60.0 / 127.0
        val pass = BeatSliceScheduler.plan(clip.accentsS, clip.durationS, period)
        // 17 accents + lead-in + tail = 18 raw segments; the
        // tail glues backward into the last accent's segment,
        // the rest take 1 beat each. Since v1.0.77 the clip
        // carries the canonical bookend splice (+4 frames, all
        // accents +2 frames): the tail is 0.292 s, the merged
        // last segment spans 1.208 s and rounds to 3 beats
        // (was 1.125 s / 2 beats with the 0.208 s tail).
        assertEquals(17, pass.segments.size)
        assertEquals(19, pass.totalBeats)
        assertEquals(19 * period, pass.durationS(period), 1e-9)
        for (segment in pass.segments) {
            assertTrue(
                "speed ${segment.speed} outside the clamp",
                segment.speed >= BeatSliceScheduler.MIN_SPEED - 1e-9 &&
                    segment.speed <= BeatSliceScheduler.MAX_SPEED + 1e-9,
            )
        }
        // The lead-in starts exactly one beat before the
        // first accent, so the first accent lands on beat 1.
        assertEquals(clip.accentsS.first() - period, pass.segments.first().clipStartS, 1e-9)
        val atBeat1 = pass.locate(period, period)
        assertEquals(clip.accentsS.first(), atBeat1!!.clipPositionS, 1e-9)
        // Every segment starts exactly where the previous one
        // ended — in the clip AND on the beat grid.
        var songStart = 0.0
        pass.segments.forEachIndexed { index, segment ->
            val located = pass.locate(songStart, period)
            assertEquals(index, located!!.segmentIndex)
            assertEquals(segment.clipStartS, located.clipPositionS, 1e-9)
            songStart += segment.songDurationS(period)
        }
        assertEquals(clip.durationS, pass.segments.last().clipEndS, 1e-9)
    }

    @Test
    fun `audit - the full tempo group stays usable at representative song tempos`() {
        // v1.0.73 chaining audit, first half: does the
        // [0.7, 1.6] speed guard shrink the song's rotation
        // at real-world tempos? Measured answer: NO — the
        // scheduler has no eligibility filter to begin with;
        // gluing plus beat-count adjustment absorbs the
        // whole tempo difference, so the pool the rotation
        // walks is the full group (10/10) at every tempo
        // below. (The repetition the user saw was the walk,
        // not the pool — see the v1.0.73 chapter.)
        val clips = shippedClips()
        val cases = listOf(
            80.0 to AmyVideoPack.GROUP_SLOW,
            96.0 to AmyVideoPack.GROUP_SLOW,
            120.0 to AmyVideoPack.GROUP_FAST,
            140.0 to AmyVideoPack.GROUP_FAST,
        )
        for ((bpm, group) in cases) {
            val pool = AmyVideoPack.dancePool(clips, bpm)
            assertEquals("pool at $bpm", 10, pool.size)
            assertTrue(pool.all { it.group == group })
            val period = 60.0 / bpm
            var usable = 0
            for (clip in pool) {
                val pass = BeatSliceScheduler.plan(
                    clip.accentsS, clip.durationS, period,
                )
                if (pass.segments.isNotEmpty() && pass.segments.all {
                        it.speed >= BeatSliceScheduler.MIN_SPEED - 1e-9 &&
                            it.speed <= BeatSliceScheduler.MAX_SPEED + 1e-9
                    }
                ) {
                    usable++
                }
            }
            assertEquals("usable clips at $bpm BPM", 10, usable)
        }
    }

    @Test
    fun `slow song glues short segments until they can hold a beat`() {
        val clip = fast01()
        val period = 60.0 / 50.0
        val pass = BeatSliceScheduler.plan(clip.accentsS, clip.durationS, period)
        assertTrue(pass.segments.size < 17)
        for (segment in pass.segments) {
            assertTrue(
                "segment of ${segment.naturalS}s is shorter than 0.7 beat",
                segment.naturalS >= BeatSliceScheduler.MIN_SPEED * period - 1e-9,
            )
            assertTrue(segment.speed >= BeatSliceScheduler.MIN_SPEED - 1e-9)
            assertTrue(segment.speed <= BeatSliceScheduler.MAX_SPEED + 1e-9)
        }
        assertEquals(pass.totalBeats * period, pass.durationS(period), 1e-9)
    }

    @Test
    fun `sweep - every shipped dance clip at every tempo stays in the clamp`() {
        val dance = shippedClips().filter { it.group != AmyVideoPack.GROUP_SPECIAL }
        assertEquals(20, dance.size)
        for (clip in dance) {
            for (bpm in 50..200 step 10) {
                val period = 60.0 / bpm
                val pass = BeatSliceScheduler.plan(clip.accentsS, clip.durationS, period)
                assertTrue("${clip.name} @ $bpm planned empty", pass.segments.isNotEmpty())
                for (segment in pass.segments) {
                    assertTrue(
                        "${clip.name} @ $bpm: speed ${segment.speed} outside the clamp",
                        segment.speed >= BeatSliceScheduler.MIN_SPEED - 1e-9 &&
                            segment.speed <= BeatSliceScheduler.MAX_SPEED + 1e-9,
                    )
                }
                assertEquals(
                    "${clip.name} @ $bpm: pass duration is not whole beats",
                    pass.totalBeats * period, pass.durationS(period), 1e-9,
                )
            }
        }
    }

    @Test
    fun `no accents - the whole clip is one segment`() {
        val pass = BeatSliceScheduler.plan(emptyList(), 10.0, 0.5)
        assertEquals(1, pass.segments.size)
        assertEquals(0.0, pass.segments[0].clipStartS, 1e-9)
        assertEquals(10.0, pass.segments[0].clipEndS, 1e-9)
        assertEquals(20, pass.segments[0].beats)
        assertEquals(1.0, pass.segments[0].speed, 1e-9)
    }

    @Test
    fun `degenerate inputs plan an empty pass`() {
        assertTrue(BeatSliceScheduler.plan(listOf(1.0), 0.0, 0.5).segments.isEmpty())
        assertTrue(BeatSliceScheduler.plan(listOf(1.0), 10.0, 0.0).segments.isEmpty())
        assertTrue(BeatSliceScheduler.plan(listOf(1.0), -1.0, 0.5).segments.isEmpty())
    }

    @Test
    fun `locate interpolates inside a segment and clamps at the ends`() {
        // One accent at 2 s in a 4 s clip, half-second beats:
        // lead-in [1.5, 2.0) on 1 beat, then [2.0, 4.0) on 4.
        val pass = BeatSliceScheduler.plan(listOf(2.0), 4.0, 0.5)
        assertEquals(2, pass.segments.size)
        assertEquals(1.75, pass.locate(0.25, 0.5)!!.clipPositionS, 1e-9)
        assertEquals(2.0, pass.locate(0.5, 0.5)!!.clipPositionS, 1e-9)
        assertEquals(3.0, pass.locate(1.5, 0.5)!!.clipPositionS, 1e-9)
        assertEquals(4.0, pass.locate(99.0, 0.5)!!.clipPositionS, 1e-9)
        assertEquals(1.5, pass.locate(-1.0, 0.5)!!.clipPositionS, 1e-9)
    }

    @Test
    fun `unsorted duplicate and out-of-range accents are normalised`() {
        val messy = BeatSliceScheduler.plan(listOf(3.0, 1.0, 1.0, 99.0, -1.0, 0.0, 4.0), 4.0, 0.5)
        val clean = BeatSliceScheduler.plan(listOf(1.0, 3.0), 4.0, 0.5)
        assertEquals(clean.segments, messy.segments)
    }

    @Test
    fun `clamp constants are the user's 0_7 to 1_6 guard`() {
        assertEquals(0.7, BeatSliceScheduler.MIN_SPEED, 0.0)
        assertEquals(1.6, BeatSliceScheduler.MAX_SPEED, 0.0)
        assertTrue(abs(BeatSliceScheduler.MIN_SPEED - 0.7) < 1e-12)
    }
}
