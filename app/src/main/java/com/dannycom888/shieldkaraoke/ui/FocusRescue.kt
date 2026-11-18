package com.dannycom888.shieldkaraoke.ui

import androidx.compose.ui.input.key.Key

/**
 * v1.0.65: the remote-focus safety net's pure logic.
 *
 * The user's report on the real Shield: "the remote can't
 * control any button". The audit behind this version found
 * every control individually focusable, but focus itself
 * could be LOST — the browse and admin screens requested no
 * initial focus (the player screen's focused chip is disposed
 * on the way in, taking the focus owner with it), picking a
 * singer disposed the focused row without moving focus, and
 * nothing anywhere recovered from a lost focus owner. On a
 * touch phone none of that is visible; with only a D-pad it
 * is total: no focus, no visible focus indicator, no clicks.
 *
 * The net: the root tracks whether ANY node in the
 * composition holds focus (onFocusEvent's hasFocus), and a
 * navigation key that arrives while nothing is focused is
 * spent on re-anchoring focus to the current screen's
 * designated first control instead of being dropped. Each
 * screen ALSO requests its anchor on entry (the normal
 * path); the net is the guarantee that no sequence of
 * transitions, dialog dismissals or disposal races can
 * leave the app focus-less for more than one key press.
 */
object FocusRescue {

    /** The keys a TV remote navigates and activates with.
     *  Back and media keys are deliberately NOT navigation
     *  keys here: Back belongs to the BackHandlers, media
     *  keys to the root's media handling, and neither may be
     *  swallowed by the net. */
    fun isNavigationKey(key: Key): Boolean =
        key == Key.DirectionUp || key == Key.DirectionDown ||
            key == Key.DirectionLeft || key == Key.DirectionRight ||
            key == Key.DirectionCenter || key == Key.Enter ||
            key == Key.NumPadEnter

    /** The net fires only into a focus vacuum. While any
     *  control holds focus, key handling is the ordinary
     *  spatial navigation and the net stays out of the way. */
    fun shouldRescue(hasFocus: Boolean): Boolean = !hasFocus
}
