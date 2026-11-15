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
 * Guards the applause sound (v1.0.14): the asset must exist, decode as
 * the 44.1 kHz / 16-bit / mono WAV the generator script
 * (audio/make_applause.py) produces, run 3.5-4.0 s so it lines up
 * with APPLAUSE_MS on the TV, sit at a song-like level, and end with
 * its thinning tail faded to near silence — never guillotined.
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
        assertTrue("duration $durationS s outside 3.5-4.0 s", durationS in 3.5..4.0)
    }

    @Test
    fun applauseLevelIsSongLikeAndTailIsNotCut() {
        val wav = readWav(assetFile())
        val s = wav.samples
        val rms = sqrt(s.sumOf { it * it } / s.size)
        val rmsDb = 20 * log10(rms)
        assertTrue("RMS $rmsDb dBFS outside -24..-10", rmsDb in -24.0..-10.0)
        val peak = s.maxOf { abs(it) }
        assertTrue("peak $peak outside 0.5..1.0", peak in 0.5..1.0)
        // The tail thins out: the last 0.8 s is much quieter on
        // average than the full middle (individual late claps may
        // still be loud — that's what a real room sounds like).
        val tail = s.sliceArray(s.size - (0.8 * wav.sampleRate).toInt() until s.size)
        val mid = s.sliceArray(wav.sampleRate until 2 * wav.sampleRate)
        val tailRms = sqrt(tail.sumOf { it * it } / tail.size)
        val midRms = sqrt(mid.sumOf { it * it } / mid.size)
        assertTrue("tail RMS $tailRms not thinner than mid RMS $midRms", tailRms < midRms * 0.7)
        // And it lands on silence: the final fade reaches ~zero, so
        // the tail is never guillotined mid-clap.
        val endMax = s.sliceArray(s.size - (0.03 * wav.sampleRate).toInt() until s.size).maxOf { abs(it) }
        assertTrue("final 30 ms max $endMax not silent", endMax < 0.02)
    }
}
