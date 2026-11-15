package com.dannycom888.shieldkaraoke.ui

import androidx.annotation.DrawableRes
import com.dannycom888.shieldkaraoke.R
import com.dannycom888.shieldkaraoke.state.DancerKeys

/** One little dancer: key (shared with phone/prefs), label, artwork
 *  and its dance profile. [resId] is the original single-frame
 *  cutout, still used for the applause celebration row; [frames]
 *  are the forty dance-pose frames (transparent WebP cut from the
 *  approved pose sheets, one canvas per dancer with a shared floor
 *  baseline) that [PoseCycle] steps through while a song plays.
 *  [inviteResId] is the intro "please sing" frame shown before the
 *  first lyric line (see [DancerActivity.Phase.INVITING]). */
data class Dancer(
    val key: String,
    val label: String,
    @DrawableRes val resId: Int,
    @DrawableRes val inviteResId: Int,
    @DrawableRes val frames: List<Int>,
    val profile: DanceMotion.Profile,
)

object Dancers {
    val ALL: List<Dancer> = listOf(
        Dancer(
            DancerKeys.MODERN, "春丽", R.drawable.dancer_modern_woman, R.drawable.dancer_modern_woman_invite,
            listOf(
                R.drawable.dancer_modern_woman_01, R.drawable.dancer_modern_woman_02,
                R.drawable.dancer_modern_woman_03, R.drawable.dancer_modern_woman_04,
                R.drawable.dancer_modern_woman_05, R.drawable.dancer_modern_woman_06,
                R.drawable.dancer_modern_woman_07, R.drawable.dancer_modern_woman_08,
                R.drawable.dancer_modern_woman_09, R.drawable.dancer_modern_woman_10,
                R.drawable.dancer_modern_woman_11, R.drawable.dancer_modern_woman_12,
                R.drawable.dancer_modern_woman_13, R.drawable.dancer_modern_woman_14,
                R.drawable.dancer_modern_woman_15, R.drawable.dancer_modern_woman_16,
                R.drawable.dancer_modern_woman_17, R.drawable.dancer_modern_woman_18,
                R.drawable.dancer_modern_woman_19, R.drawable.dancer_modern_woman_20,
                R.drawable.dancer_modern_woman_21, R.drawable.dancer_modern_woman_22,
                R.drawable.dancer_modern_woman_23, R.drawable.dancer_modern_woman_24,
                R.drawable.dancer_modern_woman_25, R.drawable.dancer_modern_woman_26,
                R.drawable.dancer_modern_woman_27, R.drawable.dancer_modern_woman_28,
                R.drawable.dancer_modern_woman_29, R.drawable.dancer_modern_woman_30,
                R.drawable.dancer_modern_woman_31, R.drawable.dancer_modern_woman_32,
                R.drawable.dancer_modern_woman_33, R.drawable.dancer_modern_woman_34,
                R.drawable.dancer_modern_woman_35, R.drawable.dancer_modern_woman_36,
                R.drawable.dancer_modern_woman_37, R.drawable.dancer_modern_woman_38,
                R.drawable.dancer_modern_woman_39, R.drawable.dancer_modern_woman_40,
            ),
            DanceMotion.Profile.MODERN,
        ),
        Dancer(
            DancerKeys.ANCIENT, "林语嫣", R.drawable.dancer_ancient_woman, R.drawable.dancer_ancient_woman_invite,
            listOf(
                R.drawable.dancer_ancient_woman_01, R.drawable.dancer_ancient_woman_02,
                R.drawable.dancer_ancient_woman_03, R.drawable.dancer_ancient_woman_04,
                R.drawable.dancer_ancient_woman_05, R.drawable.dancer_ancient_woman_06,
                R.drawable.dancer_ancient_woman_07, R.drawable.dancer_ancient_woman_08,
                R.drawable.dancer_ancient_woman_09, R.drawable.dancer_ancient_woman_10,
                R.drawable.dancer_ancient_woman_11, R.drawable.dancer_ancient_woman_12,
                R.drawable.dancer_ancient_woman_13, R.drawable.dancer_ancient_woman_14,
                R.drawable.dancer_ancient_woman_15, R.drawable.dancer_ancient_woman_16,
                R.drawable.dancer_ancient_woman_17, R.drawable.dancer_ancient_woman_18,
                R.drawable.dancer_ancient_woman_19, R.drawable.dancer_ancient_woman_20,
                R.drawable.dancer_ancient_woman_21, R.drawable.dancer_ancient_woman_22,
                R.drawable.dancer_ancient_woman_23, R.drawable.dancer_ancient_woman_24,
                R.drawable.dancer_ancient_woman_25, R.drawable.dancer_ancient_woman_26,
                R.drawable.dancer_ancient_woman_27, R.drawable.dancer_ancient_woman_28,
                R.drawable.dancer_ancient_woman_29, R.drawable.dancer_ancient_woman_30,
                R.drawable.dancer_ancient_woman_31, R.drawable.dancer_ancient_woman_32,
                R.drawable.dancer_ancient_woman_33, R.drawable.dancer_ancient_woman_34,
                R.drawable.dancer_ancient_woman_35, R.drawable.dancer_ancient_woman_36,
                R.drawable.dancer_ancient_woman_37, R.drawable.dancer_ancient_woman_38,
                R.drawable.dancer_ancient_woman_39, R.drawable.dancer_ancient_woman_40,
            ),
            DanceMotion.Profile.ANCIENT,
        ),
        Dancer(
            DancerKeys.SUMO, "姿三四郎", R.drawable.dancer_sumo, R.drawable.dancer_sumo_invite,
            listOf(
                R.drawable.dancer_sumo_01, R.drawable.dancer_sumo_02,
                R.drawable.dancer_sumo_03, R.drawable.dancer_sumo_04,
                R.drawable.dancer_sumo_05, R.drawable.dancer_sumo_06,
                R.drawable.dancer_sumo_07, R.drawable.dancer_sumo_08,
                R.drawable.dancer_sumo_09, R.drawable.dancer_sumo_10,
                R.drawable.dancer_sumo_11, R.drawable.dancer_sumo_12,
                R.drawable.dancer_sumo_13, R.drawable.dancer_sumo_14,
                R.drawable.dancer_sumo_15, R.drawable.dancer_sumo_16,
                R.drawable.dancer_sumo_17, R.drawable.dancer_sumo_18,
                R.drawable.dancer_sumo_19, R.drawable.dancer_sumo_20,
                R.drawable.dancer_sumo_21, R.drawable.dancer_sumo_22,
                R.drawable.dancer_sumo_23, R.drawable.dancer_sumo_24,
                R.drawable.dancer_sumo_25, R.drawable.dancer_sumo_26,
                R.drawable.dancer_sumo_27, R.drawable.dancer_sumo_28,
                R.drawable.dancer_sumo_29, R.drawable.dancer_sumo_30,
                R.drawable.dancer_sumo_31, R.drawable.dancer_sumo_32,
                R.drawable.dancer_sumo_33, R.drawable.dancer_sumo_34,
                R.drawable.dancer_sumo_35, R.drawable.dancer_sumo_36,
                R.drawable.dancer_sumo_37, R.drawable.dancer_sumo_38,
                R.drawable.dancer_sumo_39, R.drawable.dancer_sumo_40,
            ),
            DanceMotion.Profile.SUMO,
        ),
        Dancer(
            DancerKeys.ANCIENT_GIRL, "小蛮", R.drawable.dancer_ancient_girl, R.drawable.dancer_ancient_girl_invite,
            listOf(
                R.drawable.dancer_ancient_girl_01, R.drawable.dancer_ancient_girl_02,
                R.drawable.dancer_ancient_girl_03, R.drawable.dancer_ancient_girl_04,
                R.drawable.dancer_ancient_girl_05, R.drawable.dancer_ancient_girl_06,
                R.drawable.dancer_ancient_girl_07, R.drawable.dancer_ancient_girl_08,
                R.drawable.dancer_ancient_girl_09, R.drawable.dancer_ancient_girl_10,
                R.drawable.dancer_ancient_girl_11, R.drawable.dancer_ancient_girl_12,
                R.drawable.dancer_ancient_girl_13, R.drawable.dancer_ancient_girl_14,
                R.drawable.dancer_ancient_girl_15, R.drawable.dancer_ancient_girl_16,
                R.drawable.dancer_ancient_girl_17, R.drawable.dancer_ancient_girl_18,
                R.drawable.dancer_ancient_girl_19, R.drawable.dancer_ancient_girl_20,
                R.drawable.dancer_ancient_girl_21, R.drawable.dancer_ancient_girl_22,
                R.drawable.dancer_ancient_girl_23, R.drawable.dancer_ancient_girl_24,
                R.drawable.dancer_ancient_girl_25, R.drawable.dancer_ancient_girl_26,
                R.drawable.dancer_ancient_girl_27, R.drawable.dancer_ancient_girl_28,
                R.drawable.dancer_ancient_girl_29, R.drawable.dancer_ancient_girl_30,
                R.drawable.dancer_ancient_girl_31, R.drawable.dancer_ancient_girl_32,
                R.drawable.dancer_ancient_girl_33, R.drawable.dancer_ancient_girl_34,
                R.drawable.dancer_ancient_girl_35, R.drawable.dancer_ancient_girl_36,
                R.drawable.dancer_ancient_girl_37, R.drawable.dancer_ancient_girl_38,
                R.drawable.dancer_ancient_girl_39, R.drawable.dancer_ancient_girl_40,
            ),
            DanceMotion.Profile.ANCIENT,
        ),
        Dancer(
            DancerKeys.BABY, "哪吒", R.drawable.dancer_baby, R.drawable.dancer_baby_invite,
            listOf(
                R.drawable.dancer_baby_01, R.drawable.dancer_baby_02,
                R.drawable.dancer_baby_03, R.drawable.dancer_baby_04,
                R.drawable.dancer_baby_05, R.drawable.dancer_baby_06,
                R.drawable.dancer_baby_07, R.drawable.dancer_baby_08,
                R.drawable.dancer_baby_09, R.drawable.dancer_baby_10,
                R.drawable.dancer_baby_11, R.drawable.dancer_baby_12,
                R.drawable.dancer_baby_13, R.drawable.dancer_baby_14,
                R.drawable.dancer_baby_15, R.drawable.dancer_baby_16,
                R.drawable.dancer_baby_17, R.drawable.dancer_baby_18,
                R.drawable.dancer_baby_19, R.drawable.dancer_baby_20,
                R.drawable.dancer_baby_21, R.drawable.dancer_baby_22,
                R.drawable.dancer_baby_23, R.drawable.dancer_baby_24,
                R.drawable.dancer_baby_25, R.drawable.dancer_baby_26,
                R.drawable.dancer_baby_27, R.drawable.dancer_baby_28,
                R.drawable.dancer_baby_29, R.drawable.dancer_baby_30,
                R.drawable.dancer_baby_31, R.drawable.dancer_baby_32,
                R.drawable.dancer_baby_33, R.drawable.dancer_baby_34,
                R.drawable.dancer_baby_35, R.drawable.dancer_baby_36,
                R.drawable.dancer_baby_37, R.drawable.dancer_baby_38,
                R.drawable.dancer_baby_39, R.drawable.dancer_baby_40,
            ),
            DanceMotion.Profile.BABY,
        ),
        Dancer(
            DancerKeys.ROBOT, "瓦力", R.drawable.dancer_robot, R.drawable.dancer_robot_invite,
            listOf(
                R.drawable.dancer_robot_01, R.drawable.dancer_robot_02,
                R.drawable.dancer_robot_03, R.drawable.dancer_robot_04,
                R.drawable.dancer_robot_05, R.drawable.dancer_robot_06,
                R.drawable.dancer_robot_07, R.drawable.dancer_robot_08,
                R.drawable.dancer_robot_09, R.drawable.dancer_robot_10,
                R.drawable.dancer_robot_11, R.drawable.dancer_robot_12,
                R.drawable.dancer_robot_13, R.drawable.dancer_robot_14,
                R.drawable.dancer_robot_15, R.drawable.dancer_robot_16,
                R.drawable.dancer_robot_17, R.drawable.dancer_robot_18,
                R.drawable.dancer_robot_19, R.drawable.dancer_robot_20,
                R.drawable.dancer_robot_21, R.drawable.dancer_robot_22,
                R.drawable.dancer_robot_23, R.drawable.dancer_robot_24,
                R.drawable.dancer_robot_25, R.drawable.dancer_robot_26,
                R.drawable.dancer_robot_27, R.drawable.dancer_robot_28,
                R.drawable.dancer_robot_29, R.drawable.dancer_robot_30,
                R.drawable.dancer_robot_31, R.drawable.dancer_robot_32,
                R.drawable.dancer_robot_33, R.drawable.dancer_robot_34,
                R.drawable.dancer_robot_35, R.drawable.dancer_robot_36,
                R.drawable.dancer_robot_37, R.drawable.dancer_robot_38,
                R.drawable.dancer_robot_39, R.drawable.dancer_robot_40,
            ),
            DanceMotion.Profile.ROBOT,
        ),
    )

    /** Unknown / unset keys fall back to the default (Chun-Li). */
    fun byKey(key: String?): Dancer = ALL.firstOrNull { it.key == key } ?: ALL[0]

    fun indexOf(dancer: Dancer): Int = ALL.indexOf(dancer).coerceAtLeast(0)
}
