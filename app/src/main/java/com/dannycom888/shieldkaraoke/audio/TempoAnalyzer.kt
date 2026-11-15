package com.dannycom888.shieldkaraoke.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Measures a song's tempo by decoding its audio with MediaCodec
 * (mirroring [LoudnessAnalyzer]) and running [BeatDetector] over an
 * RMS energy envelope at [FPS] frames per second, using at most the
 * first [MAX_SECONDS] seconds. Returns null on any failure; the
 * caller then dances at the default tempo. Results are cached by
 * the caller alongside the loudness cache.
 */
object TempoAnalyzer {

    private const val MAX_SECONDS = 60
    private const val FPS = 100

    fun measureBpm(context: Context, uri: Uri): Double? {
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
            if (sampleRate <= 0 || channels <= 0) return null
            val hopFrames = sampleRate / FPS // one envelope frame = 10 ms of audio
            if (hopFrames <= 0) return null
            val maxFrames = FPS * MAX_SECONDS
            val envelope = FloatArray(maxFrames)
            var frameIndex = 0
            var hopSumSquares = 0.0
            var hopCount = 0 // samples (all channels) in the current hop
            val hopTarget = hopFrames * channels
            var totalFrames = 0L
            val maxTotalFrames = sampleRate.toLong() * MAX_SECONDS
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone && totalFrames < maxTotalFrames && frameIndex < maxFrames) {
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
                        while (i + 1 < end && frameIndex < maxFrames) {
                            val v = le.getShort(i).toDouble() / 32768.0
                            hopSumSquares += v * v
                            hopCount++
                            i += 2
                            if (hopCount >= hopTarget) {
                                envelope[frameIndex++] = sqrt(hopSumSquares / hopCount).toFloat()
                                totalFrames += hopFrames
                                hopSumSquares = 0.0
                                hopCount = 0
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            if (frameIndex < FPS * 4) return null
            return BeatDetector.estimateBpm(envelope.copyOf(frameIndex), FPS)
        } catch (e: Exception) {
            return null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            codec?.release()
            extractor?.release()
        }
    }
}
