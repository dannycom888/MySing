package com.dannycom888.shieldkaraoke.ui

import androidx.annotation.DrawableRes
import com.dannycom888.shieldkaraoke.R
import com.dannycom888.shieldkaraoke.state.DancerKeys

/** One little dancer: key (shared with phone/prefs), label, artwork
 *  (transparent PNG cut from the approved group designs, full-canvas
 *  height so relative sizes and the shared floor line survive), and
 *  its dance profile. */
data class Dancer(
    val key: String,
    val label: String,
    @DrawableRes val resId: Int,
    val profile: DanceMotion.Profile,
)

object Dancers {
    val ALL: List<Dancer> = listOf(
        Dancer(DancerKeys.MODERN, "现代女", R.drawable.dancer_modern_woman, DanceMotion.Profile.MODERN),
        Dancer(DancerKeys.ANCIENT, "古代女", R.drawable.dancer_ancient_woman, DanceMotion.Profile.ANCIENT),
        Dancer(DancerKeys.GIRL, "现代女孩", R.drawable.dancer_modern_girl, DanceMotion.Profile.MODERN),
        Dancer(DancerKeys.ANCIENT_GIRL, "古代女童", R.drawable.dancer_ancient_girl, DanceMotion.Profile.ANCIENT),
        Dancer(DancerKeys.BABY, "小婴儿", R.drawable.dancer_baby, DanceMotion.Profile.BABY),
        Dancer(DancerKeys.ROBOT, "机器人", R.drawable.dancer_robot, DanceMotion.Profile.ROBOT),
    )

    /** Unknown / unset keys fall back to the default (modern woman). */
    fun byKey(key: String?): Dancer = ALL.firstOrNull { it.key == key } ?: ALL[0]

    fun indexOf(dancer: Dancer): Int = ALL.indexOf(dancer).coerceAtLeast(0)
}
