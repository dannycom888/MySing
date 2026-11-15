package com.dannycom888.shieldkaraoke.state

/**
 * The six little dancers, identified by stable string keys shared by
 * the phone page, the HTTP server, [Prefs] and the TV UI (which maps
 * keys to drawables). The dancer is chosen per *orderer*: whoever
 * ordered the song picks the dancer that comes out for their song.
 */
object DancerKeys {
    const val MODERN = "modern"
    const val ANCIENT = "ancient"
    const val GIRL = "girl"
    const val ANCIENT_GIRL = "ancient_girl"
    const val BABY = "baby"
    const val ROBOT = "robot"

    /** Selection cycle order on the phone page. */
    val ALL = listOf(MODERN, ANCIENT, GIRL, ANCIENT_GIRL, BABY, ROBOT)

    val LABELS = mapOf(
        MODERN to "现代女",
        ANCIENT to "古代女",
        GIRL to "现代女孩",
        ANCIENT_GIRL to "古代女童",
        BABY to "小婴儿",
        ROBOT to "机器人",
    )

    /** TV-ordered songs and unknown orderers get the modern woman. */
    const val DEFAULT = MODERN

    fun isValid(key: String): Boolean = key in ALL

    fun label(key: String?): String = LABELS[key] ?: LABELS.getValue(DEFAULT)
}
