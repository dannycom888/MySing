package com.dannycom888.shieldkaraoke.state

import com.dannycom888.shieldkaraoke.i18n.UiLang

/**
 * The one place dancer display names are resolved (v1.0.21).
 *
 * The host can rename the six dancers on the TV admin page —
 * friends come over, their names go on the dancers. A custom name
 * lives in [Prefs] per dancer key and wins in EVERY language: a
 * person's name is not translated. With no custom name the
 * bilingual defaults in [DancerKeys] apply, exactly as before.
 *
 * What is stored per orderer elsewhere (the dancer *selection*,
 * `Prefs.dancerSelections`) holds dancer KEYS, never names, so a
 * rename cannot disturb anyone's pick — it only changes how the
 * picked key is labelled.
 */
object DancerNames {

    /** Name length cap, in characters — the TV edit box enforces
     *  the same limit while typing. */
    const val MAX_NAME_LENGTH = 10

    /**
     * Clean a name typed on the TV (or read back from storage):
     * trim, drop control characters (including the storage
     * separator U+0001 and newlines) and angle brackets — a name
     * is rendered as text on the TV and injected into the phone
     * page, so markup characters have no business in it — then
     * cap at [MAX_NAME_LENGTH] characters without splitting a
     * surrogate pair. Null when nothing usable remains: an empty
     * name is never stored, callers fall back to the current name.
     */
    fun sanitize(raw: String?): String? {
        if (raw == null) return null
        val cleaned = raw.filter { it.code >= 0x20 && it.code != 0x7F && it != '<' && it != '>' }.trim()
        if (cleaned.isEmpty()) return null
        return if (cleaned.codePointCount(0, cleaned.length) > MAX_NAME_LENGTH) {
            cleaned.substring(0, cleaned.offsetByCodePoints(0, MAX_NAME_LENGTH))
        } else {
            cleaned
        }
    }

    /**
     * The name to show for dancer [key] in [lang]: the custom name
     * when one is set (verbatim, in both languages), otherwise the
     * default stage name for the language. Unknown / null keys
     * resolve as the default dancer, like everywhere else.
     */
    fun display(key: String?, lang: UiLang, customNames: Map<String, String>): String {
        val validKey = if (key != null && DancerKeys.isValid(key)) key else DancerKeys.DEFAULT
        val custom = sanitize(customNames[validKey])
        if (custom != null) return custom
        return if (lang == UiLang.EN) DancerKeys.labelEn(validKey) else DancerKeys.label(validKey)
    }
}
