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
 * permutation of 0..59.
 */
object DancerPoseOrder {

    /** Identity order (sheet order), the fallback for unknown keys. */
    val IDENTITY: List<Int> = List(60) { it }

    private val ORDERS: Map<String, List<Int>> = mapOf(
        DancerKeys.MODERN to listOf(
            0, 30, 58, 14, 4, 44, 39, 37, 16, 20,
            10, 56, 47, 49, 48, 59, 11, 12, 29, 2,
            3, 28, 19, 8, 54, 1, 43, 38, 33, 26,
            32, 17, 36, 35, 40, 22, 52, 27, 45, 55,
            46, 57, 41, 21, 6, 23, 5, 25, 31, 15,
            51, 18, 9, 34, 42, 53, 7, 24, 13, 50,
        ),
        DancerKeys.ANCIENT to listOf(
            0, 47, 35, 19, 49, 14, 26, 24, 48, 34,
            38, 17, 51, 36, 46, 12, 7, 1, 11, 13,
            31, 42, 56, 43, 28, 27, 2, 22, 33, 9,
            29, 18, 8, 59, 39, 23, 25, 41, 15, 3,
            4, 40, 6, 5, 50, 52, 30, 20, 58, 32,
            21, 45, 57, 44, 37, 16, 10, 53, 54, 55,
        ),
        DancerKeys.SUMO to listOf(
            0, 57, 56, 41, 50, 38, 9, 3, 4, 1,
            16, 13, 31, 25, 46, 52, 5, 37, 44, 7,
            2, 54, 24, 27, 20, 21, 22, 49, 29, 45,
            28, 26, 58, 12, 15, 30, 6, 53, 35, 18,
            34, 33, 32, 39, 10, 11, 14, 55, 43, 36,
            8, 17, 42, 51, 59, 23, 19, 47, 48, 40,
        ),
        DancerKeys.ANCIENT_GIRL to listOf(
            0, 6, 28, 25, 20, 35, 48, 42, 33, 39,
            18, 4, 5, 15, 9, 41, 17, 49, 55, 32,
            38, 22, 43, 50, 52, 40, 56, 47, 51, 44,
            27, 31, 57, 36, 59, 2, 16, 19, 37, 7,
            3, 34, 8, 23, 58, 54, 24, 29, 21, 53,
            46, 45, 12, 13, 14, 10, 11, 1, 30, 26,
        ),
        DancerKeys.BABY to listOf(
            0, 57, 44, 56, 48, 45, 40, 8, 18, 55,
            35, 59, 6, 23, 28, 3, 7, 27, 42, 37,
            58, 20, 33, 4, 21, 17, 10, 22, 47, 54,
            9, 24, 49, 41, 43, 52, 50, 51, 46, 53,
            39, 26, 36, 5, 16, 15, 19, 31, 2, 32,
            34, 12, 13, 38, 29, 1, 25, 30, 11, 14,
        ),
        DancerKeys.ROBOT to listOf(
            0, 59, 40, 52, 41, 44, 53, 50, 56, 57,
            42, 37, 12, 3, 30, 45, 39, 48, 27, 25,
            36, 5, 4, 1, 11, 17, 13, 21, 20, 31,
            19, 47, 34, 33, 16, 24, 26, 35, 9, 6,
            15, 14, 51, 18, 8, 38, 29, 23, 22, 28,
            7, 55, 43, 46, 54, 49, 58, 32, 10, 2,
        ),
    )

    /** The dance order for [key]; unknown keys get [IDENTITY]. */
    fun orderFor(key: String?): List<Int> = ORDERS[key] ?: IDENTITY
}
