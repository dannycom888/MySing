package com.dannycom888.shieldkaraoke.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The vocal-cancel processor must accept the formats decoders
 * actually emit — 16-bit AND float PCM, any channel count ≥ 2 —
 * instead of returning NOT_SET and being bypassed (the user's
 * "原唱和伴奏没什么区别": on his songs the switch did nothing
 * because the processor never engaged).
 *
 * v1.0.40 rewrites (noted per the version's 口径 rules): the
 * declared output format is now ALWAYS 16-bit PCM — float input
 * is converted to 16-bit samples on the way in and cancelled in
 * the same short domain, so the chain never hands float to the
 * sink. The v1.0.38 tests that pinned float-in/float-out
 * (`accepts16BitStereoAndFloatStereo`'s float expectation and
 * `cancelFloatMatches16BitInFloatDomain`) are rewritten to the
 * 16-bit-output 口径, and dedicated conversion tests are added.
 *
 * v1.0.44 rewrites (the crossover): the enabled path is no
 * longer a stateless full-band (L−R)/2 — a one-pole lowpass at
 * 180 Hz splits each channel, the low band is preserved as the
 * mono sum and only the high band is cancelled. Single-frame
 * sample-exact pins were a property of the stateless algorithm
 * (the first frame out of a zeroed filter is transient, e.g.
 * (1000, 200) now yields 405, not 400, and a constant/DC input
 * is by design preserved as lows), so:
 *  - `cancel16BitIsClassicCentreCancellation` is replaced by
 *    steady-state sine pins (bass retention, 1 kHz residual,
 *    panned identity, buffer-seam continuity);
 *  - `acceptsMultichannelAndCancelsFirstTwoChannelsOnly` now
 *    pins the history-independent properties (extras verbatim,
 *    first two channels mono-ised) over a long stream instead
 *    of one frame's value;
 *  - `cancelFloatConvertsThenCancelsIn16BitDomain` becomes an
 *    equivalence pin: float input, converted, must produce the
 *    native 16-bit path's output (±1 LSB of float rounding);
 *  - `floatMultichannelConvertsExtraChannelsVerbatim` keeps its
 *    name and claim, generalised from one frame to a stream.
 * Format/disabled/reset pins are untouched — those 口径 did not
 * change.
 */
class VocalCancelAudioProcessorTest {

    private val sampleRate = 44100

    private fun format(channels: Int, encoding: Int) =
        AudioProcessor.AudioFormat(sampleRate, channels, encoding)

    private fun shortBuffer(vararg samples: Short): ByteBuffer {
        val buf = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { buf.putShort(it) }
        buf.flip()
        return buf
    }

    private fun floatBuffer(vararg samples: Float): ByteBuffer {
        val buf = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { buf.putFloat(it) }
        buf.flip()
        return buf
    }

    private fun drainShorts(p: VocalCancelAudioProcessor, count: Int): List<Short> {
        val out = p.output.order(ByteOrder.LITTLE_ENDIAN)
        return List(count) { out.getShort(it * 2) }
    }

    /** Queue one buffer and copy its whole output out (the
     *  processor reuses its internal buffer, so copy eagerly). */
    private fun feed(p: VocalCancelAudioProcessor, samples: ShortArray): List<Short> {
        val buf = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { buf.putShort(it) }
        buf.flip()
        p.queueInput(buf)
        return drainShorts(p, samples.size)
    }

    private fun feedFloats(p: VocalCancelAudioProcessor, samples: FloatArray): List<Short> {
        val buf = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { buf.putFloat(it) }
        buf.flip()
        p.queueInput(buf)
        val out = p.output.order(ByteOrder.LITTLE_ENDIAN)
        return List(samples.size) { out.getShort(it * 2) }
    }

    /** Interleaved stereo sine; [rightGain] 1.0 = dead centre. */
    private fun sineStereo(
        freqHz: Double,
        seconds: Double,
        amplitude: Double = 12000.0,
        rightGain: Double = 1.0,
    ): ShortArray {
        val frames = (sampleRate * seconds).toInt()
        return ShortArray(frames * 2) { i ->
            val frame = i / 2
            val gain = if (i % 2 == 0) 1.0 else rightGain
            (amplitude * gain * sin(2.0 * PI * freqHz * frame / sampleRate))
                .roundToInt().toShort()
        }
    }

    /** The pre-v1.0.44 full-band algorithm, kept as the
     *  comparison baseline the crossover has to beat on bass
     *  and match on vocal-band cancellation. */
    private fun classicCancel(samples: ShortArray): List<Short> {
        val out = ArrayList<Short>(samples.size)
        var i = 0
        while (i + 1 < samples.size) {
            val mid = ((samples[i].toInt() - samples[i + 1].toInt()) / 2).toShort()
            out.add(mid)
            out.add(mid)
            i += 2
        }
        return out
    }

    /** Sum of squares over both channels, frames [fromFrame, toFrame). */
    private fun energy(samples: List<Short>, fromFrame: Int, toFrame: Int): Double {
        var e = 0.0
        for (f in fromFrame until toFrame) {
            val l = samples[f * 2].toDouble()
            val r = samples[f * 2 + 1].toDouble()
            e += l * l + r * r
        }
        return e
    }

    private fun energy(samples: ShortArray, fromFrame: Int, toFrame: Int): Double =
        energy(samples.toList(), fromFrame, toFrame)

    @Test
    fun accepts16BitStereoAndFloatStereo() {
        val p16 = VocalCancelAudioProcessor()
        assertEquals(
            format(2, C.ENCODING_PCM_16BIT),
            p16.configure(format(2, C.ENCODING_PCM_16BIT)),
        )
        assertTrue(p16.isActive)
        assertTrue(p16.configured)

        // v1.0.40 口径 rewrite: float is accepted, but the
        // declared output format is 16-bit, never float.
        val pFloat = VocalCancelAudioProcessor()
        assertEquals(
            format(2, C.ENCODING_PCM_16BIT),
            pFloat.configure(format(2, C.ENCODING_PCM_FLOAT)),
        )
        assertTrue(pFloat.isActive)
        assertTrue(pFloat.configured)
    }

    @Test
    fun multichannelCancelsFirstTwoChannelsAndPassesExtrasVerbatim() {
        val p = VocalCancelAudioProcessor()
        assertEquals(
            format(4, C.ENCODING_PCM_16BIT),
            p.configure(format(4, C.ENCODING_PCM_16BIT)),
        )
        p.enabled = true
        // 4000 frames: L/R sines, extras a deterministic pattern
        // that must come through sample-exact; L/R must come out
        // mono-ised (both channels the same value) — the two
        // properties of the new path that hold at every frame,
        // transient or not.
        val frames = 4000
        val input = ShortArray(frames * 4) { i ->
            val frame = i / 4
            when (i % 4) {
                0 -> (10000 * sin(2.0 * PI * 1000 * frame / sampleRate)).roundToInt().toShort()
                1 -> (5000 * sin(2.0 * PI * 1000 * frame / sampleRate)).roundToInt().toShort()
                2 -> ((frame * 7919) % 40001 - 20000).toShort()
                else -> (20000 - (frame * 104729) % 40001).toShort()
            }
        }
        val out = feed(p, input)
        for (f in 0 until frames) {
            assertEquals(input[f * 4 + 2], out[f * 4 + 2])
            assertEquals(input[f * 4 + 3], out[f * 4 + 3])
            assertEquals(out[f * 4], out[f * 4 + 1])
        }
    }

    @Test
    fun rejectsMonoAndNonPcmEncodings() {
        val mono = VocalCancelAudioProcessor()
        assertEquals(
            AudioProcessor.AudioFormat.NOT_SET,
            mono.configure(format(1, C.ENCODING_PCM_16BIT)),
        )
        assertFalse(mono.isActive)
        // Configured-but-rejected is exactly the state the
        // controller's degradation note keys on.
        assertTrue(mono.configured)

        val eightBit = VocalCancelAudioProcessor()
        assertEquals(
            AudioProcessor.AudioFormat.NOT_SET,
            eightBit.configure(format(2, C.ENCODING_PCM_8BIT)),
        )
        assertFalse(eightBit.isActive)
    }

    @Test
    fun disabledFloatInputIsConvertedVerbatimTo16Bit() {
        val p = VocalCancelAudioProcessor()
        p.configure(format(2, C.ENCODING_PCM_FLOAT))
        p.enabled = false
        // No cancellation, but the format conversion still
        // happens: the declared output is 16-bit, so float bytes
        // may never leak downstream even when disabled.
        p.queueInput(floatBuffer(1.0f, -1.0f, 0.0f, -1.0f))
        assertEquals(
            listOf(32767.toShort(), (-32767).toShort(), 0.toShort(), (-32767).toShort()),
            drainShorts(p, 4),
        )
    }

    @Test
    fun cancelFloatConvertsThenCancelsLikeNative16Bit() {
        // Mixed content (a low tone both channels share, a mid
        // tone at different gains, a high tone on one side) so
        // both bands and the filter state are exercised.
        val frames = 8000
        val shorts = ShortArray(frames * 2) { i ->
            val frame = i / 2
            val t = frame.toDouble() / sampleRate
            val l = 9000 * sin(2.0 * PI * 300 * t) + 5000 * sin(2.0 * PI * 2000 * t)
            val r = 9000 * sin(2.0 * PI * 300 * t) + 3500 * sin(2.0 * PI * 2000 * t) +
                2500 * sin(2.0 * PI * 5000 * t)
            (if (i % 2 == 0) l else r).roundToInt().toShort()
        }
        val floats = FloatArray(shorts.size) { shorts[it] / 32767f }

        val p16 = VocalCancelAudioProcessor()
        p16.configure(format(2, C.ENCODING_PCM_16BIT))
        p16.enabled = true
        val out16 = feed(p16, shorts)

        val pFloat = VocalCancelAudioProcessor()
        pFloat.configure(format(2, C.ENCODING_PCM_FLOAT))
        pFloat.enabled = true
        val outFloat = feedFloats(pFloat, floats)

        assertEquals(out16.size, outFloat.size)
        for (i in out16.indices) {
            assertTrue(
                "sample $i: 16-bit ${out16[i]} vs float ${outFloat[i]}",
                abs(out16[i].toInt() - outFloat[i].toInt()) <= 1,
            )
        }
    }

    @Test
    fun floatMultichannelConvertsExtraChannelsVerbatim() {
        val p = VocalCancelAudioProcessor()
        assertEquals(
            format(4, C.ENCODING_PCM_16BIT),
            p.configure(format(4, C.ENCODING_PCM_FLOAT)),
        )
        p.enabled = true
        val frames = 2000
        val input = FloatArray(frames * 4) { i ->
            val frame = i / 4
            when (i % 4) {
                0 -> (0.6 * sin(2.0 * PI * 1000 * frame / sampleRate)).toFloat()
                1 -> (0.3 * sin(2.0 * PI * 1000 * frame / sampleRate)).toFloat()
                2 -> (((frame * 7919) % 40001 - 20000) / 32767f)
                else -> ((20000 - (frame * 104729) % 40001) / 32767f)
            }
        }
        val out = feedFloats(p, input)
        for (f in 0 until frames) {
            // Extras: converted with the documented formula,
            // never cancelled.
            for (c in 2..3) {
                val expected = (input[f * 4 + c] * 32767f).roundToInt()
                    .coerceIn(-32768, 32767).toShort()
                assertEquals(expected, out[f * 4 + c])
            }
            assertEquals(out[f * 4], out[f * 4 + 1])
        }
    }

    @Test
    fun disabledIsByteForBytePassThrough() {
        val p = VocalCancelAudioProcessor()
        p.configure(format(2, C.ENCODING_PCM_16BIT))
        p.enabled = false
        p.queueInput(shortBuffer(1234, -987, 32767, -32768))
        assertEquals(
            listOf(1234.toShort(), (-987).toShort(), 32767.toShort(), (-32768).toShort()),
            drainShorts(p, 4),
        )
    }

    @Test
    fun resetClearsConfiguredFlag() {
        val p = VocalCancelAudioProcessor()
        p.configure(format(2, C.ENCODING_PCM_16BIT))
        assertTrue(p.configured)
        p.reset()
        assertFalse(p.configured)
        assertFalse(p.isActive)
    }

    @Test
    fun flushAndResetClearLowpassState() {
        val probe = sineStereo(60.0, 0.2, amplitude = 10000.0)
        val junk = sineStereo(200.0, 0.1, amplitude = 20000.0)

        val fresh = VocalCancelAudioProcessor()
        fresh.configure(format(2, C.ENCODING_PCM_16BIT))
        fresh.enabled = true
        val expected = feed(fresh, probe)

        // Pollute the filter with a loud low buffer, flush, and
        // the probe must come out exactly as on a fresh unit —
        // a seek must not smear the previous position's bass
        // into the new one.
        val flushed = VocalCancelAudioProcessor()
        flushed.configure(format(2, C.ENCODING_PCM_16BIT))
        flushed.enabled = true
        feed(flushed, junk)
        flushed.flush()
        assertEquals(expected, feed(flushed, probe))

        val reset = VocalCancelAudioProcessor()
        reset.configure(format(2, C.ENCODING_PCM_16BIT))
        reset.enabled = true
        feed(reset, junk)
        reset.reset()
        reset.configure(format(2, C.ENCODING_PCM_16BIT))
        reset.enabled = true
        assertEquals(expected, feed(reset, probe))
    }

    @Test
    fun centeredBassSurvivesCrossoverCancellation() {
        // 60 Hz sits far below the 180 Hz crossover: the old
        // full-band algorithm annihilates a centred bass tone
        // (measured: exactly 0 energy out), the crossover keeps
        // it (measured: 0.900 of the input energy in steady
        // state). Transient skipped: first half second.
        val input = sineStereo(60.0, 2.0)
        val p = VocalCancelAudioProcessor()
        p.configure(format(2, C.ENCODING_PCM_16BIT))
        p.enabled = true
        val out = feed(p, input)
        val frames = input.size / 2
        val from = sampleRate / 2
        val eIn = energy(input, from, frames)
        val eNew = energy(out, from, frames)
        val eOld = energy(classicCancel(input), from, frames)
        assertTrue("old algorithm must leave ~nothing, got ${eOld / eIn}", eOld <= 0.0001 * eIn)
        assertTrue("bass retention ${eNew / eIn} must be ≥ 0.70", eNew >= 0.70 * eIn)
    }

    @Test
    fun centeredBassRetentionBeatsClassicByOrdersOfMagnitude() {
        // A hair of channel imbalance (real mixes are never
        // bit-identical) gives the classic algorithm a finite
        // residual to ratio against: measured ≈ 8,800× the
        // retained energy (0.882 of input vs 0.0001). Pinned
        // conservatively at ≥ 1,000×.
        val input = sineStereo(60.0, 2.0, rightGain = 0.98)
        val p = VocalCancelAudioProcessor()
        p.configure(format(2, C.ENCODING_PCM_16BIT))
        p.enabled = true
        val out = feed(p, input)
        val frames = input.size / 2
        val from = sampleRate / 2
        val eNew = energy(out, from, frames)
        val eOld = energy(classicCancel(input), from, frames)
        assertTrue("ratio ${eNew / eOld} must be ≥ 1000", eNew >= 1000.0 * eOld)
    }

    @Test
    fun centered1kHzStillCancelsLikeClassic() {
        // 1 kHz is the vocal's home turf: the classic algorithm
        // leaves exactly 0 on a centred tone; the crossover's
        // one-pole skirt leaks a bounded slice of the lowpass
        // sum (measured: 0.0314 of input energy ≈ −15 dB).
        // Pinned as: residual ≤ classic + a small ABSOLUTE
        // tolerance (5% of input energy) — same league, and
        // the price of rescuing the bass, stated not hidden.
        val input = sineStereo(1000.0, 2.0)
        val p = VocalCancelAudioProcessor()
        p.configure(format(2, C.ENCODING_PCM_16BIT))
        p.enabled = true
        val out = feed(p, input)
        val frames = input.size / 2
        val from = sampleRate / 2
        val eIn = energy(input, from, frames)
        val eNew = energy(out, from, frames)
        val eOld = energy(classicCancel(input), from, frames)
        assertTrue("residual ${eNew / eIn} must be ≤ 0.05 of input", eNew <= 0.05 * eIn)
        assertTrue(eNew <= eOld + 0.05 * eIn)
    }

    @Test
    fun pannedContentPassesAsClassicHalf() {
        // Fully panned content (R = 0): LP + H = x per channel
        // makes the crossover output exactly the classic x/2 at
        // EVERY sample, transient included (±1 LSB of output
        // rounding). Nothing a mix puts off-centre may change
        // level versus the old algorithm.
        val frames = sampleRate / 2
        val input = ShortArray(frames * 2) { i ->
            if (i % 2 == 1) 0
            else (12000 * sin(2.0 * PI * 1000 * (i / 2) / sampleRate)).roundToInt().toShort()
        }
        val p = VocalCancelAudioProcessor()
        p.configure(format(2, C.ENCODING_PCM_16BIT))
        p.enabled = true
        val out = feed(p, input)
        for (f in 0 until frames) {
            assertEquals(out[f * 2], out[f * 2 + 1])
            val expected = input[f * 2].toDouble() / 2.0
            assertTrue(
                "frame $f: ${out[f * 2]} vs $expected",
                abs(out[f * 2].toDouble() - expected) <= 1.0,
            )
        }
    }

    @Test
    fun lowpassStateCarriesAcrossBufferBoundary() {
        // 90 Hz lives in the filter's skirts, where the state
        // matters most: feeding the same stream in two chunks
        // must produce the identical output as one chunk — no
        // seam, no click, no re-transient at the boundary.
        val input = sineStereo(90.0, 0.3, amplitude = 10000.0)
        val whole = VocalCancelAudioProcessor()
        whole.configure(format(2, C.ENCODING_PCM_16BIT))
        whole.enabled = true
        val outWhole = feed(whole, input)

        val splitAt = (input.size / 2 / 2) * 2 // frame-aligned, mid-stream
        val chunked = VocalCancelAudioProcessor()
        chunked.configure(format(2, C.ENCODING_PCM_16BIT))
        chunked.enabled = true
        val outChunked = feed(chunked, input.copyOfRange(0, splitAt)) +
            feed(chunked, input.copyOfRange(splitAt, input.size))
        assertEquals(outWhole, outChunked)
    }
}
