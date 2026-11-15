package com.dannycom888.shieldkaraoke.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.12 roster: the sumo （姿三四郎） replaces the modern girl and
 * every dancer carries a stage name. The retired "girl" key must
 * degrade to the default exactly like any unknown key — phones and
 * TV prefs still holding it must not crash or wedge.
 */
class DancerKeysTest {

    @Test
    fun rosterIsSixWithSumoInTheGirlsOldSlot() {
        assertEquals(
            listOf("modern", "ancient", "sumo", "ancient_girl", "baby", "robot"),
            DancerKeys.ALL,
        )
    }

    @Test
    fun sumoIsValidAndGirlIsRetired() {
        assertTrue(DancerKeys.isValid(DancerKeys.SUMO))
        assertFalse(DancerKeys.isValid("girl"))
    }

    @Test
    fun stageNames() {
        assertEquals("春丽", DancerKeys.label("modern"))
        assertEquals("林语嫣", DancerKeys.label("ancient"))
        assertEquals("姿三四郎", DancerKeys.label("sumo"))
        assertEquals("小蛮", DancerKeys.label("ancient_girl"))
        assertEquals("哪吒", DancerKeys.label("baby"))
        assertEquals("瓦力", DancerKeys.label("robot"))
    }

    @Test
    fun englishStageNames() {
        // v1.0.13: the phone page's English cards read these names;
        // the page carries the same mapping inline and PhonePageTest
        // pins the two sides together.
        assertEquals("Chun-Li", DancerKeys.labelEn("modern"))
        assertEquals("Lin Yuyan", DancerKeys.labelEn("ancient"))
        assertEquals("Sanshiro", DancerKeys.labelEn("sumo"))
        assertEquals("Xiaoman", DancerKeys.labelEn("ancient_girl"))
        assertEquals("Nezha", DancerKeys.labelEn("baby"))
        assertEquals("WALL-E", DancerKeys.labelEn("robot"))
        assertEquals(DancerKeys.ALL.toSet(), DancerKeys.LABELS_EN.keys)
        assertEquals("Chun-Li", DancerKeys.labelEn("girl"))
        assertEquals("Chun-Li", DancerKeys.labelEn(null))
    }

    @Test
    fun retiredAndUnknownKeysFallBackToDefault() {
        assertEquals("春丽", DancerKeys.label("girl"))
        assertEquals("春丽", DancerKeys.label("nonsense"))
        assertEquals("春丽", DancerKeys.label(null))
        assertEquals(DancerKeys.MODERN, DancerKeys.DEFAULT)
    }
}
