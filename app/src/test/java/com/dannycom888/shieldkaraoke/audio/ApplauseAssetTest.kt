package com.dannycom888.shieldkaraoke.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Guards the applause sound (v1.0.16): a real crowd recording, not a
 * synthesis — public-domain "Slow starting applause" (pdsounds, via
 * Wikimedia Commons), cut at 12 s for 3.8 s with fade-in/out and
 * loudness aligned to song level, picked by the user by ear over the
 * v1.0.14 synthetic version. The asset must exist, decode as
 * 44.1 kHz / 16-bit / mono WAV, run ~3.8 s so it lines up with
 * APPLAUSE_MS on the TV, sit at a song-like level, and end with its
 * tail faded to near silence — never guillotined.
 */
class ApplauseAssetTest {

    private fun assetFile(): File {
        val candidates = listOf(
            File("src/main/assets/applause.wav"), // unit tests run in the app module dir
            File("app/src/main/assets/applause.wav"), // …or the project root
        )
        return candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("applause.wav not found; tried ${candidates.map { it.absolutePath }}")
    }

    private class Wav(val channels: Int, val sampleRate: Int, val bitsPerSample: Int, val samples: DoubleArray)

    private fun readWav(file: File): Wav {
        RandomAccessFile(file, "r").use { raf ->
            val bytes = ByteArray(raf.length().toInt())
            raf.readFully(bytes)
            fun ascii(at: Int) = String(bytes, at, 4, Charsets.US_ASCII)
            fun u32(at: Int) = (bytes[at].toInt() and 0xFF) or
                ((bytes[at + 1].toInt() and 0xFF) shl 8) or
                ((bytes[at + 2].toInt() and 0xFF) shl 16) or
                ((bytes[at + 3].toInt() and 0xFF) shl 24)
            fun u16(at: Int) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
            assertEquals("RIFF", ascii(0))
            assertEquals("WAVE", ascii(8))
            var channels = -1
            var sampleRate = -1
            var bits = -1
            var dataAt = -1
            var dataLen = 0
            var at = 12
            while (at + 8 <= bytes.size) {
                val size = u32(at + 4)
                when (ascii(at)) {
                    "fmt " -> {
                        assertEquals(1, u16(at + 8)) // audio format 1 = PCM
                        channels = u16(at + 10)
                        sampleRate = u32(at + 12)
                        bits = u16(at + 22)
                    }
                    "data" -> {
                        dataAt = at + 8
                        dataLen = size
                    }
                }
                at += 8 + size + (size and 1)
            }
            assertTrue("fmt chunk missing", channels > 0)
            assertTrue("data chunk missing", dataAt >= 0)
            val count = dataLen / 2
            val samples = DoubleArray(count) { i ->
                val lo = bytes[dataAt + 2 * i].toInt() and 0xFF
                val hi = bytes[dataAt + 2 * i + 1].toInt()
                ((hi shl 8) or lo) / 32768.0
            }
            return Wav(channels, sampleRate, bits, samples)
        }
    }

    @Test
    fun applauseAssetDecodesWithExpectedFormat() {
        val wav = readWav(assetFile())
        assertEquals(1, wav.channels)
        assertEquals(44100, wav.sampleRate)
        assertEquals(16, wav.bitsPerSample)
        val durationS = wav.samples.size.toDouble() / wav.sampleRate
        assertTrue("duration $durationS s outside 3.7-3.9 s", durationS in 3.7..3.9)
    }

    @Test
    fun applauseLevelIsSongLikeAndTailIsNotCut() {
        val wav = readWav(assetFile())
        val s = wav.samples
        val rms = sqrt(s.sumOf { it * it } / s.size)
        val rmsDb = 20 * log10(rms)
        // The delivered master measures -16.2 dBFS RMS / -1 dB peak;
        // the bands below leave honest headroom either way while
        // still catching a wrong or unlevelled file.
        assertTrue("RMS $rmsDb dBFS outside -20..-13", rmsDb in -20.0..-13.0)
        val peak = s.maxOf { abs(it) }
        assertTrue("peak $peak outside 0.75..0.98", peak in 0.75..0.98)
        // The applause dies away naturally: the last 0.8 s is much
        // quieter on average than the full middle (individual late
        // claps may still pop — that's what a real room sounds like).
        val tail = s.sliceArray(s.size - (0.8 * wav.sampleRate).toInt() until s.size)
        val mid = s.sliceArray(wav.sampleRate until 2 * wav.sampleRate)
        val tailRms = sqrt(tail.sumOf { it * it } / tail.size)
        val midRms = sqrt(mid.sumOf { it * it } / mid.size)
        assertTrue("tail RMS $tailRms not thinner than mid RMS $midRms", tailRms < midRms * 0.7)
        // And it lands on near silence: the final 100 ms averages
        // below -35 dBFS and the last 30 ms peaks under 0.02, so the
        // fade-out is never guillotined mid-clap.
        val end100 = s.sliceArray(s.size - (0.1 * wav.sampleRate).toInt() until s.size)
        val end100Db = 20 * log10(sqrt(end100.sumOf { it * it } / end100.size))
        assertTrue("final 100 ms RMS $end100Db dBFS not near silence", end100Db < -35.0)
        val endMax = s.sliceArray(s.size - (0.03 * wav.sampleRate).toInt() until s.size).maxOf { abs(it) }
        assertTrue("final 30 ms max $endMax not silent", endMax < 0.02)
    }
}
