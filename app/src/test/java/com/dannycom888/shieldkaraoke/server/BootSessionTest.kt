package com.dannycom888.shieldkaraoke.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the v1.0.8 stale-page defence: every server start mints
 *  a boot id, pages carry it, and a page whose id no longer
 *  matches the live server's is stale and must reload itself. */
class BootSessionTest {

    @Test
    fun newIdCombinesTimeAndRandom() {
        val a = BootSession.newId(1_700_000_000_000L, 42L)
        assertNotEquals(a, BootSession.newId(1_700_000_000_001L, 42L))
        assertNotEquals(a, BootSession.newId(1_700_000_000_000L, 43L))
        assertEquals(a, BootSession.newId(1_700_000_000_000L, 42L))
    }

    @Test
    fun newIdIsSafeToEmbedAnywhere() {
        // The id lands raw inside a JS string literal, a JSON field
        // and URLs — it may only contain base36 digits and the
        // separator, for any inputs including negative randoms.
        val id = BootSession.newId(1_762_000_000_000L, -987_654_321L)
        assertTrue(id.matches(Regex("[0-9a-z]+-[0-9a-z]+")))
    }

    @Test
    fun sameBootIsNotStale() {
        assertFalse(BootSession.isStale("abc-123", "abc-123"))
    }

    @Test
    fun differentBootIsStale() {
        assertTrue(BootSession.isStale("abc-123", "def-456"))
    }

    @Test
    fun unknownPageBootIsNotStale() {
        // A page too old to carry an id has no comparing JS anyway;
        // judging it stale could only cause reload loops.
        assertFalse(BootSession.isStale(null, "abc-123"))
        assertFalse(BootSession.isStale("", "abc-123"))
        assertFalse(BootSession.isStale("   ", "abc-123"))
    }
}
