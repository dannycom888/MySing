package com.dannycom888.shieldkaraoke.audio

import com.dannycom888.shieldkaraoke.queue.AccompanimentPolicy
import com.dannycom888.shieldkaraoke.queue.EntrySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v1.0.47 source matrix: what an accompaniment intent plays,
 * across (paired file?) × (accompaniment on/off) × (how the song
 * started — ordered vs filler, which sets the starting intent
 * through [AccompanimentPolicy]).
 */
class PlaybackSourcePolicyTest {

    private fun startChoice(hasFile: Boolean, source: EntrySource?) =
        PlaybackSourcePolicy.choose(hasFile, AccompanimentPolicy.defaultOnStart(source))

    @Test
    fun orderedSongWithFileStartsOnTheFile() {
        val choice = startChoice(hasFile = true, EntrySource.ORDERED)
        assertTrue(choice.useAccompanimentFile)
        assertFalse(choice.useRealtimeCancel)
    }

    @Test
    fun orderedSongWithoutFileStartsOnRealtimeCancel() {
        val choice = startChoice(hasFile = false, EntrySource.ORDERED)
        assertFalse(choice.useAccompanimentFile)
        assertTrue(choice.useRealtimeCancel)
    }

    @Test
    fun fillerPlaysTheOriginalEvenWhenAFileExists() {
        // Filler is background listening: the user's ruling keeps
        // it on the original vocal, paired file or not.
        val choice = startChoice(hasFile = true, EntrySource.FILLER)
        assertFalse(choice.useAccompanimentFile)
        assertFalse(choice.useRealtimeCancel)
    }

    @Test
    fun fillerWithoutFilePlaysTheOriginalToo() {
        val choice = startChoice(hasFile = false, EntrySource.FILLER)
        assertEquals(
            PlaybackSourcePolicy.Choice(useAccompanimentFile = false, useRealtimeCancel = false),
            choice,
        )
    }

    @Test
    fun manualOffAlwaysReturnsToTheOriginal() {
        // The singer's toggle rules for the song: off is off,
        // file or no file.
        assertEquals(
            PlaybackSourcePolicy.Choice(false, false),
            PlaybackSourcePolicy.choose(hasAccompanimentFile = true, accompanimentOn = false),
        )
        assertEquals(
            PlaybackSourcePolicy.Choice(false, false),
            PlaybackSourcePolicy.choose(hasAccompanimentFile = false, accompanimentOn = false),
        )
    }

    @Test
    fun manualOnPrefersTheFileOverTheProcessor() {
        // The file is already vocal-free; running it through the
        // cancel processor as well would only damage it.
        val choice = PlaybackSourcePolicy.choose(hasAccompanimentFile = true, accompanimentOn = true)
        assertTrue(choice.useAccompanimentFile)
        assertFalse(choice.useRealtimeCancel)
    }
}
