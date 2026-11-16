package com.dannycom888.shieldkaraoke.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

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
 * player build did not carry the processor at all.
 *
 * v1.0.40 output rule: the declared output format is ALWAYS
 * 16-bit PCM (same sample rate and channel count as the input).
 * 16-bit input is processed exactly as before; float input is
 * converted to 16-bit samples on the way in and then runs
 * through the very same short-domain (L−R)/2, so our chain
 * never hands float to the sink. (Before v1.0.38 a float stream
 * took the sink's own conversion path after this processor's
 * NOT_SET bypass; v1.0.38 passed float through our chain — a
 * new variable on a device whose custom-sink build already
 * fails first-try for other reasons. v1.0.40 removes float
 * from our chain entirely.)
 *  - any channel count ≥ 2 is accepted — the first two channels
 *    get the cancellation, any further channels (5.1 rears etc.)
 *    pass through (converted, if float) untouched;
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

    /** The encoding the DECODER hands us (16-bit or float); the
     *  output we declare and emit is always 16-bit (v1.0.40). */
    private var inputEncoding: Int = C.ENCODING_PCM_16BIT
    private var channelCount: Int = 2

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        configured = true
        val supported = (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) &&
            inputAudioFormat.channelCount >= 2
        if (!supported) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        inputEncoding = inputAudioFormat.encoding
        channelCount = inputAudioFormat.channelCount
        // Output is ALWAYS 16-bit PCM (v1.0.40): float input is
        // converted inside queueInput, never passed downstream.
        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            inputAudioFormat.channelCount,
            C.ENCODING_PCM_16BIT,
        )
    }

    override fun onReset() {
        configured = false
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (inputEncoding == C.ENCODING_PCM_FLOAT) {
            queueFloatInput(inputBuffer)
            return
        }
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
                cancel16(inBuf, outBuf, size)
                // The cancel loop writes with ABSOLUTE puts, which
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

    /**
     * Float input (v1.0.40): every sample is converted to 16-bit
     * on the way in — verbatim when disabled, and through the
     * same short-domain (L−R)/2 as native 16-bit input when
     * enabled — so the emitted bytes always match the 16-bit
     * output format declared in [onConfigure]. Only whole float
     * samples are converted; trailing partial-sample bytes
     * (decoders do not emit them in practice) are dropped.
     * Belt and braces as ever: any failure emits silence in the
     * declared format rather than wrong-format bytes.
     */
    private fun queueFloatInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        val sampleCount = size / 4
        val outSize = sampleCount * 2
        val output = replaceOutputBuffer(outSize)
        try {
            val inBuf = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val outBuf = output.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            var s = 0
            if (enabled) {
                while (s + channelCount <= sampleCount) {
                    val left = floatToShort(inBuf.getFloat(s * 4)).toInt()
                    val right = floatToShort(inBuf.getFloat((s + 1) * 4)).toInt()
                    val mid = ((left - right) / 2).coerceIn(-32768, 32767).toShort()
                    outBuf.putShort(s * 2, mid)
                    outBuf.putShort((s + 1) * 2, mid)
                    var c = 2
                    while (c < channelCount) {
                        outBuf.putShort((s + c) * 2, floatToShort(inBuf.getFloat((s + c) * 4)))
                        c++
                    }
                    s += channelCount
                }
            }
            // Disabled, or the trailing partial frame: convert
            // every remaining sample verbatim.
            while (s < sampleCount) {
                outBuf.putShort(s * 2, floatToShort(inBuf.getFloat(s * 4)))
                s++
            }
            // Absolute puts again: publish [0, outSize), not an
            // empty buffer (the pre-v1.0.38 defect).
            output.position(outSize)
        } catch (e: Exception) {
            output.clear()
            for (i in 0 until outSize) output.put(i, 0)
            output.position(outSize)
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    /** Float sample → 16-bit sample, clamped to the short range. */
    private fun floatToShort(f: Float): Short =
        (f * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()

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

}
