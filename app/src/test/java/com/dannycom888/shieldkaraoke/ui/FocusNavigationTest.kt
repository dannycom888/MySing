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

    // ---------- v1.0.66: the admin page, path by path ----------

    @Test
    fun adminFocusPathsAreWired() {
        val source = mainSource()
        val admin = slice(source, "private fun AdminScreen", "private fun AdminDancerRow")
        // In through the gear, back through the same door.
        assertTrue(admin.contains("BackHandler { screen = adminReturnTo }"))
        assertTrue(admin.contains("screen = adminReturnTo"))
        // The folder card launches the system tree picker;
        // the page (and its focus owner) stays composed
        // across the round trip.
        assertTrue(admin.contains("ActivityResultContracts.OpenDocumentTree()"))
        assertTrue(admin.contains("picker.launch(null)"))
        // Rescan is a plain focusable card next to it.
        assertTrue(admin.contains("TvKey.RESCAN"))
        // The sweep-colour chips are the shared Chip.
        assertTrue(admin.contains("TvKey.SWEEP_GOLD"))
        assertTrue(admin.contains("TvKey.SWEEP_PINK"))
        // Restore-defaults goes through its confirm dialog.
        assertTrue(admin.contains("confirmRestoreNames = true"))
        assertTrue(admin.contains("TvKey.ADMIN_RESTORE_CONFIRM"))
        // The rename editor is the native dialog, and it
        // explicitly takes focus on show (the Leanback
        // keyboard follows it).
        val rename = slice(source, "private fun showRenameDialog", "private fun showSearchDialog")
        assertTrue(rename.contains("edit.requestFocus()"))
        assertTrue(rename.contains("setPositiveButton"))
        assertTrue(rename.contains("setNegativeButton"))
    }

    // ---------- v1.0.66: the TV order flow, handoff by handoff ----------

    @Test
    fun orderingFlowHandoffsAreWired() {
        val source = mainSource()
        val browse = slice(source, "private fun BrowseScreen", "private fun BrandLogo")
        // The search field carries its own anchor…
        assertTrue(browse.contains("focusRequester(searchFieldRequester)"))
        // …because the clear chip removes ITSELF with the
        // query: its click must hand focus to the field.
        val clearAt = browse.indexOf("TvKey.SEARCH_CLEAR")
        assertTrue("clear chip found", clearAt >= 0)
        assertTrue(
            browse.substring(clearAt, minOf(clearAt + 700, browse.length))
                .contains("refocusSearchField()"),
        )
        // The singer ✕ chip is the same shape: clearing the
        // singer removes the chip, so its click hands focus
        // to the first song row like a fresh query does.
        val singerChipAt = browse.indexOf("TvKey.SINGER_CHIP")
        assertTrue("singer chip found", singerChipAt >= 0)
        assertTrue(
            browse.substring(singerChipAt, minOf(singerChipAt + 700, browse.length))
                .contains("focusFirstResultTick++"),
        )
        // A fresh query (typed save or voice result) lands
        // on the first result — one shared move.
        assertTrue(source.contains("focusFirstResultTick++"))
        val query = slice(source, "private fun applyBrowseQuery", "private fun rescueFocus")
        assertTrue(query.contains("focusFirstResultTick++"))
        // Ordering while a song plays returns to the stage;
        // ordering into an idle stage starts the song (the
        // root's onShouldStart lands on the same screen) —
        // either way the player screen's entry anchor owns
        // the landing.
        assertTrue(
            source.contains(
                "if (app.state.queue.current != null) screen = Screen.PLAYER",
            ),
        )
        // The queue panel's focus machinery: expansion aims
        // at the play-now chip, exits land back on the bar,
        // and Back is two-stage while the panel has focus.
        val player = slice(source, "private fun PlayerScreen", "private fun QueuePanel")
        assertTrue(player.contains("PanelFocusTarget.PlayNowAction"))
        assertTrue(player.contains("focusTarget = PanelFocusTarget.ControlBar"))
        assertTrue(player.contains("BackHandler(enabled = panelFocused"))
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
