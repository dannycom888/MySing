package com.dannycom888.shieldkaraoke.server

import com.dannycom888.shieldkaraoke.state.DancerKeys

/**
 * The phone page's dancer portraits: the six dancers' standing
 * single-frame cutouts, served as PNG so the "选伴舞" picker can
 * show faces instead of bare names (the user: "手机上我好象没看到
 * 这几个小人" — the old picker was six text-only chips). Keys are
 * the shared [DancerKeys]; the bytes themselves are decoded and
 * cached once by the app (see KaraokeApp), never per request.
 */
object DancerImages {

    const val ROUTE_PREFIX = "/dancer-img/"

    /** Image height the app downscales the portraits to, in px —
     *  the page shows them ~96 CSS px tall, so this leaves
     *  headroom for high-density phone screens. */
    const val SERVED_HEIGHT_PX = 300

    /**
     * The dancer key a request path asks for, or null when the
     * path is not an image route or the key is not a real dancer
     * (unknown keys 404 instead of serving a wrong portrait).
     */
    fun keyForPath(path: String): String? {
        if (!path.startsWith(ROUTE_PREFIX)) return null
        val key = path.removePrefix(ROUTE_PREFIX)
        return if (DancerKeys.isValid(key)) key else null
    }

    fun pathFor(key: String): String = ROUTE_PREFIX + key
}
