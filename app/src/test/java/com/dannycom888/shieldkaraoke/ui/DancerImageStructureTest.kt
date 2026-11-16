package com.dannycom888.shieldkaraoke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.0.42 structural pin for the whole-body sideways flash fix
 * (the user on v1.0.41: "不是自然律动，是整个身体左右闪").
 * Root cause was layout, not art: DancerFrame's Image sets only
 * its height, so its width follows each frame's own canvas
 * aspect (measured 0.195–0.78 across the library), and
 * DancerImage's frame box sized itself to its widest child with
 * the default TopStart alignment. Through every beat's
 * cross-fade the narrower of the two layers sat
 * (wide − narrow)/2 off its solo position and snapped back to
 * centre when the fade ended. DancerImage must therefore keep
 * its v1.0.42 shape: ONE frame box with a FIXED width derived
 * from [SpotlightGeom.FRAME_ASPECT] (via dancerWidthDp), height
 * = heightDp, contentAlignment = BottomCenter, holding both
 * cross-fade layers and the single draw in that same rect.
 * DancerImage is Compose UI, so the invariant is pinned on the
 * source, the same file-reading style as SweepLineStructureTest.
 */
class DancerImageStructureTest {

    private fun dancerImageBody(): String {
        val candidates = listOf(
            File("src/main/java/com/dannycom888/shieldkaraoke/ui/MainActivity.kt"),
            File("app/src/main/java/com/dannycom888/shieldkaraoke/ui/MainActivity.kt"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("MainActivity.kt not found; tried ${candidates.map { it.absolutePath }}")
        val src = file.readText()
        val start = src.indexOf("private fun DancerImage(")
        assertTrue("DancerImage must exist", start >= 0)
        val end = src.indexOf("\n    }\n", start)
        assertTrue("DancerImage must terminate", end > start)
        return src.substring(start, end)
    }

    @Test
    fun frameBoxHasFixedNominalWidthAndHeight() {
        val body = dancerImageBody()
        assertTrue(
            "the frame box width must be fixed from the nominal frame aspect (SpotlightGeom.dancerWidthDp)",
            body.contains(".width(SpotlightGeom.dancerWidthDp(heightDp.value).dp)"),
        )
        assertTrue(
            "the frame box height must be fixed at heightDp",
            body.contains(".height(heightDp)"),
        )
        assertTrue(
            "the fixed size must be set on the container before any DancerFrame is drawn",
            body.indexOf(".width(SpotlightGeom.dancerWidthDp") < body.indexOf("DancerFrame("),
        )
    }

    @Test
    fun bothCrossFadeLayersShareTheOneBottomCenterRect() {
        val body = dancerImageBody()
        assertTrue(
            "the frame box must BottomCenter-align its layers (feet on the floor, canvas centres coincident)",
            body.contains("contentAlignment = Alignment.BottomCenter"),
        )
        assertEquals(
            "DancerImage must contain exactly one frame Box, so the two cross-fade layers share the same fixed rect",
            1,
            Regex("""Box\(""").findAll(body).count(),
        )
        assertEquals(
            "the blend branch must still draw previous + current layers plus the single draw (3 DancerFrame calls)",
            3,
            Regex("""DancerFrame\(""").findAll(body).count(),
        )
    }
}
