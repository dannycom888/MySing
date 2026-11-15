package com.dannycom888.shieldkaraoke.server

import com.dannycom888.shieldkaraoke.state.DancerKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Guards the dancer-portrait route: only the six real dancer
 *  keys resolve; anything else must 404 rather than serve a
 *  wrong portrait. */
class DancerImagesTest {

    @Test
    fun everyDancerKeyResolves() {
        for (key in DancerKeys.ALL) {
            assertEquals(key, DancerImages.keyForPath("/dancer-img/$key"))
        }
    }

    @Test
    fun pathForRoundTrips() {
        for (key in DancerKeys.ALL) {
            assertEquals(key, DancerImages.keyForPath(DancerImages.pathFor(key)))
        }
    }

    @Test
    fun unknownKeysDoNotResolve() {
        assertNull(DancerImages.keyForPath("/dancer-img/"))
        assertNull(DancerImages.keyForPath("/dancer-img/nobody"))
        assertNull(DancerImages.keyForPath("/dancer-img/MODERN"))
        assertNull(DancerImages.keyForPath("/dancer-img/modern/extra"))
        assertNull(DancerImages.keyForPath("/dancer-img/modern.png"))
    }

    @Test
    fun otherPathsDoNotResolve() {
        assertNull(DancerImages.keyForPath("/"))
        assertNull(DancerImages.keyForPath("/api/queue"))
        assertNull(DancerImages.keyForPath("/dancer"))
        assertNull(DancerImages.keyForPath("dancer-img/modern"))
    }
}
