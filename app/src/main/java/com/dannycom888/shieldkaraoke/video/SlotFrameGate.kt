package com.dannycom888.shieldkaraoke.video

/**
 * v1.0.78: the first-frame gate for one video texture slot —
 * the pure visibility decision behind the renderer's draw
 * loop (see ChromaKeyRenderer in AmyVideoStage.kt).
 *
 * The defect it closes (device, Note10+, v1.0.77): Amy's
 * video area showed an opaque BLACK box for ~2 seconds on
 * a cold app open, and for ~1 second at every song start,
 * before the keying "started working". Nothing was wrong
 * with the keying: the GL loop draws the front slot from
 * the surface's very first frame, but the player needs a
 * second or so to spin up (and to load each new song's
 * item), and a SurfaceTexture that has not delivered the
 * current item's frame yet samples as black (0,0,0).
 * Black's chroma sits ~0.49 from the key green's — far
 * past the shader's 0.34 ramp — so the shader faithfully
 * paints the quad alpha 1: a solid black rectangle with
 * correctly-keyed nothing. Once the first real frame
 * landed, the same math keyed it away, which is why the
 * box "fixed itself".
 *
 * The rule: a slot is invisible from each media load
 * until the NEW item's first frame has been rendered, then
 * it fades in over [APPEAR_FADE_MS] (the factor multiplies
 * the slot alpha that feeds the shader's uGlobalAlpha).
 * The gate is per load, not per texture: after a media
 * swap the texture can still hold the PREVIOUS clip's last
 * frame, so "the texture has a frame" proves nothing — the
 * driver closes the gate at every load (cold start, every
 * song start, every mode switch, the dance preload) and
 * only the new item's first rendered frame — the player's
 * own onRenderedFirstFrame, with the texture's
 * frame-available callback as the second signal — opens
 * it again. Fresh textures (a recreated GL context) start
 * closed the same way.
 */
object SlotFrameGate {

    /** How long a slot takes to fade in once its first
     *  frame has landed: long enough to soften the figure's
     *  appearance into a fade rather than a pop, short
     *  enough to never read as an effect of its own. */
    const val APPEAR_FADE_MS = 250L

    /**
     * This frame's visibility factor for a slot whose
     * current load rendered its first frame at
     * [firstFrameUptimeMs] (SystemClock.uptimeMillis;
     * 0 = no frame for this load yet), evaluated at
     * [nowMs]: exactly 0 before that frame, ramping
     * linearly to 1 over [APPEAR_FADE_MS] after it, 1
     * from then on (until the next load closes the gate).
     */
    fun alphaFactor(firstFrameUptimeMs: Long, nowMs: Long): Float {
        if (firstFrameUptimeMs <= 0L) return 0f
        val elapsedMs = nowMs - firstFrameUptimeMs
        if (elapsedMs <= 0L) return 0f
        return (elapsedMs.toFloat() / APPEAR_FADE_MS).coerceAtMost(1f)
    }
}
