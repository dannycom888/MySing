package com.dannycom888.shieldkaraoke.audio

/**
 * Renders a playback error for the diagnostics line and the error
 * banner.
 *
 * Until v1.0.57 this was just "error code name + first message
 * line" — the cause chain was thrown away, which is exactly the
 * part that names the root fault (on the user's tablet the top
 * message was the content-free "Unexpected runtime error" of
 * ERROR_CODE_FAILED_RUNTIME_CHECK; whatever actually broke sat
 * in a cause nobody kept). v1.0.58 keeps the chain: the code name
 * and top message first, then each cause in turn, class name and
 * first message line apiece.
 *
 * The result is hard-capped at [MAX_CHARS]: the diagnostics line
 * is one line of small print, and a pathological chain must not
 * push it off the admin page. Pure and JVM-only (it takes any
 * [Throwable]) so the chain/cap behaviour is unit-testable
 * without a media3 exception instance.
 */
object PlaybackErrorText {

    const val MAX_CHARS = 480

    /** Guard against a cyclic cause chain (Throwable permits them). */
    private const val MAX_CAUSES = 8

    private const val SEPARATOR = " <- "

    fun describe(errorCodeName: String, error: Throwable): String {
        val full = buildString {
            append(errorCodeName)
            val top = firstLine(error.message)
            if (top.isNotEmpty()) append(": ").append(top)
            var cause = error.cause
            var depth = 0
            while (cause != null && depth < MAX_CAUSES) {
                append(SEPARATOR).append("caused by ").append(cause.javaClass.simpleName)
                val line = firstLine(cause.message)
                if (line.isNotEmpty()) append(": ").append(line)
                if (cause === cause.cause) break
                cause = cause.cause
                depth++
            }
        }
        if (full.length <= MAX_CHARS) return full
        return full.substring(0, MAX_CHARS - 1).trimEnd() + "…"
    }

    private fun firstLine(message: String?): String =
        message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
}
