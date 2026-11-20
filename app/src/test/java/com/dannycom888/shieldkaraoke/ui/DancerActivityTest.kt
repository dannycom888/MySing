package com.dannycom888.shieldkaraoke.ui

import com.dannycom888.shieldkaraoke.lyrics.LyricLine
import com.dannycom888.shieldkaraoke.lyrics.WordTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DancerActivityTest {

    private fun plainLine(timeMs: Long) = LyricLine(timeMs = timeMs, text = "句")

    private fun enhancedLine(timeMs: Long, endMarkerMs: Long?) = LyricLine(
        timeMs = timeMs,
        text = "句子",
        words = buildList {
            add(WordTiming(timeMs, "句"))
            add(WordTiming(timeMs + 500, "子"))
            if (endMarkerMs != null) add(WordTiming(endMarkerMs, ""))
        },
    )

    @Test
    fun noLyricsMeansNoEnd() {
        assertNull(DancerActivity.lastLyricEndMs(emptyList()))
    }

    @Test
    fun noLyricsDancesTheWholeSong() {
        assertTrue(DancerActivity.isDancing(0L, null))
        assertTrue(DancerActivity.isDancing(3_600_000L, null))
    }

    @Test
    fun plainLastLineFallsBackToItsStart() {
        val lines = listOf(plainLine(10_000), plainLine(180_000))
        assertEquals(180_000L, DancerActivity.lastLyricEndMs(lines))
    }

    @Test
    fun enhancedLastLineUsesItsEndMarker() {
        val lines = listOf(plainLine(10_000), enhancedLine(180_000, 184_500))
        assertEquals(184_500L, DancerActivity.lastLyricEndMs(lines))
    }

    @Test
    fun enhancedLastLineWithoutMarkerFallsBackToStart() {
        val lines = listOf(enhancedLine(180_000, null))
        assertEquals(180_000L, DancerActivity.lastLyricEndMs(lines))
    }

    @Test
    fun endMarkerBeforeLineStartIsIgnored() {
        val lines = listOf(enhancedLine(180_000, 179_000))
        assertEquals(180_000L, DancerActivity.lastLyricEndMs(lines))
    }

    @Test
    fun dancingUntilTheEndThenStill() {
        val end = 184_500L
        assertTrue(DancerActivity.isDancing(end - 1, end))
        assertTrue(DancerActivity.isDancing(end, end))
        assertFalse(DancerActivity.isDancing(end + 1, end))
        assertFalse(DancerActivity.isDancing(end + 60_000, end))
    }

    @Test
    fun stillFrameIsTheNeutralFirstFrame() {
        assertEquals(0, DancerActivity.STILL_FRAME)
    }

    // --- breathing while standing still (v1.0.7) ---

    private val human = DanceMotion.Profile.MODERN

    @Test
    fun breathStartsNeutral() {
        val pose = DancerActivity.breathPose(0L, human)
        assertEquals(1f, pose.scale, 0.0001f)
        assertEquals(0f, pose.bounceFraction, 0.0001f)
        assertEquals(0f, pose.rotationDeg, 0.0001f)
        assertEquals(0f, pose.sinkFraction, 0.0001f)
    }

    @Test
    fun breathPeaksHalfwayThroughThePeriod() {
        val pose = DancerActivity.breathPose(DancerActivity.BREATH_PERIOD_MS / 2, human)
        assertEquals(1f + DancerActivity.BREATH_SCALE_AMP, pose.scale, 0.0001f)
        assertEquals(DancerActivity.BREATH_RISE_AMP, pose.bounceFraction, 0.0001f)
    }

    @Test
    fun breathReturnsToNeutralAfterAFullPeriod() {
        val start = DancerActivity.breathPose(0L, human)
        val after = DancerActivity.breathPose(DancerActivity.BREATH_PERIOD_MS, human)
        assertEquals(start.scale, after.scale, 0.0001f)
        assertEquals(start.bounceFraction, after.bounceFraction, 0.0001f)
    }

    @Test
    fun breathIsPeriodic() {
        for (t in listOf(1L, 400L, 1599L, 2000L, 3199L, 123_456L)) {
            val a = DancerActivity.breathPose(t, human)
            val b = DancerActivity.breathPose(t + DancerActivity.BREATH_PERIOD_MS, human)
            assertEquals(a.scale, b.scale, 0.0001f)
            assertEquals(a.bounceFraction, b.bounceFraction, 0.0001f)
        }
    }

    @Test
    fun breathRisesThenFallsSymmetrically() {
        val quarter = DancerActivity.breathPose(DancerActivity.BREATH_PERIOD_MS / 4, human)
        val peak = DancerActivity.breathPose(DancerActivity.BREATH_PERIOD_MS / 2, human)
        val threeQuarter = DancerActivity.breathPose(3 * DancerActivity.BREATH_PERIOD_MS / 4, human)
        assertTrue(quarter.scale < peak.scale)
        assertTrue(threeQuarter.scale < peak.scale)
        assertEquals(quarter.scale, threeQuarter.scale, 0.0001f)
        assertEquals(quarter.bounceFraction, threeQuarter.bounceFraction, 0.0001f)
    }

    @Test
    fun breathNeverRotatesOrSinks() {
        var t = 0L
        while (t <= DancerActivity.BREATH_PERIOD_MS) {
            val pose = DancerActivity.breathPose(t, human)
            assertEquals(0f, pose.rotationDeg, 0.0001f)
            assertEquals(0f, pose.sinkFraction, 0.0001f)
            t += 137L
        }
    }

    @Test
    fun breathStaysSubtleAcrossTheWholeCycle() {
        var t = 0L
        while (t <= DancerActivity.BREATH_PERIOD_MS) {
            val pose = DancerActivity.breathPose(t, human)
            assertTrue(pose.scale >= 1f && pose.scale <= 1f + DancerActivity.BREATH_SCALE_AMP + 0.0001f)
            assertTrue(pose.bounceFraction >= 0f && pose.bounceFraction <= DancerActivity.BREATH_RISE_AMP + 0.0001f)
            t += 100L
        }
    }

    @Test
    fun robotBreathesAtHalfAmplitude() {
        val half = DancerActivity.BREATH_PERIOD_MS / 2
        val robot = DancerActivity.breathPose(half, DanceMotion.Profile.ROBOT)
        assertEquals(1f + DancerActivity.BREATH_SCALE_AMP / 2, robot.scale, 0.0001f)
        assertEquals(DancerActivity.BREATH_RISE_AMP / 2, robot.bounceFraction, 0.0001f)
    }

    @Test
    fun breathPeriodIsAboutTwoSeconds() {
        assertEquals(2_000L, DancerActivity.BREATH_PERIOD_MS)
    }

    // --- three phases + the intro invite (v1.0.7) ---

    @Test
    fun noLyricsMeansNoStart() {
        assertNull(DancerActivity.firstSungStartMs(emptyList(), "歌手", "歌名"))
    }

    @Test
    fun firstSungStartIsTheFirstSungLine() {
        val lines = listOf(plainLine(12_500), plainLine(180_000))
        assertEquals(12_500L, DancerActivity.firstSungStartMs(lines, "歌手", "歌名"))
    }

    // --- the header block must not poison the intro (v1.0.30) ---
    //
    // Root cause of "开头单人邀请动作没出现": this library's
    // enhanced LRCs open with a timed header block — a title card
    // at ~0 s, then 词/曲 credit lines — while the first sung line
    // lands 10–30 s in. firstLyricStartMs took the header's first
    // line, so hasIntro judged nearly every song "no intro" and
    // the dancer danced through the whole instrumental intro.
    // The fixtures below are the real files' actual opening lines.

    @Test
    fun creditLabelsAreRecognised() {
        assertTrue(DancerActivity.isCreditLine("词：小美"))
        assertTrue(DancerActivity.isCreditLine("曲：胡伟立"))
        assertTrue(DancerActivity.isCreditLine("作词：黄家驹"))
        assertTrue(DancerActivity.isCreditLine("作曲 : mc六道"))
        // v1.0.74: the 作词人/作曲人 forms count too.
        assertTrue(DancerActivity.isCreditLine("作词人：黄家驹"))
        assertTrue(DancerActivity.isCreditLine("作曲人 : 胡伟立"))
        assertTrue(DancerActivity.isCreditLine("编曲：刀郎"))
        assertTrue(DancerActivity.isCreditLine("Lyrics by Adele"))
        // A sung line that merely STARTS with the same character
        // is not a credit: no colon, no label.
        assertFalse(DancerActivity.isCreditLine("词不达意"))
        assertFalse(DancerActivity.isCreditLine("曲终人散了"))
        assertFalse(DancerActivity.isCreditLine("如何面对"))
    }

    @Test
    fun creditLinesAreDroppedFromTheDisplayList() {
        // v1.0.74 (user report: "歌词的作曲者、作词者还在"): the
        // stage's lyric list drops every credit line outright —
        // all six label forms, judged on the tag-stripped text
        // (the 词 line below is enhanced, word tags and all).
        // The title card is not a credit and stays; a sung line
        // that merely starts with 词 (no colon) stays too.
        val lines = com.dannycom888.shieldkaraoke.lyrics.LrcParser.parse(
            """
            [00:00.50]歌手 - 歌名
            [00:01.00]<00:01.00>词<00:01.20>：<00:01.40>某<00:01.60>人<00:01.80>
            [00:02.00]曲：某人
            [00:03.00]作词：某人
            [00:04.00]作曲：某人
            [00:05.00]作词人：某人
            [00:06.00]作曲人：某人
            [00:30.00]第一句歌词
            [00:35.00]词不达意也成句
            """.trimIndent()
        )
        assertEquals(9, lines.size) // the parser itself is untouched
        val shown = DancerActivity.displayLines(lines)
        assertEquals(
            listOf("歌手 - 歌名", "第一句歌词", "词不达意也成句"),
            shown.map { it.text },
        )
        // Dance start is unchanged by the drop: credit lines
        // never counted as sung lines in the first place.
        assertEquals(30_000L, DancerActivity.firstSungStartMs(lines, "歌手", "歌名"))
        assertEquals(30_000L, DancerActivity.firstSungStartMs(shown, "歌手", "歌名"))
    }

    @Test
    fun oneSidedStemsStillFindTheirIntro() {
        // v1.0.74, the whole chain behind the user's report: a
        // file whose name spaces the hyphen on one side only
        // ("张宇 -雨一直下" and friends) used to scan with a blank
        // artist and the whole stem as its title, so the LRC's
        // title card — which mirrors the file name — no longer
        // matched the song's pair, posed as the first sung line,
        // and the dancer danced through the intro instead of
        // inviting. For each real deviating stem: split it, then
        // run the header block its LRC actually opens with
        // (title card at 0.6 s, 词 at 1.6 s, singing at 30 s).
        val stems = listOf(
            "张宇 -雨一直下", "罗志祥 -爱疯头", "周杰伦- 夜曲", "小虎队- 红蜻蜓", "王杰 王韵婵- 祈祷",
        )
        for (stem in stems) {
            val (artist, title) =
                com.dannycom888.shieldkaraoke.library.LibraryScanner.splitArtistTitle(stem)
            val lines = listOf(
                LyricLine(600L, stem),
                LyricLine(1_600L, "词：佚名"),
                LyricLine(30_000L, "第一句歌词"),
            )
            val start = DancerActivity.firstSungStartMs(lines, artist, title)
            assertEquals("stem $stem must skip its title card", 30_000L, start)
            assertEquals(
                "stem $stem must open INVITING",
                DancerActivity.Phase.INVITING,
                DancerActivity.phase(0L, start, 200_000L),
            )
        }
    }

    @Test
    fun titleCardMatchesTheSongsOwnPairEitherOrder() {
        assertTrue(DancerActivity.isTitleLine("Beyond - 海阔天空", "Beyond", "海阔天空"))
        assertTrue(DancerActivity.isTitleLine("海阔天空 - Beyond", "Beyond", "海阔天空"))
        // Some other song's title card is not this song's.
        assertFalse(DancerActivity.isTitleLine("Beyond - 大地", "Beyond", "海阔天空"))
        // No pair handed in → nothing is guessed.
        assertFalse(DancerActivity.isTitleLine("Beyond - 海阔天空", null, null))
    }

    @Test
    fun titleCardSpacingAroundTheHyphenDoesNotMatter() {
        // The library's own spacing: file names are "歌手 -歌名"
        // (space before the hyphen, none after) and the title
        // cards mirror them (v1.0.36, the 雨一直下 report).
        assertTrue(DancerActivity.isTitleLine("张宇 -雨一直下", "张宇", "雨一直下"))
        assertTrue(DancerActivity.isTitleLine("张宇-雨一直下", "张宇", "雨一直下"))
        assertTrue(DancerActivity.isTitleLine("张宇 - 雨一直下", "张宇", "雨一直下"))
        assertTrue(DancerActivity.isTitleLine("雨一直下 -张宇", "张宇", "雨一直下"))
        // Still exactly the pair, nothing looser: a near-miss
        // title or a different song stays a lyric.
        assertFalse(DancerActivity.isTitleLine("张宇 -雨一直下吧", "张宇", "雨一直下"))
        assertFalse(DancerActivity.isTitleLine("张宇 -下雨", "张宇", "雨一直下"))
        assertFalse(DancerActivity.isTitleLine("雨一直下", "张宇", "雨一直下"))
        // A name that itself carries a hyphen is not broken by
        // the spacing squeeze (whole-line comparison, never a
        // split): the library's Toy-Box and the "Candy-" file.
        assertTrue(DancerActivity.isTitleLine("Toy-Box - Tarzan Jane", "Toy-Box", "Tarzan Jane"))
        assertTrue(DancerActivity.isTitleLine("Mandy Moore - Candy-", "Mandy Moore", "Candy-"))
    }

    @Test
    fun rainSongRealOpeningInvitesForTheWhole40SecondIntro() {
        // 张宇《雨一直下》as the file on disk opens (user report
        // 2026-11-15: no invite at all): the title card at
        // 0.60 s reads "张宇 -雨一直下" — space before the
        // hyphen, none after — then 词 at 1.60 s, 曲 at 2.60 s,
        // and the singing only starts at 40.44 s. The whole
        // 40-second intro must be INVITING.
        val lines = com.dannycom888.shieldkaraoke.lyrics.LrcParser.parse(
            """
            [00:00.60]<00:00.60>张<00:00.71>宇<00:00.82> <00:00.93>-<00:01.04>雨<00:01.15>一<00:01.26>直<00:01.37>下<00:01.48>
            [00:01.60]<00:01.60>词<00:01.80>：<00:02.00>十<00:02.20>一<00:02.40>郎<00:02.60>
            [00:02.60]<00:02.60>曲<00:03.40>：<00:04.20>张<00:05.00>宇<00:05.80>
            [00:40.44]<00:40.44>雨<00:40.95>一<00:41.47>直<00:41.99>下<00:42.51>
            [00:43.85]<00:43.85>气<00:44.42>氛<00:45.00>不<00:45.57>算<00:46.15>融<00:46.72>洽<00:47.30>
            """.trimIndent()
        )
        assertEquals("张宇 -雨一直下", lines[0].text)
        val start = DancerActivity.firstSungStartMs(lines, "张宇", "雨一直下")
        assertEquals(40_440L, start)
        assertTrue(DancerActivity.hasIntro(start))
        assertEquals(DancerActivity.Phase.INVITING, DancerActivity.phase(0L, start, 300_000L))
        assertEquals(DancerActivity.Phase.INVITING, DancerActivity.phase(40_439L, start, 300_000L))
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(40_440L, start, 300_000L))
    }

    @Test
    fun realFileTitleAndCreditsBeforeAn18SecondIntro() {
        // Beyond《海阔天空》as the converter wrote it: title card
        // at 0.72 s, 词 at 7.87 s, 曲 at 8.87 s, singing at
        // 18.35 s. The whole instrumental intro must be INVITING.
        val lines = com.dannycom888.shieldkaraoke.lyrics.LrcParser.parse(
            """
            [00:00.72]<00:00.72>Beyond - 海阔天空
            [00:07.87]<00:07.87>词：黄家驹
            [00:08.87]<00:08.87>曲：黄家驹
            [00:18.35]<00:18.35>今天我 寒夜里看雪飘过
            [00:25.01]<00:25.01>怀着冷却了的心窝漂远方
            """.trimIndent()
        )
        val start = DancerActivity.firstSungStartMs(lines, "Beyond", "海阔天空")
        assertEquals(18_350L, start)
        assertTrue(DancerActivity.hasIntro(start))
        assertEquals(DancerActivity.Phase.INVITING, DancerActivity.phase(0L, start, 200_000L))
        assertEquals(DancerActivity.Phase.INVITING, DancerActivity.phase(18_349L, start, 200_000L))
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(18_350L, start, 200_000L))
    }

    @Test
    fun creditsWithinTheFirstSecondAndSingingAt18Seconds() {
        // The reported shape, minimal: header lines inside the
        // first second, the first sung line at 18 s.
        val lines = listOf(
            LyricLine(0L, "曲婉婷 - 我的歌声里"),
            LyricLine(500L, "词：曲婉婷"),
            LyricLine(900L, "曲：曲婉婷"),
            LyricLine(18_000L, "没有一点点防备"),
        )
        val start = DancerActivity.firstSungStartMs(lines, "曲婉婷", "我的歌声里")
        assertEquals(18_000L, start)
        assertEquals(DancerActivity.Phase.INVITING, DancerActivity.phase(0L, start, 200_000L))
        assertEquals(DancerActivity.Phase.INVITING, DancerActivity.phase(17_999L, start, 200_000L))
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(18_000L, start, 200_000L))
    }

    @Test
    fun creditFirstFileStillFindsItsIntro() {
        // Beyond《大地》opens with the credit, no title card:
        // 作词 at 0 s, singing at 26 s.
        val lines = listOf(
            LyricLine(0L, "作词：刘卓辉"),
            LyricLine(26_000L, "在黑夜中 站在高山上"),
        )
        assertEquals(
            26_000L,
            DancerActivity.firstSungStartMs(lines, "Beyond", "大地"),
        )
    }

    @Test
    fun titleCardThenImmediateSingingStaysNoIntro() {
        // Youngblood's real opening: title card at 0.58 s, the
        // singing starts at 0.75 s — under MIN_INTRO_MS, so the
        // dancer still dances from 0 (the user's own rule).
        val lines = com.dannycom888.shieldkaraoke.lyrics.LrcParser.parse(
            """
            [00:00.58]<00:00.58>5 Seconds of Summer - Youngblood
            [00:00.75]<00:00.75>Remember the words you told me
            """.trimIndent()
        )
        val start = DancerActivity.firstSungStartMs(lines, "5 Seconds of Summer", "Youngblood")
        assertEquals(750L, start)
        assertFalse(DancerActivity.hasIntro(start))
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(0L, start, 200_000L))
    }

    @Test
    fun noLyricsIsDancingThroughout() {
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(0L, null, null))
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(3_600_000L, null, null))
    }

    @Test
    fun beforeTheFirstLineIsInviting() {
        val first = 12_500L
        val end = 184_500L
        assertEquals(DancerActivity.Phase.INVITING, DancerActivity.phase(0L, first, end))
        assertEquals(DancerActivity.Phase.INVITING, DancerActivity.phase(first - 1, first, end))
    }

    @Test
    fun shortIntroDancesFromTheStart() {
        // v1.0.22: "如果没前奏就直接跳" — a first line at 2.4 s
        // leaves no room for a bow, so the song dances from 0.
        val first = 2_400L
        val end = 184_500L
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(0L, first, end))
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(first - 1, first, end))
    }

    @Test
    fun introJustPastTheThresholdStillInvites() {
        // A first line at 2.6 s is past the threshold: invite
        // until the line starts, then dance.
        val first = 2_600L
        val end = 184_500L
        assertEquals(DancerActivity.Phase.INVITING, DancerActivity.phase(0L, first, end))
        assertEquals(DancerActivity.Phase.INVITING, DancerActivity.phase(first - 1, first, end))
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(first, first, end))
    }

    @Test
    fun hasIntroThreshold() {
        assertEquals(2_500L, DancerActivity.MIN_INTRO_MS)
        assertFalse(DancerActivity.hasIntro(null))
        assertFalse(DancerActivity.hasIntro(0L))
        assertFalse(DancerActivity.hasIntro(2_499L))
        assertTrue(DancerActivity.hasIntro(2_500L))
        assertTrue(DancerActivity.hasIntro(12_500L))
    }

    @Test
    fun firstLineStartBeginsTheDance() {
        val first = 12_500L
        val end = 184_500L
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(first, first, end))
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(end, first, end))
    }

    @Test
    fun afterTheLastLineIsStill() {
        val first = 12_500L
        val end = 184_500L
        // v1.0.22: the standing frame only takes over once the
        // 2 s outro hold has run out.
        assertEquals(
            DancerActivity.Phase.STILL,
            DancerActivity.phase(end + DancerActivity.OUTRO_HOLD_MS, first, end),
        )
        assertEquals(
            DancerActivity.Phase.STILL,
            DancerActivity.phase(end + DancerActivity.OUTRO_HOLD_MS + 60_000L, first, end),
        )
    }

    // ---- v1.0.22: the outro hold — last pose held 2 s, then a
    // short settle fade into the standing breathing phase.

    @Test
    fun outroHoldStartsTheMomentTheSingingEnds() {
        val first = 12_500L
        val end = 184_500L
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(end, first, end))
        assertEquals(DancerActivity.Phase.OUTRO_HOLD, DancerActivity.phase(end + 1, first, end))
        assertEquals(
            DancerActivity.Phase.OUTRO_HOLD,
            DancerActivity.phase(end + DancerActivity.OUTRO_HOLD_MS - 1, first, end),
        )
        assertEquals(
            DancerActivity.Phase.STILL,
            DancerActivity.phase(end + DancerActivity.OUTRO_HOLD_MS, first, end),
        )
    }

    @Test
    fun outroHoldPoseSettlesToNeutral() {
        val end = 184_500L
        val frozen = DanceMotion.Pose(
            bounceFraction = 0.8f, rotationDeg = 6f, scale = 1.05f, sinkFraction = 0.2f,
        )
        // At the boundary the frozen pose is untouched…
        assertEquals(frozen, DancerActivity.outroHoldPose(frozen, end, end))
        // …half-way it has relaxed part of the way (smoothstep:
        // exactly half at the midpoint)…
        val mid = DancerActivity.outroHoldPose(frozen, end + DancerActivity.OUTRO_HOLD_MS / 2, end)
        assertEquals(0.4f, mid.bounceFraction, 0.001f)
        assertEquals(3f, mid.rotationDeg, 0.001f)
        assertEquals(1.025f, mid.scale, 0.001f)
        assertEquals(0.1f, mid.sinkFraction, 0.001f)
        // …and at the hold's end it is fully neutral.
        val done = DancerActivity.outroHoldPose(frozen, end + DancerActivity.OUTRO_HOLD_MS, end)
        assertEquals(DanceMotion.Pose(0f, 0f, 1f, 0f), done)
    }

    @Test
    fun outroSettleFadesOnlyInItsWindow() {
        val end = 184_500L
        val settleStart = end + DancerActivity.OUTRO_HOLD_MS
        assertNull(DancerActivity.outroSettleAlpha(settleStart - 1, end))
        assertEquals(0f, DancerActivity.outroSettleAlpha(settleStart, end))
        assertEquals(
            0.5f,
            DancerActivity.outroSettleAlpha(settleStart + DancerActivity.OUTRO_SETTLE_MS / 2, end)!!,
            0.001f,
        )
        assertNull(DancerActivity.outroSettleAlpha(settleStart + DancerActivity.OUTRO_SETTLE_MS, end))
        assertNull(DancerActivity.outroSettleAlpha(settleStart, null))
    }

    @Test
    fun breathingPhaseContinuesAcrossTheSettle() {
        // breathPose is an absolute-position function, so the
        // breath the dancer breathes right after the settle is
        // the same one it would have breathed had the standing
        // frame been up all along — no phase jump at the seam.
        val end = 184_500L
        val seam = end + DancerActivity.OUTRO_HOLD_MS + DancerActivity.OUTRO_SETTLE_MS
        val atSeam = DancerActivity.breathPose(seam, DanceMotion.Profile.MODERN)
        val onePeriodLater = DancerActivity.breathPose(
            seam + DancerActivity.BREATH_PERIOD_MS, DanceMotion.Profile.MODERN,
        )
        assertEquals(atSeam, onePeriodLater)
    }

    @Test
    fun shortOutroEndsInsideTheHold() {
        // Outro of only 0.5 s: the song is over before the hold
        // could finish — positions inside the tail still read
        // HOLD, positions at/past the duration read STILL, and
        // nothing forces the full hold to play out.
        val first = 12_500L
        val end = 184_500L
        val duration = end + 500L
        assertEquals(
            DancerActivity.Phase.OUTRO_HOLD,
            DancerActivity.phase(end + 250, first, end, duration),
        )
        assertEquals(
            DancerActivity.Phase.STILL,
            DancerActivity.phase(duration, first, end, duration),
        )
        assertEquals(
            DancerActivity.Phase.STILL,
            DancerActivity.phase(duration + 5_000, first, end, duration),
        )
    }

    @Test
    fun noLyricSongNeverEntersTheOutroHold() {
        assertEquals(DancerActivity.Phase.DANCING, DancerActivity.phase(999_999L, null, null))
        assertNull(DancerActivity.outroSettleAlpha(999_999L, null))
    }

    @Test
    fun inviteStartsUpright() {
        val pose = DancerActivity.invitePose(0L, human)
        assertEquals(0f, pose.rotationDeg, 0.0001f)
        assertEquals(0f, pose.sinkFraction, 0.0001f)
        assertEquals(0f, pose.bounceFraction, 0.0001f)
        assertEquals(1f, pose.scale, 0.0001f)
    }

    @Test
    fun inviteBowsDeepestHalfwayThroughThePeriod() {
        val pose = DancerActivity.invitePose(DancerActivity.INVITE_PERIOD_MS / 2, human)
        assertEquals(DancerActivity.INVITE_LEAN_DEG, pose.rotationDeg, 0.0001f)
        assertEquals(DancerActivity.INVITE_SINK_AMP, pose.sinkFraction, 0.0001f)
    }

    @Test
    fun inviteReturnsUprightAfterAFullPeriod() {
        val start = DancerActivity.invitePose(0L, human)
        val after = DancerActivity.invitePose(DancerActivity.INVITE_PERIOD_MS, human)
        assertEquals(start.rotationDeg, after.rotationDeg, 0.0001f)
        assertEquals(start.sinkFraction, after.sinkFraction, 0.0001f)
    }

    @Test
    fun inviteIsPeriodic() {
        for (t in listOf(1L, 500L, 1999L, 2000L, 87_654L)) {
            val a = DancerActivity.invitePose(t, human)
            val b = DancerActivity.invitePose(t + DancerActivity.INVITE_PERIOD_MS, human)
            assertEquals(a.rotationDeg, b.rotationDeg, 0.0001f)
            assertEquals(a.sinkFraction, b.sinkFraction, 0.0001f)
        }
    }

    @Test
    fun inviteNeverBouncesOrSwells() {
        var t = 0L
        while (t <= DancerActivity.INVITE_PERIOD_MS) {
            val pose = DancerActivity.invitePose(t, human)
            assertEquals(0f, pose.bounceFraction, 0.0001f)
            assertEquals(1f, pose.scale, 0.0001f)
            assertTrue(pose.rotationDeg >= 0f && pose.rotationDeg <= DancerActivity.INVITE_LEAN_DEG + 0.0001f)
            assertTrue(pose.sinkFraction >= 0f && pose.sinkFraction <= DancerActivity.INVITE_SINK_AMP + 0.0001f)
            t += 100L
        }
    }

    @Test
    fun robotInvitesAtHalfAmplitude() {
        val half = DancerActivity.INVITE_PERIOD_MS / 2
        val robot = DancerActivity.invitePose(half, DanceMotion.Profile.ROBOT)
        assertEquals(DancerActivity.INVITE_LEAN_DEG / 2, robot.rotationDeg, 0.0001f)
        assertEquals(DancerActivity.INVITE_SINK_AMP / 2, robot.sinkFraction, 0.0001f)
    }

    @Test
    fun invitePeriodIsAboutFourSeconds() {
        // v1.0.22: the user asked for the intro invite bow to run
        // "慢些再慢些" — one full lean cycle now takes 4 s, twice
        // the v1.0.7 period; amplitude and shape are unchanged.
        assertEquals(4_000L, DancerActivity.INVITE_PERIOD_MS)
    }

    // ---- v1.0.32: the intro step-forward envelope ----
    //
    // "邀请的时候伴舞向前一大步，变大。跳舞时再退后" — during the
    // intro invite the dancer steps toward the audience (progress
    // 1 = fully forward at the bigger size); the moment the
    // singing starts she steps back to dancing size (progress 0).
    // The renderer maps progress onto her layout height; here we
    // pin the envelope itself.

    private val introStart = 18_350L // 《海阔天空》's real first sung line

    @Test
    fun stepRampIsPointSevenSeconds() {
        assertEquals(700L, DancerActivity.STEP_RAMP_MS)
    }

    @Test
    fun stepNeverHappensWithoutLyrics() {
        for (t in listOf(0L, 350L, 10_000L, 3_600_000L)) {
            assertEquals(0f, DancerActivity.stepProgress(t, null, human), 0.0001f)
        }
    }

    @Test
    fun stepNeverHappensForShortIntroSongs() {
        // First sung line at 2.4 s: no INVITING phase exists, so
        // there is nothing to step forward in — not even at the
        // positions where an invite would have run.
        val first = 2_400L
        for (t in listOf(0L, 350L, 700L, 1_200L, 2_399L, 2_400L, 3_100L, 60_000L)) {
            assertEquals(0f, DancerActivity.stepProgress(t, first, human), 0.0001f)
        }
    }

    @Test
    fun stepStartsAtDancingSize() {
        assertEquals(0f, DancerActivity.stepProgress(0L, introStart, human), 0.0001f)
        assertEquals(0f, DancerActivity.stepProgress(-500L, introStart, human), 0.0001f)
    }

    @Test
    fun stepRampsUpAcrossTheFirstRampWindow() {
        // Smoothstep: exactly half at the ramp's midpoint, fully
        // forward at its end, monotonically growing in between.
        assertEquals(
            0.5f,
            DancerActivity.stepProgress(DancerActivity.STEP_RAMP_MS / 2, introStart, human),
            0.0001f,
        )
        assertEquals(
            1f,
            DancerActivity.stepProgress(DancerActivity.STEP_RAMP_MS, introStart, human),
            0.0001f,
        )
        var prev = -1f
        var t = 0L
        while (t <= DancerActivity.STEP_RAMP_MS) {
            val p = DancerActivity.stepProgress(t, introStart, human)
            assertTrue(p >= prev)
            prev = p
            t += 50L
        }
    }

    @Test
    fun stepHoldsFullyForwardThroughTheRestOfTheIntro() {
        assertEquals(1f, DancerActivity.stepProgress(5_000L, introStart, human), 0.0001f)
        assertEquals(1f, DancerActivity.stepProgress(introStart - 1, introStart, human), 0.0001f)
        assertEquals(1f, DancerActivity.stepProgress(introStart, introStart, human), 0.0001f)
    }

    @Test
    fun stepReturnsAcrossTheSingingsFirstRampWindow() {
        assertEquals(
            0.5f,
            DancerActivity.stepProgress(
                introStart + DancerActivity.STEP_RAMP_MS / 2, introStart, human,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            DancerActivity.stepProgress(
                introStart + DancerActivity.STEP_RAMP_MS, introStart, human,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            DancerActivity.stepProgress(introStart + 60_000L, introStart, human),
            0.0001f,
        )
    }

    @Test
    fun stepStaysInsideBoundsAcrossTheWholeSong() {
        var t = 0L
        while (t <= 200_000L) {
            val p = DancerActivity.stepProgress(t, introStart, human)
            assertTrue("progress $p at $t", p >= 0f && p <= 1f)
            t += 37L
        }
    }

    @Test
    fun robotStepIsQuantisedIntoHardLevels() {
        assertEquals(4, DancerActivity.ROBOT_STEP_LEVELS)
        val levels = setOf(0f, 1f / 3f, 2f / 3f, 1f)
        var t = 0L
        while (t <= introStart + 2 * DancerActivity.STEP_RAMP_MS) {
            val p = DancerActivity.stepProgress(t, introStart, DanceMotion.Profile.ROBOT)
            assertTrue("robot progress $p at $t not a hard level", levels.any { kotlin.math.abs(it - p) < 0.0001f })
            t += 33L // the player's own polling step
        }
    }

    @Test
    fun robotStepStillReachesFullAndReturns() {
        val robot = DanceMotion.Profile.ROBOT
        assertEquals(0f, DancerActivity.stepProgress(0L, introStart, robot), 0.0001f)
        assertEquals(
            1f,
            DancerActivity.stepProgress(DancerActivity.STEP_RAMP_MS, introStart, robot),
            0.0001f,
        )
        assertEquals(1f, DancerActivity.stepProgress(introStart, introStart, robot), 0.0001f)
        assertEquals(
            0f,
            DancerActivity.stepProgress(
                introStart + DancerActivity.STEP_RAMP_MS, introStart, robot,
            ),
            0.0001f,
        )
        // Mid-ramp the robot sits on a step, never between steps:
        // the smooth envelope's midpoint 0.5 quantises to 2/3.
        assertEquals(
            2f / 3f,
            DancerActivity.stepProgress(DancerActivity.STEP_RAMP_MS / 2, introStart, robot),
            0.0001f,
        )
    }

    @Test
    fun stepAlignsWithTheInvitingPhase() {
        // Fully forward exactly while INVITING runs (past the
        // opening ramp), back at dancing size once DANCING is
        // under way — the envelope never leaks into the outro.
        val end = 184_500L
        assertEquals(
            DancerActivity.Phase.INVITING,
            DancerActivity.phase(introStart - 1, introStart, end),
        )
        assertEquals(1f, DancerActivity.stepProgress(introStart - 1, introStart, human), 0.0001f)
        assertEquals(
            0f,
            DancerActivity.stepProgress(end + DancerActivity.OUTRO_HOLD_MS + 1_000L, introStart, human),
            0.0001f,
        )
    }
}
