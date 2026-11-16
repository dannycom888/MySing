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
 * [FallbackStateMachine]. v1.0.38 briefly made the retry use the
 * same custom factory build as the first attempt; on the user's
 * device the custom-sink build has failed first-try all along
 * (every field screenshot showed sink=plain-retry), so both
 * attempts died identically and playback was completely dead.
 * v1.0.40 restores the two different builds: the bare retry is
 * the lifeboat and stays bare (accompaniment is unavailable on
 * it, and says so — see [accompanimentUnavailable]).
 * The active sink path stays visible in
 * the diagnostics line (on the admin page since v1.0.35); the
 * error banner shows
 * while the fault is live and clears once a plain-retry recovery
 * is confirmed progressing (v1.0.15, see [ErrorDisplayPolicy]).
 * Music-stream audio focus is requested before playback and
 * abandoned on release.
 *
 * v1.0.47: a song may carry a paired accompaniment FILE (see
 * [com.dannycom888.shieldkaraoke.library.AccompanimentPairing]).
 * When it does, 原唱/伴奏 switching is a media-source swap
 * between the two files at the current position — no processor
 * involved — so file accompaniment works on EVERY build,
 * including the bare plain retry. [PlaybackSourcePolicy] makes
 * the choice; this class only executes it. Songs without a file
 * keep the realtime path below, untouched.
 */
class PlayerController(private val context: Context) {

    val vocalProcessor = VocalCancelAudioProcessor()

    /** Media3 1.8 has no Builder.setAudioSink; a custom sink is
     *  injected by subclassing DefaultRenderersFactory. Only the
     *  FIRST build goes through this factory; the error retry is
     *  a stock bare build with no custom sink and no processor
     *  (v1.0.40 — see the class doc for why it must stay bare). */
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

    /** True when accompaniment was requested but is genuinely
     *  not in effect for the current song. Two real causes, and
     *  the diagnostics line names the one that applies:
     *   - the live build is the bare plain retry (v1.0.40), whose
     *     chain has no vocal processor at all; or
     *   - the custom sink configured the processor and it
     *     rejected this song's format (mono, or an encoding other
     *     than 16-bit/float PCM).
     *  On the custom path before the sink has configured the
     *  processor for the song it reads false: "not known yet" is
     *  not a degradation.
     *  v1.0.47: a song playing its paired accompaniment FILE is
     *  never "unavailable" — the file needs no processor, so it
     *  is in effect on every build, the bare retry included. */
    val accompanimentUnavailable: Boolean
        get() = wantAccompaniment && !usingAccompanimentFile &&
            (fallback.path != FallbackStateMachine.Path.CUSTOM ||
                (vocalProcessor.configured && !vocalProcessor.isActive()))

    private var currentUri: Uri? = null
    private var currentGainDb: Double = 0.0
    private var wantAccompaniment: Boolean = false

    /** The current song's two files (v1.0.47): the original, and
     *  its paired accompaniment file when the scan found one. */
    private var originalUri: Uri? = null
    private var accompanimentFileUri: Uri? = null

    /** True while the media item under the singer is the paired
     *  accompaniment file rather than the original. */
    var usingAccompanimentFile: Boolean = false
        private set

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { /* state only */ }

    private fun buildPlayer(customSink: Boolean): ExoPlayer {
        // Two builds (v1.0.40, restoring the pre-v1.0.38 shape):
        // the first attempt carries the vocal processor via the
        // custom factory; the error retry is a stock bare build —
        // DefaultRenderersFactory with no custom sink and no
        // processor — so a fault in the custom chain can never
        // take the retry down with it.
        val built = if (customSink) {
            ExoPlayer.Builder(context, KaraokeRenderersFactory(context, vocalProcessor)).build()
        } else {
            ExoPlayer.Builder(context, DefaultRenderersFactory(context)).build()
        }
        built.addListener(listener)
        return built
    }

    fun load(
        uri: Uri,
        gainDb: Double,
        accompanimentByDefault: Boolean,
        accompanimentUri: Uri? = null,
    ) {
        // A fresh song gets a fresh chance on the custom sink.
        if (fallback.path != FallbackStateMachine.Path.CUSTOM) {
            rebuildPlayer(customSink = true)
        }
        fallback.onNewLoad()
        lastError = null
        errorDisplay.reset()
        originalUri = uri
        accompanimentFileUri = accompanimentUri
        currentGainDb = gainDb
        semitones = 0
        player.playbackParameters = PlaybackParameters(1f, 1f)
        // Per-song accompaniment default (v1.0.44): the caller
        // derives it from the entry's source via
        // AccompanimentPolicy — ordered songs start with
        // accompaniment on, filler with the original vocal.
        // v1.0.47: what that intent PLAYS is PlaybackSourcePolicy's
        // call — a paired accompaniment file wins and the song
        // starts on the file itself; without a file the realtime
        // processor is switched to match. Load always lands on
        // the custom build first (rebuilt above when needed), so
        // the processor can be set directly; and if an error
        // later drops this song to the bare retry, the remembered
        // request — and, for file songs, the file itself — keeps
        // accompanimentUnavailable truthful.
        wantAccompaniment = accompanimentByDefault
        val choice = PlaybackSourcePolicy.choose(accompanimentUri != null, accompanimentByDefault)
        usingAccompanimentFile = choice.useAccompanimentFile
        vocalProcessor.enabled = choice.useRealtimeCancel
        currentUri = if (choice.useAccompanimentFile) accompanimentUri else uri
        requestAudioFocus()
        player.setMediaItem(MediaItem.fromUri(currentUri!!))
        player.prepare()
        applyGain(gainDb)
        player.play()
    }

    /** First error on the custom sink → rebuild bare (stock
     *  factory, no processor), retry once, on whichever file is
     *  current. If accompaniment was on via the realtime
     *  processor, [accompanimentUnavailable] turns true via the
     *  path — the retry audibly plays the original until the
     *  next song gets its fresh chance on the custom sink. If
     *  accompaniment was on via the paired FILE (v1.0.47), the
     *  retry simply keeps playing that file — the bare build
     *  needs no processor for it. */
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
        val choice = PlaybackSourcePolicy.choose(accompanimentFileUri != null, on)
        if (choice.useAccompanimentFile != usingAccompanimentFile) {
            // The source itself changes (v1.0.47): swap the media
            // item under the singer at the current position. This
            // is deliberately NOT tied to the custom sink — a file
            // swap works on the bare retry build too, which is
            // the whole point of file accompaniment.
            swapSource(choice.useAccompanimentFile)
        } else if (fallback.path == FallbackStateMachine.Path.CUSTOM) {
            // No file involved: the realtime processor switches,
            // exactly as before.
            vocalProcessor.enabled = choice.useRealtimeCancel
        } else {
            // Plain retry build has no vocal processor in its
            // chain: nothing to switch. The request is remembered
            // (wantAccompaniment) and [accompanimentUnavailable]
            // reports the degradation visibly.
            vocalProcessor.enabled = false
        }
    }

    /** Replace the playing file with the song's other file at the
     *  current position — same LRC, same progress, same pitch;
     *  only the audio under the singing changes. The position is
     *  clamped to the new file's duration when it is already
     *  known (the two files are sample-exact by construction,
     *  but a shorter file must never be seeked past its end). */
    private fun swapSource(toAccompanimentFile: Boolean) {
        val target = (if (toAccompanimentFile) accompanimentFileUri else originalUri) ?: return
        usingAccompanimentFile = toAccompanimentFile
        // A file source never runs through the cancel processor:
        // the accompaniment file is already vocal-free, and the
        // original must come back exactly as it was recorded.
        vocalProcessor.enabled = false
        val wasPlayWhenReady = player.playWhenReady
        val position = try { player.currentPosition } catch (e: Exception) { 0L }
        currentUri = target
        player.setMediaItem(MediaItem.fromUri(target))
        player.prepare()
        val duration = try { player.duration } catch (e: Exception) { 0L }
        player.seekTo(
            if (duration > 0) minOf(position, duration) else position.coerceAtLeast(0L)
        )
        player.playbackParameters = PlaybackParameters(1f, AudioMath.semitoneFactor(semitones))
        applyGain(currentGainDb)
        player.playWhenReady = wasPlayWhenReady
    }

    /** Accompaniment audibly in effect: either the paired file
     *  is playing, or — on the custom build — the processor is
     *  switched on. (A custom-path format rejection still leaves
     *  the processor case true — the audible truth for that case
     *  is [accompanimentUnavailable]'s.) */
    val isAccompaniment: Boolean
        get() = usingAccompanimentFile ||
            (fallback.path == FallbackStateMachine.Path.CUSTOM && vocalProcessor.enabled)

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
