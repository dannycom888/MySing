package com.dannycom888.shieldkaraoke.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原唱/伴奏 switch, accompaniment side: classic centre-channel
 * cancellation ("OOPS"). The lead vocal in most studio mixes sits
 * dead centre, so (L−R) removes much of it while off-centre
 * instruments survive. Effect varies per song — that is inherent
 * to the technique and was explained to the user up front.
 * When disabled the processor is a byte-for-byte pass-through.
 *
 * v1.0.38 format robustness (the user: 原唱和伴奏没什么区别 —
 * on his songs the switch did nothing): until now this processor
 * accepted ONLY 16-bit stereo PCM and returned NOT_SET for
 * anything else, so the sink bypassed it entirely whenever the
 * decoder handed over a different format, and the plain-retry
 * player build did not carry the processor at all. Now:
 *  - 16-bit PCM and float PCM are both accepted, converted and
 *    processed in place, output format identical to the input;
 *  - any channel count ≥ 2 is accepted — the first two channels
 *    get the cancellation, any further channels (5.1 rears etc.)
 *    pass through untouched;
 *  - mono and non-PCM encodings still return NOT_SET, and
 *    [configured] + [isActive] let the controller report that
 *    truthfully (see PlayerController.accompanimentUnavailable)
 *    instead of the toggle pretending.
 * The (L−R)/2 algorithm itself is unchanged — measured on the
 * user's own library files it leaves only 18–37% residual
 * energy; the bug was delivery, not strength.
 */
class VocalCancelAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    /**
     * Whether the sink has configured this processor for the
     * current stream at all. Distinguishes "not configured yet"
     * (accompaniment availability unknown — do not cry wolf) from
     * "configured and rejected" ([isActive] false — accompaniment
     * genuinely unavailable for this song's format). Cleared on
     * reset, set on every configure.
     */
    @Volatile
    var configured: Boolean = false
        private set

    private var encoding: Int = C.ENCODING_PCM_16BIT
    private var channelCount: Int = 2

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        configured = true
        val supported = (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) &&
            inputAudioFormat.channelCount >= 2
        if (!supported) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        encoding = inputAudioFormat.encoding
        channelCount = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun onReset() {
        configured = false
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
                when (encoding) {
                    C.ENCODING_PCM_16BIT -> cancel16(inBuf, outBuf, size)
                    C.ENCODING_PCM_FLOAT -> cancelFloat(inBuf, outBuf, size)
                    else -> outBuf.put(inBuf) // unreachable: onConfigure gates the formats
                }
                // The cancel loops write with ABSOLUTE puts, which
                // do not advance the buffer position — without this
                // the flip() below would publish an EMPTY buffer
                // (the pre-v1.0.38 code had exactly this defect:
                // on the custom path, enabling accompaniment
                // produced no audio at all).
                output.position(size)
            } catch (e: Exception) {
                output.clear()
                output.put(inputBuffer.duplicate())
            }
            inputBuffer.position(inputBuffer.limit())
        }
        output.flip()
    }

    /** (L−R)/2 into channels 0/1 of every 16-bit frame; channels
     *  2+ copied verbatim. Trailing partial-frame bytes (decoders
     *  do not emit them in practice) are copied verbatim too. */
    private fun cancel16(input: ByteBuffer, output: ByteBuffer, size: Int) {
        val frameBytes = channelCount * 2
        var i = 0
        while (i + frameBytes <= size) {
            val left = input.getShort(i).toInt()
            val right = input.getShort(i + 2).toInt()
            val mid = ((left - right) / 2).coerceIn(-32768, 32767).toShort()
            output.putShort(i, mid)
            output.putShort(i + 2, mid)
            var c = 2
            while (c < channelCount) {
                output.putShort(i + c * 2, input.getShort(i + c * 2))
                c++
            }
            i += frameBytes
        }
        while (i < size) {
            output.put(i, input.get(i))
            i++
        }
    }

    /** Float twin of [cancel16]: same (L−R)/2 in the float
     *  domain, so float-output chains cancel exactly like 16-bit
     *  ones and the output format stays float. */
    private fun cancelFloat(input: ByteBuffer, output: ByteBuffer, size: Int) {
        val frameBytes = channelCount * 4
        var i = 0
        while (i + frameBytes <= size) {
            val left = input.getFloat(i)
            val right = input.getFloat(i + 4)
            val mid = (left - right) / 2f
            output.putFloat(i, mid)
            output.putFloat(i + 4, mid)
            var c = 2
            while (c < channelCount) {
                output.putFloat(i + c * 4, input.getFloat(i + c * 4))
                c++
            }
            i += frameBytes
        }
        while (i < size) {
            output.put(i, input.get(i))
            i++
        }
    }
}
