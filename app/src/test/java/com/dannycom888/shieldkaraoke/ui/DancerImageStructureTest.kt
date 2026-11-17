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
 *
 * v1.0.57 amendment: the figure stack is now defined once (a
 * `figureLayers` lambda holding the same three DancerFrame
 * calls) and drawn twice — upright in the v1.0.42 frame box,
 * and once more as the stage floor reflection (flipped via
 * [StageFloor.reflectionScaleY], faded via a DstIn gradient).
 * The old "exactly one Box" pin is superseded by the new shape
 * (wrapper / frame box / reflection node = three), but the
 * invariant it protected is unchanged and still pinned here:
 * one fixed rect, both cross-fade layers inside it, and the
 * reflection is the SAME stack — never a second, separately
 * resolved frame draw that could drift from the figure.
 *
 * v1.0.61 amendment: the contact shadow joins the frame rect
 * as a draw modifier in the chain — NOT a fourth node (the
 * three-node pin above stands) — and its paint order is the
 * point: pool first, shadow over the pool, figure over both.
 * The shadow is opt-in (a Boolean parameter, default off), so
 * only the call sites that ask for it — the solo placement —
 * ever paint it.
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
            "the fixed size must be set on the frame box before the figure stack is drawn into it",
            body.indexOf(".width(SpotlightGeom.dancerWidthDp") < body.indexOf("figureLayers()"),
        )
    }

    @Test
    fun bothCrossFadeLayersShareTheOneBottomCenterRect() {
        val body = dancerImageBody()
        assertEquals(
            "the figure stack must be defined once: previous + current blend layers plus the single draw (3 DancerFrame calls)",
            3,
            Regex("""DancerFrame\(""").findAll(body).count(),
        )
        assertEquals(
            "the one figure stack is drawn twice: upright, and once more as the floor reflection",
            2,
            Regex("""figureLayers\(\)""").findAll(body).count(),
        )
        assertEquals(
            "wrapper + frame box + reflection node: three Boxes, no more (a fourth would be a second frame rect)",
            3,
            Regex("""Box\(""").findAll(body).count(),
        )
        assertEquals(
            "the frame box and the reflection node both BottomCenter-align the stack (feet on the floor line, canvas centres coincident)",
            2,
            Regex("""contentAlignment = Alignment\.BottomCenter""").findAll(body).count(),
        )
    }

    @Test
    fun contactShadowPaintsOverThePoolAndUnderTheFigure() {
        val body = dancerImageBody()
        // Opt-in only: the parameter defaults off, so a call
        // site that does not ask for the shadow never gets one.
        assertTrue(
            "the contact shadow must be an opt-in parameter, default off",
            body.contains("contactShadow: Boolean = false"),
        )
        // The layer order is the modifier chain's order: the
        // pool's draw modifier is applied first (it paints
        // first), the shadow's next (it paints over the pool),
        // and the figure stack is the content both paint
        // behind.
        val pool = body.indexOf("Modifier.spotlightPool")
        val shadow = body.indexOf("Modifier.contactShadow")
        val figure = body.indexOf("figureLayers()")
        assertTrue("the pool modifier must be applied", pool >= 0)
        assertTrue(
            "the shadow paints over the pool",
            shadow > pool,
        )
        assertTrue(
            "the figure paints over the shadow",
            figure > shadow,
        )
    }

    @Test
    fun reflectionIsTheSameStackFlippedAndFaded() {
        val body = dancerImageBody()
        assertTrue(
            "the reflection must flip via StageFloor's pinned scale (a true mirror onto the reflection band)",
            body.contains("StageFloor.reflectionScaleY"),
        )
        assertTrue(
            "the reflection must pivot on StageFloor's pinned origin fraction (feet stay on the baseline)",
            body.contains("StageFloor.reflectionOriginFraction"),
        )
        assertTrue(
            "the reflection must fade downward through an offscreen DstIn mask, not a flat alpha",
            body.contains("CompositingStrategy.Offscreen") && body.contains("BlendMode.DstIn"),
        )
        assertTrue(
            "the reflection is drawn after the upright figure (it lives under the feet, never over the body)",
            body.indexOf("StageFloor.reflectionScaleY(") > body.indexOf(".height(heightDp)"),
        )
    }
}
