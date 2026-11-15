package com.dannycom888.shieldkaraoke.server

/**
 * Boot-session identity for the phone-ordering page (v1.0.8).
 *
 * The diagnosed failure: a guest keeps the ordering page open from
 * an earlier TV-app session; after the app restarts (or is
 * upgraded) the old page's requests go nowhere and — the fetches
 * having no failure handler — the phone showed nothing at all
 * while the TV sat unmoved. Rescanning the QR fixed it instantly,
 * which pinned the fault on the stale page.
 *
 * So every server start mints a fresh [newId]; the served page
 * carries it, and every /api/queue poll returns the live one. The
 * page compares the two and reloads itself when they differ, so a
 * stale page heals instead of silently swallowing orders. Pure
 * functions only — the page's JS mirrors [isStale] exactly.
 */
object BootSession {

    /** A URL- and JS-string-safe id from the start time and a
     *  random component: base36 keeps it short and free of any
     *  character that would need escaping in HTML/JS/JSON. */
    fun newId(nowMs: Long, randomBits: Long): String =
        nowMs.toString(36) + "-" + (randomBits and Long.MAX_VALUE).toString(36)

    /**
     * True when the page's embedded boot id proves the page came
     * from a dead server session. A blank page id means "unknown"
     * (a page too old to carry one has no comparing JS anyway) and
     * is NOT judged stale — that also rules out reload loops if
     * embedding ever failed.
     */
    fun isStale(pageBoot: String?, currentBoot: String): Boolean {
        val page = pageBoot?.trim().orEmpty()
        if (page.isEmpty()) return false
        return page != currentBoot
    }
}
