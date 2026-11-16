package com.dannycom888.shieldkaraoke.ui

import com.dannycom888.shieldkaraoke.i18n.UiLang

/**
 * TV voice search (v1.0.46): the pure decisions behind the browse
 * page's 🎤 button, split out of the Android glue so they can be
 * unit-tested. The recognition itself is the device's own speech
 * service (SpeechRecognizer) — device-side capability, not faked
 * here; this object only decides the language to ask for, what
 * state the button is in, and what a result list does to the
 * browse page's search query.
 *
 * The microphone here hears search commands only. The singing
 * microphone is a separate hardware chain (mic → amp → speaker)
 * the app never touches; nothing in this feature records singing
 * and there is no scoring, by design.
 */
object VoiceSearch {

    /** Recognition language tag for the app's current UI language:
     *  the TV speaks the language the host picked on the banner. */
    fun languageTag(lang: UiLang): String = if (lang == UiLang.EN) "en-US" else "zh-CN"

    /** The voice button's state, from the one device fact that
     *  decides it: whether a recognition service exists at all
     *  (SpeechRecognizer.isRecognitionAvailable, read by the UI). */
    enum class ButtonState { READY, NO_SERVICE }

    fun buttonState(recognitionAvailable: Boolean): ButtonState =
        if (recognitionAvailable) ButtonState.READY else ButtonState.NO_SERVICE

    fun buttonEnabled(recognitionAvailable: Boolean): Boolean =
        buttonState(recognitionAvailable) == ButtonState.READY

    /**
     * The first usable candidate from the recognizer's result
     * list — the service ranks its guesses best-first, so the
     * first non-blank one wins. Null when nothing usable was
     * heard (null/empty/all-blank results).
     */
    fun firstCandidate(results: List<String>?): String? =
        results?.firstOrNull { it.isNotBlank() }?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * What a finished recognition does to the browse search query:
     * a heard candidate replaces the query (it stays editable —
     * the host can fix a misheard character in the search field);
     * silence leaves the current query untouched.
     */
    fun queryAfterResult(current: String, results: List<String>?): String =
        firstCandidate(results) ?: current
}
