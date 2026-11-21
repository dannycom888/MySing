package com.dannycom888.shieldkaraoke.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * v1.0.73: the figure_scale audit's second half — not the
 * JSON data (AmyVideoPackTest pins that), but the SIZE
 * COMPUTATION the dance path actually renders with:
 * [AmyVideoSizing.onStageFigureHeight], the product of
 * the box scale, the slot's GL draw scale and the clip's
 * measured figure fraction. For every shipped dance clip
 * it must land on the pack's one anchor within 1%, or a
 * chain switch changes Amy's size mid-song even with
 * perfect data.
 */
class AmyVideoSizingTest {

    private fun assetDir(): File {
        val candidates = listOf(
            File("src/main/assets/${AmyVideoPack.ASSET_DIR}"),
            File("app/src/main/assets/${AmyVideoPack.ASSET_DIR}"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: throw AssertionError("amy_video assets not found; tried ${candidates.map { it.absolutePath }}")
    }

    private fun shippedClips(): List<AmyClip> =
        assetDir().listFiles { f -> f.name.endsWith(".accents.json") }
            .orEmpty()
            .map { AmyVideoPack.parseAccentsJson(it.readText()) }
            .sortedBy { it.name }

    @Test
    fun `the dance path's size computation lands every dance clip on the anchor`() {
        val clips = shippedClips()
        val idle = clips.first { it.name == AmyVideoPack.SPECIAL_IDLE }
        // The anchor as the computation itself produces it:
        // idle is the clip VIDEO_FIGURE_SCALE was tuned
        // against (its slot scale is exactly 1).
        assertEquals(1f, AmyVideoSizing.slotScale(idle), 1e-6f)
        val anchor = AmyVideoSizing.onStageFigureHeight(idle)
        assertTrue("anchor must be a real height", anchor > 0.5)
        val dance = clips.filter { it.group != AmyVideoPack.GROUP_SPECIAL }
        assertEquals(20, dance.size)
        for (clip in dance) {
            val height = AmyVideoSizing.onStageFigureHeight(clip)
            assertTrue(
                "${clip.name} on-stage height $height vs anchor $anchor (>1% off)",
                abs(height - anchor) <= 0.01 * anchor,
            )
        }
        // The widest spread in the pack, for the record:
        // slow-08 (drawn 1.242×) and slow-06 (0.991×) land
        // on the same stage height.
        val slow08 = dance.first { it.name == "slow-08" }
        val slow06 = dance.first { it.name == "slow-06" }
        assertEquals(
            AmyVideoSizing.onStageFigureHeight(slow08),
            AmyVideoSizing.onStageFigureHeight(slow06),
            0.01 * anchor,
        )
    }

    @Test
    fun `every shipped clip carries its measured foot baseline`() {
        val clips = shippedClips()
        assertEquals(24, clips.size)
        for (clip in clips) {
            // A real green margin below the boots exists on
            // every clip — the float the user saw on v1.0.72
            // — and it stays a margin, not half the frame.
            // The sizing surface the stage reads must hand
            // the renderer exactly the packaged value.
            assertTrue(
                "${clip.name} feetV ${clip.feetV} outside the measured band",
                clip.feetV in 0.02..0.15,
            )
            assertEquals(
                clip.feetV.toFloat(), AmyVideoSizing.slotFeetV(clip), 1e-6f,
            )
        }
        // The spread that forces a per-clip anchor, for the
        // record: slow-08's boots sit 0.121 of the frame
        // above its bottom edge, fast-03's only 0.035.
        // (0.120 until v1.0.76; the v1.0.77 bookend-splice
        // re-encode moved slow-08's p95 by one asset row.)
        assertEquals(0.121, clips.first { it.name == "slow-08" }.feetV, 1e-9)
        assertEquals(0.035, clips.first { it.name == "fast-03" }.feetV, 1e-9)
    }
}
