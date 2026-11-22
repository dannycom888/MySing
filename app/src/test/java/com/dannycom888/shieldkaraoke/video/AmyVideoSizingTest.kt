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
        // The anchor as the computation itself produces
        // it: idle's on-stage height. v1.0.80 (pack2):
        // idle is no longer the anchor SOURCE — the stage
        // anchor is the frozen constant 0.6338 (the
        // original pack2 idle's measured height, frozen at
        // wave 52 when idle was re-shot), and idle carries
        // its own measured slot scale, 0.9981.
        assertEquals(0.9981f, AmyVideoSizing.slotScale(idle), 1e-6f)
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
        // fast-01 (drawn 1.1318× — pack2's fast set is
        // composed wide at 0.56 native height) and slow-01
        // (0.9962×) land on the same stage height.
        val fast01 = dance.first { it.name == "fast-01" }
        val slow01 = dance.first { it.name == "slow-01" }
        assertEquals(
            AmyVideoSizing.onStageFigureHeight(fast01),
            AmyVideoSizing.onStageFigureHeight(slow01),
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
        // v1.0.80 (pack2): every clip is composed onto ONE
        // shared geometry — feet baseline at 0.95H — so the
        // foot margin is the same 0.05 on all 24 clips. The
        // old pack's per-clip spread (0.035..0.121) that
        // forced a per-clip anchor is gone by construction;
        // the pin is now uniformity.
        for (clip in clips) {
            assertEquals("${clip.name} feetV", 0.05, clip.feetV, 1e-9)
        }
    }
}
