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
        // Pack2's fast-01 (v1.0.80) carries 18 accents:
        // lead-in + accents + tail = 19 raw segments; the
        // 0.25 s segment before the 2.0 s accent is too
        // short to hold a beat and glues forward into the
        // next, leaving 18 segments; two of them (3.083–
        // 3.792 and the 9.0–tail close) take 2 beats, the
        // rest 1 — 20 beats in all.
        assertEquals(18, pass.segments.size)
        assertEquals(20, pass.totalBeats)
        assertEquals(20 * period, pass.durationS(period), 1e-9)
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
    fun `audit - every ratio-eligible clip plans in-band at representative song tempos`() {
        // v1.0.73 chaining audit, first half — reframed by
        // v1.0.79's ratio eligibility: the pool is now the
        // set of clips whose whole-clip speed (song tempo
        // over implied tempo) sits in [0.7, 1.6]. The audit
        // question becomes whether the scheduler's actual
        // per-segment plans agree with that whole-clip
        // judgement — i.e. every eligible clip really does
        // plan with every segment inside the same band
        // (gluing plus beat-count adjustment absorbs the
        // tempo difference). Measured on the shipped pack:
        // yes, at every tempo below.
        val clips = shippedClips()
        for (bpm in listOf(80.0, 96.0, 120.0, 140.0, 171.4)) {
            val pool = AmyVideoPack.dancePool(clips, bpm)
            assertTrue("pool at $bpm must not be empty", pool.isNotEmpty())
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
            assertEquals("usable clips at $bpm BPM", pool.size, usable)
        }
    }

    @Test
    fun `slow song glues short segments until they can hold a beat`() {
        val clip = fast01()
        val period = 60.0 / 50.0
        val pass = BeatSliceScheduler.plan(clip.accentsS, clip.durationS, period)
        // Pack2 fast-01's 18 accents glue down to 9
        // segments at a 50 BPM song beat (measured).
        assertEquals(9, pass.segments.size)
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
