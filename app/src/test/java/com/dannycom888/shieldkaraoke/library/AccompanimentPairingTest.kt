package com.dannycom888.shieldkaraoke.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The （伴奏） suffix rule in isolation (v1.0.47). */
class AccompanimentPairingTest {

    @Test
    fun fullWidthSuffixStripsToTheOriginalStem() {
        assertEquals("周杰伦 - 晴天", AccompanimentPairing.baseStemOf("周杰伦 - 晴天（伴奏）"))
        assertTrue(AccompanimentPairing.isAccompanimentStem("周杰伦 - 晴天（伴奏）"))
    }

    @Test
    fun halfWidthSuffixIsAlsoRecognised() {
        assertEquals("Adele - Hello", AccompanimentPairing.baseStemOf("Adele - Hello(伴奏)"))
        assertTrue(AccompanimentPairing.isAccompanimentStem("Adele - Hello(伴奏)"))
    }

    @Test
    fun plainStemsAreNotAccompaniment() {
        assertNull(AccompanimentPairing.baseStemOf("周杰伦 - 晴天"))
        assertFalse(AccompanimentPairing.isAccompanimentStem("周杰伦 - 晴天"))
    }

    @Test
    fun theWordAloneWithoutBracketsIsJustATitle() {
        // A song genuinely titled 伴奏 (no brackets) must stay a
        // normal song — only the bracketed suffix pairs.
        assertNull(AccompanimentPairing.baseStemOf("歌手 - 伴奏"))
        assertFalse(AccompanimentPairing.isAccompanimentStem("歌手 - 伴奏"))
        assertFalse(AccompanimentPairing.isAccompanimentStem("伴奏"))
    }

    @Test
    fun aBareSuffixHasNoBaseAndIsNotAPairing() {
        assertNull(AccompanimentPairing.baseStemOf("（伴奏）"))
        assertNull(AccompanimentPairing.baseStemOf("(伴奏)"))
        assertFalse(AccompanimentPairing.isAccompanimentStem("（伴奏）"))
    }

    @Test
    fun onlyOneSuffixIsStripped() {
        assertEquals("歌名（伴奏）", AccompanimentPairing.baseStemOf("歌名（伴奏）（伴奏）"))
    }

    @Test
    fun suffixMustBeAtTheVeryEnd() {
        assertNull(AccompanimentPairing.baseStemOf("周杰伦 - 晴天（伴奏）remix"))
        assertNull(AccompanimentPairing.baseStemOf("（伴奏）周杰伦 - 晴天"))
    }
}
