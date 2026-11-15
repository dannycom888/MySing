package com.dannycom888.shieldkaraoke.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Measures a song's RMS level (dBFS) by decoding its audio with
 * MediaCodec, at most the first [MAX_SECONDS] seconds — enough for a
 * stable loudness estimate. Returns null on any failure; the caller
 * then applies unity gain. Results are cached by the caller.
 */
object LoudnessAnalyzer {

    private const val MAX_SECONDS = 45

    fun measureRmsDbfs(context: Context, uri: Uri): Double? {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor().apply { setDataSource(context, uri, null) }
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    track = i; format = f; break
                }
            }
            if (track < 0 || format == null) return null
            extractor.selectTrack(track)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val maxSamples = sampleRate.toLong() * channels * MAX_SECONDS
            var sumSquares = 0.0
            var sampleCount = 0L
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone && sampleCount < maxSamples) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val read = extractor.readSampleData(inBuf, 0)
                        if (read < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, read, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        val le = outBuf.order(ByteOrder.LITTLE_ENDIAN)
                        var i = info.offset
                        val end = info.offset + info.size
                        while (i + 1 < end && sampleCount < maxSamples) {
                            val v = le.getShort(i).toDouble() / 32768.0
                            sumSquares += v * v
                            sampleCount++
                            i += 2
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            if (sampleCount == 0L) return null
            val rms = sqrt(sumSquares / sampleCount)
            if (rms <= 0.0) return null
            return 20.0 * log10(rms)
        } catch (e: Exception) {
            return null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            codec?.release()
            extractor?.release()
        }
    }
}
