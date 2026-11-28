package com.dannycom888.shieldkaraoke.video

/**
 * The Amy video figure's size computation (v1.0.73) — the
 * ONE place the dance path's sizing arithmetic lives, so
 * the unit tests exercise the exact numbers the stage
 * renders with (the v1.0.72 wiring only pinned the JSON
 * data; the audit for this build re-checked the whole
 * path — data → parse → driver → composable → GL — and
 * this object is where the rendered size is decided).
 *
 * The law, unchanged since v1.0.71/.72: the video box is
 * sized by [VIDEO_FIGURE_SCALE] off the caller's figure
 * height (her standing height lands where the frame
 * figure's stands), and each clip is drawn at its own
 * measured [AmyClip.figureScale] — applied INSIDE the GL
 * draw since v1.0.73 (the v1.0.72 law multiplied it into
 * the Compose box instead, which resized the AndroidView
 * — and churned its surface — at every chain switch even
 * though the figure inside was already normalized).
 *
 * A clip's on-stage figure height, in units of the
 * caller's heightDp, is therefore:
 *
 *   VIDEO_FIGURE_SCALE × figureScale × figureHeight
 *
 * and because packaging set figureScale = anchor ÷
 * figureHeight (anchor = idle's measured height), that
 * product is the same for every clip — the property the
 * sizing test asserts for all twenty dance clips.
 */
object AmyVideoSizing {

    /** The foot-baseline fallback for a clip whose
     *  packaged JSON predates the feet_v field: the pack's
     *  measured median (v1.0.73; the 24 clips span
     *  0.035..0.120). */
    const val DEFAULT_FEET_V = 0.06

    /**
     * The GL draw scale for [clip]'s texture slot: the
     * clip's measured figure factor, as a float for the
     * renderer. Every slot the stage draws — dance front,
     * dance preload, and the specials — takes its scale
     * from here.
     */
    fun slotScale(clip: AmyClip): Float = clip.figureScale.toFloat()

    /**
     * v1.0.88: the dance figure enlargement the user
     * ordered on 2026-11-28 ("将跳舞时候的动作全部放大
     * 20%"): while she DANCES, Amy renders 1.2× larger —
     * the pack invariant figureHeight × figureScale
     * goes from the anchor 0.6338 to ≈0.7606 (the
     * rendered height carries the 0.952 box factor on
     * top, 0.6034 → 0.7240 of the caller's height).
     * The factor rides the existing GL slot scale (the
     * renderer scales around the foot baseline, so the
     * feet, the reflection and the pool's baseline never
     * move), and it applies ONLY to the dance slots:
     * the specials (invite / idle / clap / combo) keep
     * [slotScale] untouched, as do the frame dancers.
     * Geometry check at the ruling (wave 77): the widest
     * dance figure in the pack (fast-03, scale × extent
     * 0.7604 by the packaging figure mask) draws
     * 0.556 × dancerH wide at 1.2× — inside the frame
     * box (0.78 × dancerH) with ≈127dp of clearance to
     * the lyrics band even at the invite step's peak
     * height on a 16:9 stage; the GL quad's transparent
     * margins keep 19% headroom inside the canvas.
     */
    const val DANCE_FIGURE_ENLARGEMENT = 1.2f

    /**
     * The GL draw scale for a DANCE slot: the clip's
     * measured factor times [DANCE_FIGURE_ENLARGEMENT].
     * Dance loads only — the specials never call this.
     */
    fun danceSlotScale(clip: AmyClip): Float =
        slotScale(clip) * DANCE_FIGURE_ENLARGEMENT

    /**
     * The texture v of [clip]'s foot baseline (the green
     * margin below the boots, as a fraction of the frame
     * height), for the renderer's feet anchoring: the
     * figure samples v in [feetV, feetV + 1] so the feet
     * row lands exactly on the stage baseline, and the
     * reflection mirrors from that same row — no gap at
     * the feet. Set on the slot alongside [slotScale]
     * whenever a clip is loaded into it.
     */
    fun slotFeetV(clip: AmyClip): Float = clip.feetV.toFloat()

    /**
     * [clip]'s figure height on stage, as a fraction of
     * the figure height the caller hands the stage
     * (heightDp): the box scale times the clip's draw
     * scale times the fraction of the texture the figure
     * occupies. Equals VIDEO_FIGURE_SCALE × the pack
     * anchor for every correctly packaged clip.
     */
    fun onStageFigureHeight(clip: AmyClip): Double =
        VIDEO_FIGURE_SCALE.toDouble() * clip.figureScale * clip.figureHeight

    /**
     * [clip]'s figure height on stage WHILE DANCING
     * (v1.0.88): the [onStageFigureHeight] law times
     * [DANCE_FIGURE_ENLARGEMENT] — every dance clip's
     * rendered height lands 1.2× its unenlarged value,
     * on the one shared anchor.
     */
    fun onStageDanceFigureHeight(clip: AmyClip): Double =
        onStageFigureHeight(clip) * DANCE_FIGURE_ENLARGEMENT.toDouble()
}
