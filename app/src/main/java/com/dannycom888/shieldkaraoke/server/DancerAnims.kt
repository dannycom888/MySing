package com.dannycom888.shieldkaraoke.server

import com.dannycom888.shieldkaraoke.state.DancerKeys

/**
 * The phone picker's animated dancer cards (v1.0.11): each dancer
 * as a short looping animated WebP, stitched at build time from
 * the same forty pose frames the TV player steps through (see
 * dancers/make_anim.py) and bundled as an asset. The user asked
 * for the picker cards to move — "选人时就能看到每个小人跳舞的
 * 样子" — where v1.0.7's cards were static standing portraits.
 * Keys are the shared [DancerKeys]; the bytes are read from
 * assets and cached once by the app (see KaraokeApp), never per
 * request. The page falls back to the static [DancerImages]
 * portrait if an animation fails to load.
 */
object DancerAnims {

    const val ROUTE_PREFIX = "/dancer-anim/"

    /** Asset directory the generated animations live in. */
    const val ASSET_DIR = "dancer_anim"

    /**
     * The dancer key a request path asks for, or null when the
     * path is not an animation route or the key is not a real
     * dancer (unknown keys 404 instead of serving a wrong dance).
     */
    fun keyForPath(path: String): String? {
        if (!path.startsWith(ROUTE_PREFIX)) return null
        val key = path.removePrefix(ROUTE_PREFIX)
        return if (DancerKeys.isValid(key)) key else null
    }

    fun pathFor(key: String): String = ROUTE_PREFIX + key

    fun assetPathFor(key: String): String = "$ASSET_DIR/$key.webp"
}
