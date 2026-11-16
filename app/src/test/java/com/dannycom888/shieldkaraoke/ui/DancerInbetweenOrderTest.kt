package com.dannycom888.shieldkaraoke.ui

import com.dannycom888.shieldkaraoke.state.DancerKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.33: the in-between splice (see [DancerPoseOrder]). The
 * five human dancers' tours grow 60 -> 100 steps by inserting
 * generated in-between frames (frames 60..99 of `Dancer.frames`)
 * into the widest gaps of the v1.0.17/22 tour. These tests pin
 * the splice against the assets record in
 * dancers/inbetween/MANIFEST.md (derived on disk by
 * dancers/inbetween/integrate_v1033.py):
 *
 *  - the original sixty frames keep their exact v1.0.32
 *    relative order — nothing already dancing was re-shuffled;
 *  - after tour step p, exactly the in-between indices recorded
 *    for gap p appear, in playback order (inside a doubly-filled
 *    gap: the 1/3 frame, the midpoint, the 2/3 frame);
 *  - every in-between index 60..99 is used exactly once;
 *  - the robot's tour is byte-for-byte the v1.0.32 one.
 */
class DancerInbetweenOrderTest {

    /** The v1.0.32 (60-step) tours, pinned as the splice base. */
    private val oldOrders: Map<String, List<Int>> = mapOf(
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

    /**
     * Gap (tour step index) -> in-between frame indices spliced
     * in right after that step, in playback order. Transcribed
     * from dancers/inbetween/inbetween_orders.json, which the
     * integration generator cross-checked against MANIFEST.md
     * and the asset files on disk.
     */
    private val insertions: Map<String, Map<Int, List<Int>>> = mapOf(
        DancerKeys.MODERN to mapOf(
            0 to listOf(60), 1 to listOf(61), 2 to listOf(62, 63, 64), 3 to listOf(65),
            4 to listOf(66), 6 to listOf(67), 13 to listOf(68), 14 to listOf(69), 15 to listOf(70),
            16 to listOf(71), 20 to listOf(72), 21 to listOf(73, 74, 75), 23 to listOf(76),
            24 to listOf(77), 25 to listOf(78), 26 to listOf(79), 27 to listOf(80),
            28 to listOf(81, 82, 83), 29 to listOf(84), 30 to listOf(85), 31 to listOf(86),
            32 to listOf(87), 38 to listOf(88), 41 to listOf(89), 52 to listOf(90), 55 to listOf(91),
            56 to listOf(92, 93, 94), 57 to listOf(95, 96, 97), 58 to listOf(98), 59 to listOf(99)
        ),
        DancerKeys.ANCIENT to mapOf(
            0 to listOf(60), 1 to listOf(61), 8 to listOf(62), 9 to listOf(63), 11 to listOf(64),
            16 to listOf(65), 28 to listOf(66), 30 to listOf(67), 31 to listOf(68, 69, 70),
            32 to listOf(71), 33 to listOf(72), 36 to listOf(73), 37 to listOf(74),
            38 to listOf(75, 76, 77), 39 to listOf(78), 40 to listOf(79), 41 to listOf(80),
            43 to listOf(81), 44 to listOf(82, 83, 84), 45 to listOf(85), 46 to listOf(86),
            47 to listOf(87, 88, 89), 48 to listOf(90), 50 to listOf(91), 51 to listOf(92),
            52 to listOf(93), 53 to listOf(94), 54 to listOf(95, 96, 97), 55 to listOf(98),
            56 to listOf(99)
        ),
        DancerKeys.SUMO to mapOf(
            0 to listOf(60), 1 to listOf(61), 2 to listOf(62), 8 to listOf(63),
            9 to listOf(64, 65, 66), 10 to listOf(67, 68, 69), 11 to listOf(70), 12 to listOf(71),
            13 to listOf(72), 15 to listOf(73), 16 to listOf(74), 20 to listOf(75), 23 to listOf(76),
            26 to listOf(77), 27 to listOf(78), 28 to listOf(79), 32 to listOf(80, 81, 82),
            33 to listOf(83, 84, 85), 34 to listOf(86), 35 to listOf(87, 88, 89), 37 to listOf(90),
            46 to listOf(91), 47 to listOf(92), 50 to listOf(93), 52 to listOf(94), 53 to listOf(95),
            54 to listOf(96), 55 to listOf(97), 56 to listOf(98), 59 to listOf(99)
        ),
        DancerKeys.ANCIENT_GIRL to mapOf(
            1 to listOf(60), 2 to listOf(61), 3 to listOf(62), 4 to listOf(63), 5 to listOf(64),
            6 to listOf(65), 8 to listOf(66), 9 to listOf(67), 10 to listOf(68), 11 to listOf(69),
            12 to listOf(70, 71, 72), 13 to listOf(73), 14 to listOf(74), 15 to listOf(75, 76, 77),
            16 to listOf(78), 18 to listOf(79), 19 to listOf(80), 20 to listOf(81), 21 to listOf(82),
            38 to listOf(83), 39 to listOf(84), 40 to listOf(85), 41 to listOf(86), 51 to listOf(87),
            52 to listOf(88), 55 to listOf(89, 90, 91), 56 to listOf(92, 93, 94), 57 to listOf(95),
            58 to listOf(96, 97, 98), 59 to listOf(99)
        ),
        DancerKeys.BABY to mapOf(
            5 to listOf(60), 11 to listOf(61), 12 to listOf(62), 14 to listOf(63), 15 to listOf(64),
            16 to listOf(65), 17 to listOf(66), 18 to listOf(67, 68, 69), 19 to listOf(70),
            20 to listOf(71), 21 to listOf(72), 22 to listOf(73), 23 to listOf(74), 24 to listOf(75),
            29 to listOf(76), 30 to listOf(77), 31 to listOf(78), 32 to listOf(79), 38 to listOf(80),
            41 to listOf(81, 82, 83), 42 to listOf(84, 85, 86), 43 to listOf(87), 44 to listOf(88),
            45 to listOf(89, 90, 91), 46 to listOf(92), 47 to listOf(93), 48 to listOf(94, 95, 96),
            49 to listOf(97), 50 to listOf(98), 59 to listOf(99)
        ),
    )

    private val humans = listOf(
        DancerKeys.MODERN, DancerKeys.ANCIENT, DancerKeys.SUMO,
        DancerKeys.ANCIENT_GIRL, DancerKeys.BABY,
    )

    @Test
    fun humanToursHaveAHundredStepsRobotKeepsSixty() {
        for (key in humans) {
            val order = DancerPoseOrder.orderFor(key)
            assertEquals("size for $key", 100, order.size)
            assertEquals("permutation for $key", (0 until 100).toList(), order.sorted())
            assertEquals("head for $key", 0, order.first())
        }
        assertEquals(60, DancerPoseOrder.orderFor(DancerKeys.ROBOT).size)
    }

    @Test
    fun originalSixtyKeepTheirExactRelativeOrder() {
        for (key in humans) {
            val originals = DancerPoseOrder.orderFor(key).filter { it < 60 }
            assertEquals("originals for $key", oldOrders[key], originals)
        }
    }

    @Test
    fun robotTourIsUnchangedFromV1032() {
        assertEquals(oldOrders[DancerKeys.ROBOT], DancerPoseOrder.orderFor(DancerKeys.ROBOT))
    }

    @Test
    fun spliceMatchesTheManifestRecordExactly() {
        for (key in humans) {
            val old = oldOrders.getValue(key)
            val ins = insertions.getValue(key)
            val expected = mutableListOf<Int>()
            for ((pos, frame) in old.withIndex()) {
                expected.add(frame)
                expected.addAll(ins[pos].orEmpty())
            }
            assertEquals("spliced tour for $key", expected, DancerPoseOrder.orderFor(key))
        }
    }

    @Test
    fun everyInbetweenIndexIsUsedExactlyOnce() {
        for (key in humans) {
            val used = insertions.getValue(key).values.flatten()
            assertEquals("in-betweens for $key", (60 until 100).toList(), used.sorted())
            // 30 bridged gaps per dancer: 25 single + 5 double.
            assertEquals("gap count for $key", 30, insertions.getValue(key).size)
            assertEquals(
                "double gaps for $key", 5,
                insertions.getValue(key).values.count { it.size == 3 },
            )
        }
    }

    @Test
    fun hundredStepTourIsVisitedOneFramePerBeat() {
        val order = DancerPoseOrder.orderFor(DancerKeys.MODERN)
        // 120 BPM: beat k starts at k*500 ms and shows order[k].
        val seen = (0 until 100).map { PoseCycle.frameIndex(it * 500L, 120.0, order) }
        assertEquals(order, seen)
        // Beat 100 wraps to the head of the tour.
        assertEquals(order[0], PoseCycle.frameIndex(100 * 500L, 120.0, order))
        assertTrue(PoseCycle.frameIndex(12_345L, 96.0, order) in 0 until 100)
    }
}
