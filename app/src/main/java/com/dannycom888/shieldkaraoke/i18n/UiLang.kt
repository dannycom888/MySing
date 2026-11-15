package com.dannycom888.shieldkaraoke.i18n

/**
 * UI language for MySing (v1.0.13): the TV interface has a 中文/EN
 * switch on the home screen (persisted in Prefs), and every phone
 * keeps its own choice for the ordering page (localStorage there,
 * sent back to the server with each request so replies match).
 * Chinese stays the default everywhere — an absent or unknown
 * language value always means Chinese, so pages served before this
 * release keep behaving exactly as they did.
 */
enum class UiLang {
    ZH, EN;

    /** Wire form used by prefs and the phone page: "zh" / "en". */
    val code: String get() = if (this == EN) "en" else "zh"

    companion object {
        fun parse(value: String?): UiLang =
            if (value?.trim()?.lowercase() == "en") EN else ZH
    }
}
