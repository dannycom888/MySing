package com.dannycom888.shieldkaraoke.ui

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.0.65 (the remote-controls-nothing repair): the rescue
 * net's pure logic, plus source pins for the focus wiring —
 * every screen's initial-focus anchor, the handoffs that
 * used to strand focus (singer pick, dancer dialog), and
 * the invariant that every interactive control is focusable
 * while the ambient layers carry no focusable at all.
 * Device verification on the Shield is still owed; these
 * pins keep the wiring from silently regressing.
 */
class FocusNavigationTest {

    // ---------- the rescue net's logic ----------

    @Test
    fun remoteNavigationKeysAreRecognised() {
        listOf(
            Key.DirectionUp, Key.DirectionDown,
            Key.DirectionLeft, Key.DirectionRight,
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
        ).forEach { key ->
            assertTrue("$key must count as navigation", FocusRescue.isNavigationKey(key))
        }
    }

    @Test
    fun mediaAndBackKeysAreNotTheNetsBusiness() {
        // Media keys belong to the root's media handling and
        // Back to the BackHandlers; the net must never
        // swallow either.
        assertFalse(FocusRescue.isNavigationKey(Key.MediaPlayPause))
        assertFalse(FocusRescue.isNavigationKey(Key.Escape))
        assertFalse(FocusRescue.isNavigationKey(Key.A))
    }

    @Test
    fun rescueFiresOnlyIntoAFocusVacuum() {
        assertTrue(FocusRescue.shouldRescue(hasFocus = false))
        assertFalse(FocusRescue.shouldRescue(hasFocus = true))
    }

    // ---------- wiring pins ----------

    private fun mainSource(): String =
        File("src/main/java/com/dannycom888/shieldkaraoke/ui/MainActivity.kt").readText()

    private fun slice(source: String, startMarker: String, endMarker: String): String {
        val start = source.indexOf(startMarker)
        assertTrue("marker missing: $startMarker", start >= 0)
        val end = source.indexOf(endMarker, start + startMarker.length)
        assertTrue("end marker missing: $endMarker", end > start)
        return source.substring(start, end)
    }

    @Test
    fun rootRescueNetIsWired() {
        val root = slice(mainSource(), "MaterialTheme {", "override fun onDestroy")
        assertTrue(root.contains(".onFocusEvent { hasAnyFocus = it.hasFocus }"))
        assertTrue(root.contains("FocusRescue.shouldRescue(hasAnyFocus)"))
        assertTrue(root.contains("FocusRescue.isNavigationKey(event.key)"))
        assertTrue(root.contains("rescueFocus()"))
    }

    @Test
    fun rescueTargetsEachScreensAnchor() {
        val source = mainSource()
        assertTrue(source.contains("Screen.PLAYER -> playerControlRequester.requestFocus()"))
        assertTrue(source.contains("Screen.BROWSE -> browseInitialRequester.requestFocus()"))
        assertTrue(source.contains("Screen.ADMIN -> adminInitialRequester.requestFocus()"))
    }

    @Test
    fun playerAnchorIsTheSharedOne() {
        val player = slice(mainSource(), "private fun PlayerScreen", "private fun QueuePanel")
        assertTrue(player.contains("val controlRequester = playerControlRequester"))
        // The entry effect still lands on the pause chip.
        assertTrue(player.contains("controlRequester.requestFocus()"))
    }

    @Test
    fun browseScreenRequestsItsAnchorOnEntry() {
        val browse = slice(mainSource(), "private fun BrowseScreen", "private fun BrandLogo")
        assertTrue(browse.contains("browseInitialRequester.requestFocus()"))
        // …and the anchor rides the first entry card.
        assertTrue(browse.contains("Modifier.focusRequester(browseInitialRequester)"))
    }

    @Test
    fun adminScreenRequestsItsAnchorOnEntry() {
        val admin = slice(mainSource(), "private fun AdminScreen", "private fun AdminDancerRow")
        assertTrue(admin.contains("adminInitialRequester.requestFocus()"))
        assertTrue(admin.contains("Modifier.focusRequester(adminInitialRequester)"))
    }

    @Test
    fun entryCardCanCarryAnAnchor() {
        val card = slice(mainSource(), "private fun EntryCard", "private fun Chip")
        assertTrue(card.contains("modifier: Modifier = Modifier"))
        assertTrue(card.contains("modifier = modifier"))
        assertTrue(card.contains(".focusable()"))
    }

    @Test
    fun pickingASingerHandsFocusToTheFirstSong() {
        val browse = slice(mainSource(), "private fun BrowseScreen", "private fun BrandLogo")
        // The disposed singer row must not take focus with
        // it: the click bumps the first-result hand-off.
        assertTrue(browse.contains("focusFirstResultTick++"))
    }

    @Test
    fun dancerDialogReturnsFocusToItsSongRow() {
        val source = mainSource()
        // Both exits of the dialog (pick and cancel) record
        // the return…
        assertEquals(
            2, source.split("browseRowFocusSongId = song.id").size - 1,
        )
        // …the browse rows carry per-song anchors…
        val browse = slice(source, "private fun BrowseScreen", "private fun BrandLogo")
        assertTrue(browse.contains("browseRowRequesters.getOrPut(song.id)"))
        // …and the return effect performs the move.
        assertTrue(browse.contains("browseRowRequesters[id]?.requestFocus()"))
        // The dialog itself still opens on the stored pick.
        val dialog = slice(source, "private fun TvDancerPickerDialog", "private fun TvDancerPickCard")
        assertTrue(dialog.contains("cardRequesters[preselected.key]?.requestFocus()"))
    }

    @Test
    fun everyInteractiveControlIsFocusable() {
        val source = mainSource()
        // The ten hand-rolled controls (Material3 Buttons in
        // dialogs are focusable by construction).
        listOf(
            "Chip", "GearButton", "EntryCard", "ListRow",
            "SearchField", "VoiceButton", "PanelActionChip",
            "QueuePanelRowItem", "TvDancerPickCard", "AdminDancerRow",
        ).forEach { name ->
            val start = source.indexOf("fun $name(")
            assertTrue("control missing: $name", start >= 0)
            val window = source.substring(start, minOf(start + 4500, source.length))
            assertTrue("$name must stay focusable", window.contains(".focusable()"))
        }
    }

    @Test
    fun ambientLayersCarryNoFocusable() {
        // Bubbles, stars, beams, pools, reflections: pure
        // Canvas drawing — focus search must pass straight
        // through them to the controls.
        val dressing = File(
            "src/main/java/com/dannycom888/shieldkaraoke/ui/StageDressing.kt",
        ).readText()
        assertFalse(dressing.contains("focusable("))
        val spotlight = File(
            "src/main/java/com/dannycom888/shieldkaraoke/ui/Spotlight.kt",
        ).readText()
        assertFalse(spotlight.contains("focusable("))
    }
}
