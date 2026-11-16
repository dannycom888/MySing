package com.dannycom888.shieldkaraoke.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
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
 *
 * v1.0.3 self-healing: if playback errors out (the custom AudioSink /
 * vocal-processor chain is the prime suspect), the player is rebuilt
 * ONCE with the plain default renderer and the song retried — see
 * [FallbackStateMachine]. The active sink path stays visible in
 * the diagnostics line (on the admin page since v1.0.35); the
 * error banner shows
 * while the fault is live and clears once a plain-retry recovery
 * is confirmed progressing (v1.0.15, see [ErrorDisplayPolicy]).
 * Music-stream audio focus is requested before playback and
 * abandoned on release.
 */
class PlayerController(private val context: Context) {

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

    // Declaration order matters: `player`'s initialiser calls
    // buildPlayer(), which references `listener` — everything the
    // initialisation chain touches must be declared BEFORE `player`.
    // (v1.0.3 crashed at startup with an NPE because `listener` was
    // declared after `player` and was still null in buildPlayer.)
    private val fallback = FallbackStateMachine()

    /** Whether [lastError] still deserves its red banner — a
     *  self-healed error clears once the retry is confirmed
     *  progressing (v1.0.15, see [ErrorDisplayPolicy]). */
    private val errorDisplay = ErrorDisplayPolicy()

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) onEnded?.invoke()
        }

        override fun onPlayerError(error: PlaybackException) {
            handleError(error)
        }
    }

    var player: ExoPlayer = buildPlayer(customSink = true)
        private set

    private var loudnessEnhancer: LoudnessEnhancer? = null

    var onEnded: (() -> Unit)? = null

    var semitones: Int = 0
        private set

    /** Last playback error (code name + first message line) this
     *  song; null when none. Whether it is currently displayed is
     *  [visibleError]'s call, not this field's. */
    var lastError: String? = null
        private set

    /**
     * The error text for the red banner right now, or null when the
     * banner should be hidden. The banner survives a real failure
     * (FAILED path) but clears once a plain-retry recovery is
     * confirmed progressing — see [ErrorDisplayPolicy]. Reads the
     * live player, so call it from a poll loop, not composition.
     */
    fun visibleError(): String? {
        val err = lastError ?: return null
        val p = player
        val show = errorDisplay.shouldShowError(
            hasError = true,
            path = fallback.path,
            ready = p.playbackState == Player.STATE_READY,
            playing = p.isPlaying,
            positionMs = try { p.currentPosition } catch (e: Exception) { 0L },
        )
        return if (show) err else null
    }

    /** Which audio path is live: "custom" / "plain-retry" / "plain-failed". */
    val sinkLabel: String get() = fallback.sinkLabel

    /** True when accompaniment was requested but the live sink is the
     *  plain retry path, which has no vocal processor — the UI shows
     *  a visible degradation note instead of failing silently. */
    var accompanimentUnavailable: Boolean = false
        private set

    private var currentUri: Uri? = null
    private var currentGainDb: Double = 0.0
    private var wantAccompaniment: Boolean = false

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { /* state only */ }

    private fun buildPlayer(customSink: Boolean): ExoPlayer {
        val built = if (customSink) {
            ExoPlayer.Builder(context, KaraokeRenderersFactory(context, vocalProcessor)).build()
        } else {
            ExoPlayer.Builder(context).build()
        }
        built.addListener(listener)
        return built
    }

    fun load(uri: Uri, gainDb: Double) {
        // A fresh song gets a fresh chance on the custom sink.
        if (fallback.path != FallbackStateMachine.Path.CUSTOM) {
            rebuildPlayer(customSink = true)
        }
        fallback.onNewLoad()
        lastError = null
        errorDisplay.reset()
        accompanimentUnavailable = false
        wantAccompaniment = false
        currentUri = uri
        currentGainDb = gainDb
        semitones = 0
        player.playbackParameters = PlaybackParameters(1f, 1f)
        vocalProcessor.enabled = false
        requestAudioFocus()
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        applyGain(gainDb)
        player.play()
    }

    /** First error on the custom sink → rebuild plain, retry once. */
    private fun handleError(error: PlaybackException) {
        lastError = buildString {
            append(error.errorCodeName)
            val firstLine = error.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
            if (firstLine.isNotEmpty()) append(": ").append(firstLine)
        }
        // A fresh error re-arms the banner until this error's own
        // recovery (if any) is confirmed progressing.
        errorDisplay.reset()
        if (!fallback.onError()) return // second error: stay put, error stays visible
        val uri = currentUri ?: return
        val resumeAt = try { player.currentPosition } catch (e: Exception) { 0L }
        rebuildPlayer(customSink = false)
        if (wantAccompaniment) accompanimentUnavailable = true
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        if (resumeAt > 0) player.seekTo(resumeAt)
        player.playbackParameters = PlaybackParameters(1f, AudioMath.semitoneFactor(semitones))
        applyGain(currentGainDb)
        player.play()
    }

    private fun rebuildPlayer(customSink: Boolean) {
        val old = player
        old.removeListener(listener)
        try { old.stop() } catch (_: Exception) {}
        old.release()
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        player = buildPlayer(customSink)
    }

    private fun requestAudioFocus() {
        try {
            if (focusRequest == null) {
                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(focusListener)
                    .build()
            }
            audioManager.requestAudioFocus(focusRequest!!)
        } catch (_: Exception) {
            // Focus is best-effort; playback must not depend on it.
        }
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
        wantAccompaniment = on
        if (fallback.path == FallbackStateMachine.Path.CUSTOM) {
            vocalProcessor.enabled = on
            accompanimentUnavailable = false
        } else {
            // Plain retry sink has no vocal processor: degrade visibly.
            vocalProcessor.enabled = false
            accompanimentUnavailable = on
        }
    }

    val isAccompaniment: Boolean
        get() = fallback.path == FallbackStateMachine.Path.CUSTOM && vocalProcessor.enabled

    fun shiftPitch(deltaSemitones: Int) {
        semitones = (semitones + deltaSemitones).coerceIn(-12, 12)
        player.playbackParameters = PlaybackParameters(1f, AudioMath.semitoneFactor(semitones))
    }

    fun stop() {
        player.stop()
    }

    fun release() {
        try {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } catch (_: Exception) {}
        loudnessEnhancer?.release()
        player.release()
    }
}
