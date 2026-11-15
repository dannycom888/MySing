package com.dannycom888.shieldkaraoke.audio

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Owns the ExoPlayer and every remote-controlled playback feature:
 * pause/resume, replay, 原唱/伴奏 (vocal cancel), 升降调 (pitch),
 * and per-song loudness normalisation via LoudnessEnhancer.
 * Pitch resets to 原调 on every new song (KTV convention).
 */
class PlayerController(context: Context) {

    val vocalProcessor = VocalCancelAudioProcessor()

    /** Media3 1.8 has no Builder.setAudioSink; a custom sink is
     *  injected by subclassing DefaultRenderersFactory. */
    private class KaraokeRenderersFactory(
        context: Context,
        private val processor: VocalCancelAudioProcessor,
    ) : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(processor))
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }

    val player: ExoPlayer = ExoPlayer.Builder(context, KaraokeRenderersFactory(context, vocalProcessor))
        .build()

    private var loudnessEnhancer: LoudnessEnhancer? = null

    var onEnded: (() -> Unit)? = null

    var semitones: Int = 0
        private set

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded?.invoke()
            }
        })
    }

    fun load(uri: Uri, gainDb: Double) {
        semitones = 0
        player.playbackParameters = PlaybackParameters(1f, 1f)
        vocalProcessor.enabled = false
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        applyGain(gainDb)
        player.play()
    }

    private fun applyGain(gainDb: Double) {
        try {
            val sessionId = player.audioSessionId
            if (sessionId != 0) {
                if (loudnessEnhancer == null) loudnessEnhancer = LoudnessEnhancer(sessionId)
                loudnessEnhancer?.setTargetGain((gainDb * 100).toInt())
                loudnessEnhancer?.enabled = true
            }
        } catch (e: Exception) {
            // Enhancer unavailable: fall back to plain volume for cuts.
            if (gainDb < 0) player.volume = AudioMath.dbToLinear(gainDb).coerceIn(0f, 1f)
        }
    }

    /** Apply a freshly measured loudness gain to the playing song. */
    fun applyGainDb(gainDb: Double) = applyGain(gainDb)

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun replay() {
        player.seekTo(0)
        player.play()
    }

    fun setAccompaniment(on: Boolean) {
        vocalProcessor.enabled = on
    }

    val isAccompaniment: Boolean get() = vocalProcessor.enabled

    fun shiftPitch(deltaSemitones: Int) {
        semitones = (semitones + deltaSemitones).coerceIn(-12, 12)
        player.playbackParameters = PlaybackParameters(1f, AudioMath.semitoneFactor(semitones))
    }

    fun stop() {
        player.stop()
    }

    fun release() {
        loudnessEnhancer?.release()
        player.release()
    }
}
