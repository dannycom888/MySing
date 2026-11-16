package com.dannycom888.shieldkaraoke.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The vocal-cancel processor must accept the formats decoders
 * actually emit — 16-bit AND float PCM, any channel count ≥ 2 —
 * instead of returning NOT_SET and being bypassed (the user's
 * "原唱和伴奏没什么区别": on his songs the switch did nothing
 * because the processor never engaged). The (L−R)/2 algorithm
 * itself is unchanged and pinned here sample-exact.
 *
 * v1.0.40 rewrites (noted per the version's 口径 rules): the
 * declared output format is now ALWAYS 16-bit PCM — float input
 * is converted to 16-bit samples on the way in and cancelled in
 * the same short domain, so the chain never hands float to the
 * sink. The v1.0.38 tests that pinned float-in/float-out
 * (`accepts16BitStereoAndFloatStereo`'s float expectation and
 * `cancelFloatMatches16BitInFloatDomain`) are rewritten to the
 * 16-bit-output 口径, and dedicated conversion tests are added.
 */
class VocalCancelAudioProcessorTest {

    private fun format(channels: Int, encoding: Int) =
        AudioProcessor.AudioFormat(44100, channels, encoding)

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
    fun acceptsMultichannelAndCancelsFirstTwoChannelsOnly() {
        val p = VocalCancelAudioProcessor()
        assertEquals(
            format(4, C.ENCODING_PCM_16BIT),
            p.configure(format(4, C.ENCODING_PCM_16BIT)),
        )
        p.enabled = true
        // One 4-channel frame: L=1000, R=200, rear 777 / -555 pass through.
        p.queueInput(shortBuffer(1000, 200, 777, -555))
        assertEquals(listOf(400.toShort(), 400.toShort(), 777.toShort(), (-555).toShort()), drainShorts(p, 4))
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
    fun cancel16BitIsClassicCentreCancellation() {
        val p = VocalCancelAudioProcessor()
        p.configure(format(2, C.ENCODING_PCM_16BIT))
        p.enabled = true
        // (1000-200)/2=400; (-1000-1000)/2=-1000; centre-only signal vanishes.
        p.queueInput(shortBuffer(1000, 200, -1000, 1000, 500, 500))
        assertEquals(
            listOf(400.toShort(), 400.toShort(), (-1000).toShort(), (-1000).toShort(), 0.toShort(), 0.toShort()),
            drainShorts(p, 6),
        )
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
    fun cancelFloatConvertsThenCancelsIn16BitDomain() {
        val p = VocalCancelAudioProcessor()
        p.configure(format(2, C.ENCODING_PCM_FLOAT))
        p.enabled = true
        // Converted first (1.0f→32767, 0.0f→0), then the same
        // short-domain (L−R)/2 as native 16-bit input:
        // (32767-0)/2=16383; (0-32767)/2=-16383; centre vanishes.
        p.queueInput(floatBuffer(1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f))
        assertEquals(
            listOf(16383.toShort(), 16383.toShort(), (-16383).toShort(), (-16383).toShort(), 0.toShort(), 0.toShort()),
            drainShorts(p, 6),
        )
    }

    @Test
    fun floatMultichannelConvertsExtraChannelsVerbatim() {
        val p = VocalCancelAudioProcessor()
        assertEquals(
            format(4, C.ENCODING_PCM_16BIT),
            p.configure(format(4, C.ENCODING_PCM_FLOAT)),
        )
        p.enabled = true
        // One 4-channel float frame: L/R cancel as 16-bit,
        // rear channels are only converted, never cancelled.
        p.queueInput(floatBuffer(1.0f, 0.0f, -1.0f, 0.0f))
        assertEquals(
            listOf(16383.toShort(), 16383.toShort(), (-32767).toShort(), 0.toShort()),
            drainShorts(p, 4),
        )
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
}
