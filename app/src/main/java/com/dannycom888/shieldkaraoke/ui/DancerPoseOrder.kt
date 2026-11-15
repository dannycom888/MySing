package com.dannycom888.shieldkaraoke.ui

import com.dannycom888.shieldkaraoke.state.DancerKeys

/**
 * The order the forty dance-pose frames are stepped through
 * (v1.0.17).
 *
 * The frames were cut from pose sheets in sheet order, which is
 * not a dance order: consecutive sheet cells can be unrelated
 * poses, so one-frame-per-beat stepping jumped randomly between
 * poses ("姿势乱跳，前后接不上" — the user, 2026-11-15; the
 * v1.0.10 cross-fade could not fix an order problem). The frame
 * artwork is unchanged; only the playback order changed.
 *
 * Each list below is a permutation of frame indices 0..39 into
 * `Dancer.frames`, computed offline by `dancers/resequence.py`:
 * pairwise frame distance = RMS difference of premultiplied-RGBA
 * thumbnails of the frames composited exactly as the renderer
 * draws them (shared canvas, bottom-centred), then a closed tour
 * minimising the total adjacent cost (greedy nearest-neighbour +
 * 2-opt; the wrap edge last→first counts, so the loop closes
 * smoothly too). Frame 0 — the neutral standing frame — is pinned
 * to the head of every tour, so dancing starts from the standing
 * pose. Mean adjacent cost before → after (see BUILD_REPORT
 * v1.0.17): modern 0.2625→0.2116, ancient 0.2835→0.2276, sumo
 * 0.2359→0.1838, ancient_girl 0.2051→0.1658, baby 0.1879→0.1484,
 * robot 0.2976→0.2565.
 *
 * Re-run the tool and paste fresh literals here if the frame art
 * is ever re-cut; [DancerPoseOrderTest] pins every list to a true
 * permutation of 0..39.
 */
object DancerPoseOrder {

    /** Identity order (sheet order), the fallback for unknown keys. */
    val IDENTITY: List<Int> = List(40) { it }

    private val ORDERS: Map<String, List<Int>> = mapOf(
        DancerKeys.MODERN to listOf(
            0, 32, 14, 4, 39, 37, 16, 7, 24, 13,
            26, 38, 33, 1, 19, 8, 28, 3, 2, 29,
            11, 12, 34, 9, 18, 20, 10, 27, 25, 5,
            15, 31, 6, 23, 22, 21, 35, 36, 17, 30,
        ),
        DancerKeys.ANCIENT to listOf(
            0, 35, 19, 28, 27, 2, 22, 33, 34, 38,
            17, 14, 26, 24, 36, 12, 7, 6, 5, 3,
            4, 39, 23, 25, 15, 21, 32, 10, 16, 37,
            1, 11, 13, 31, 9, 29, 18, 8, 30, 20,
        ),
        DancerKeys.SUMO to listOf(
            0, 15, 12, 30, 6, 37, 7, 2, 24, 27,
            22, 20, 21, 26, 28, 29, 5, 23, 19, 36,
            8, 17, 14, 11, 10, 39, 32, 33, 34, 18,
            35, 25, 31, 13, 16, 1, 4, 3, 9, 38,
        ),
        DancerKeys.ANCIENT_GIRL to listOf(
            0, 26, 22, 38, 32, 17, 31, 27, 36, 21,
            29, 24, 9, 15, 5, 4, 18, 39, 33, 19,
            37, 7, 3, 34, 8, 23, 16, 2, 30, 1,
            11, 10, 14, 13, 12, 25, 20, 35, 28, 6,
        ),
        DancerKeys.BABY to listOf(
            0, 14, 11, 15, 16, 5, 12, 13, 34, 32,
            2, 31, 19, 9, 24, 39, 26, 36, 27, 7,
            3, 28, 23, 6, 35, 10, 17, 21, 4, 33,
            20, 37, 8, 18, 22, 38, 29, 1, 25, 30,
        ),
        DancerKeys.ROBOT to listOf(
            0, 2, 10, 32, 14, 29, 23, 22, 34, 19,
            31, 15, 6, 9, 35, 26, 24, 16, 33, 18,
            38, 8, 28, 7, 20, 21, 13, 17, 11, 1,
            4, 39, 30, 3, 12, 37, 5, 36, 25, 27,
        ),
    )

    /** The dance order for [key]; unknown keys get [IDENTITY]. */
    fun orderFor(key: String?): List<Int> = ORDERS[key] ?: IDENTITY
}
