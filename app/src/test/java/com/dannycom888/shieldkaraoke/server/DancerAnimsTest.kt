package com.dannycom888.shieldkaraoke.server

import com.dannycom888.shieldkaraoke.state.DancerKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the animated dancer-card route (v1.0.11): only the six
 *  real dancer keys resolve; anything else must 404 rather than
 *  serve a wrong dance. Mirrors DancerImagesTest for the static
 *  portrait route the page falls back to. */
class DancerAnimsTest {

    @Test
    fun everyDancerKeyResolves() {
        for (key in DancerKeys.ALL) {
            assertEquals(key, DancerAnims.keyForPath("/dancer-anim/$key"))
        }
    }

    @Test
    fun pathForRoundTrips() {
        for (key in DancerKeys.ALL) {
            assertEquals(key, DancerAnims.keyForPath(DancerAnims.pathFor(key)))
        }
    }

    @Test
    fun unknownKeysDoNotResolve() {
        assertNull(DancerAnims.keyForPath("/dancer-anim/"))
        assertNull(DancerAnims.keyForPath("/dancer-anim/nobody"))
        assertNull(DancerAnims.keyForPath("/dancer-anim/MODERN"))
        assertNull(DancerAnims.keyForPath("/dancer-anim/modern/extra"))
        assertNull(DancerAnims.keyForPath("/dancer-anim/modern.webp"))
    }

    @Test
    fun otherPathsDoNotResolve() {
        assertNull(DancerAnims.keyForPath("/"))
        assertNull(DancerAnims.keyForPath("/api/queue"))
        assertNull(DancerAnims.keyForPath("/dancer"))
        assertNull(DancerAnims.keyForPath("dancer-anim/modern"))
    }

    @Test
    fun animAndPortraitRoutesDoNotCollide() {
        // The server's binary bypass tries the portrait route
        // first; the two prefixes must stay disjoint so an
        // animation request can never be answered with a PNG.
        for (key in DancerKeys.ALL) {
            assertNull(DancerImages.keyForPath(DancerAnims.pathFor(key)))
            assertNull(DancerAnims.keyForPath(DancerImages.pathFor(key)))
        }
    }

    @Test
    fun assetPathsMatchTheBundledFiles() {
        for (key in DancerKeys.ALL) {
            assertEquals("dancer_anim/$key.webp", DancerAnims.assetPathFor(key))
        }
        assertTrue(DancerAnims.ASSET_DIR == "dancer_anim")
    }
}
