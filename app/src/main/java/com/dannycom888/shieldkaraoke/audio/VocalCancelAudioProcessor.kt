package com.dannycom888.shieldkaraoke.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原唱/伴奏 switch, accompaniment side: classic centre-channel
 * cancellation ("OOPS") for 16-bit stereo PCM. The lead vocal in most
 * studio mixes sits dead centre, so (L−R) removes much of it while
 * off-centre instruments survive. Effect varies per song — that is
 * inherent to the technique and was explained to the user up front.
 * When disabled the processor is a byte-for-byte pass-through.
 */
class VocalCancelAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        val output = replaceOutputBuffer(size)
        if (!enabled) {
            output.put(inputBuffer)
        } else {
            // Belt and braces (v1.0.3): the DSP must never be able to
            // kill playback. Any failure falls back to passing the
            // original bytes through untouched.
            try {
                val inBuf = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                val outBuf = output.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                var i = 0
                while (i + 3 < size) {
                    val left = inBuf.getShort(i).toInt()
                    val right = inBuf.getShort(i + 2).toInt()
                    val mid = ((left - right) / 2).coerceIn(-32768, 32767).toShort()
                    outBuf.putShort(i, mid)
                    outBuf.putShort(i + 2, mid)
                    i += 4
                }
            } catch (e: Exception) {
                output.clear()
                output.put(inputBuffer.duplicate())
            }
            inputBuffer.position(inputBuffer.limit())
        }
        output.flip()
    }
}
