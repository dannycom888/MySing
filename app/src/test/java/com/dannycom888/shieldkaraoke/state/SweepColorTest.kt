package com.dannycom888.shieldkaraoke.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * v1.0.26 sweep colour: the admin page offers exactly the three
 * hues MyLRC Auto settled on in its v1.0.36 (gold default, light
 * green, light pink — the orange/red it tried were rejected as
 * harsh and must never come back), stored by enum name in prefs,
 * with any missing or unrecognised value falling back to gold so
 * the sweep can never break.
 */
class SweepColorTest {

    @Test
    fun exactlyThreeChoicesAndNoHarshOnes() {
        assertEquals(
            listOf(SweepColor.GOLD, SweepColor.LIGHT_GREEN, SweepColor.LIGHT_PINK),
            SweepColor.values().toList(),
        )
        assertFalse(SweepColor.values().any { it.name == "ORANGE" })
        assertFalse(SweepColor.values().any { it.name == "RED" })
    }

    @Test
    fun argbValuesAreTheSettledHues() {
        // Gold is the sweep's colour since the v1.0.2 gradient;
        // pink is MyLRC v1.0.36's exact value. The green started
        // as MyLRC's pale #A9E5A1 but the user deepened it in
        // v1.0.38, picking #34C759 off a swatch card (MySing only).
        assertEquals(0xFFFFD54FL, SweepColor.GOLD.argb)
        assertEquals(0xFF34C759L, SweepColor.LIGHT_GREEN.argb)
        assertEquals(0xFFFFB7C5L, SweepColor.LIGHT_PINK.argb)
    }

    @Test
    fun defaultIsGold() {
        assertEquals(SweepColor.GOLD, SweepColor.DEFAULT)
    }

    @Test
    fun storedNamesRoundTrip() {
        for (choice in SweepColor.values()) {
            assertEquals(choice, SweepColor.fromStoredName(choice.name))
        }
    }

    @Test
    fun missingOrUnknownFallsBackToGold() {
        assertEquals(SweepColor.GOLD, SweepColor.fromStoredName(null))
        assertEquals(SweepColor.GOLD, SweepColor.fromStoredName(""))
        assertEquals(SweepColor.GOLD, SweepColor.fromStoredName("gold"))
        assertEquals(SweepColor.GOLD, SweepColor.fromStoredName("ORANGE"))
        assertEquals(SweepColor.GOLD, SweepColor.fromStoredName("PURPLE"))
    }
}
