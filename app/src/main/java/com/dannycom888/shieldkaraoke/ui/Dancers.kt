package com.dannycom888.shieldkaraoke.ui

import androidx.annotation.DrawableRes
import com.dannycom888.shieldkaraoke.R
import com.dannycom888.shieldkaraoke.state.DancerKeys

/** One little dancer: key (shared with phone/prefs), label, artwork
 *  and its dance profile. [resId] is the original single-frame
 *  cutout, still used for the applause celebration row; [frames]
 *  are the ten dance-pose frames (transparent WebP cut from the
 *  approved pose sheets, one canvas per dancer with a shared floor
 *  baseline) that [PoseCycle] steps through while a song plays. */
data class Dancer(
    val key: String,
    val label: String,
    @DrawableRes val resId: Int,
    @DrawableRes val frames: List<Int>,
    val profile: DanceMotion.Profile,
)

object Dancers {
    val ALL: List<Dancer> = listOf(
        Dancer(
            DancerKeys.MODERN, "现代女", R.drawable.dancer_modern_woman,
            listOf(
                R.drawable.dancer_modern_woman_01, R.drawable.dancer_modern_woman_02,
                R.drawable.dancer_modern_woman_03, R.drawable.dancer_modern_woman_04,
                R.drawable.dancer_modern_woman_05, R.drawable.dancer_modern_woman_06,
                R.drawable.dancer_modern_woman_07, R.drawable.dancer_modern_woman_08,
                R.drawable.dancer_modern_woman_09, R.drawable.dancer_modern_woman_10,
            ),
            DanceMotion.Profile.MODERN,
        ),
        Dancer(
            DancerKeys.ANCIENT, "古代女", R.drawable.dancer_ancient_woman,
            listOf(
                R.drawable.dancer_ancient_woman_01, R.drawable.dancer_ancient_woman_02,
                R.drawable.dancer_ancient_woman_03, R.drawable.dancer_ancient_woman_04,
                R.drawable.dancer_ancient_woman_05, R.drawable.dancer_ancient_woman_06,
                R.drawable.dancer_ancient_woman_07, R.drawable.dancer_ancient_woman_08,
                R.drawable.dancer_ancient_woman_09, R.drawable.dancer_ancient_woman_10,
            ),
            DanceMotion.Profile.ANCIENT,
        ),
        Dancer(
            DancerKeys.GIRL, "现代女孩", R.drawable.dancer_modern_girl,
            listOf(
                R.drawable.dancer_modern_girl_01, R.drawable.dancer_modern_girl_02,
                R.drawable.dancer_modern_girl_03, R.drawable.dancer_modern_girl_04,
                R.drawable.dancer_modern_girl_05, R.drawable.dancer_modern_girl_06,
                R.drawable.dancer_modern_girl_07, R.drawable.dancer_modern_girl_08,
                R.drawable.dancer_modern_girl_09, R.drawable.dancer_modern_girl_10,
            ),
            DanceMotion.Profile.MODERN,
        ),
        Dancer(
            DancerKeys.ANCIENT_GIRL, "古代女童", R.drawable.dancer_ancient_girl,
            listOf(
                R.drawable.dancer_ancient_girl_01, R.drawable.dancer_ancient_girl_02,
                R.drawable.dancer_ancient_girl_03, R.drawable.dancer_ancient_girl_04,
                R.drawable.dancer_ancient_girl_05, R.drawable.dancer_ancient_girl_06,
                R.drawable.dancer_ancient_girl_07, R.drawable.dancer_ancient_girl_08,
                R.drawable.dancer_ancient_girl_09, R.drawable.dancer_ancient_girl_10,
            ),
            DanceMotion.Profile.ANCIENT,
        ),
        Dancer(
            DancerKeys.BABY, "小婴儿", R.drawable.dancer_baby,
            listOf(
                R.drawable.dancer_baby_01, R.drawable.dancer_baby_02,
                R.drawable.dancer_baby_03, R.drawable.dancer_baby_04,
                R.drawable.dancer_baby_05, R.drawable.dancer_baby_06,
                R.drawable.dancer_baby_07, R.drawable.dancer_baby_08,
                R.drawable.dancer_baby_09, R.drawable.dancer_baby_10,
            ),
            DanceMotion.Profile.BABY,
        ),
        Dancer(
            DancerKeys.ROBOT, "机器人", R.drawable.dancer_robot,
            listOf(
                R.drawable.dancer_robot_01, R.drawable.dancer_robot_02,
                R.drawable.dancer_robot_03, R.drawable.dancer_robot_04,
                R.drawable.dancer_robot_05, R.drawable.dancer_robot_06,
                R.drawable.dancer_robot_07, R.drawable.dancer_robot_08,
                R.drawable.dancer_robot_09, R.drawable.dancer_robot_10,
            ),
            DanceMotion.Profile.ROBOT,
        ),
    )

    /** Unknown / unset keys fall back to the default (modern woman). */
    fun byKey(key: String?): Dancer = ALL.firstOrNull { it.key == key } ?: ALL[0]

    fun indexOf(dancer: Dancer): Int = ALL.indexOf(dancer).coerceAtLeast(0)
}
