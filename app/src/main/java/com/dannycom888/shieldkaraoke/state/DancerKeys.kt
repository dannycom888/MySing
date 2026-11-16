package com.dannycom888.shieldkaraoke.state

/**
 * The six little dancers, identified by stable string keys shared by
 * the phone page, the HTTP server, [Prefs] and the TV UI (which maps
 * keys to drawables). The dancer is chosen per *orderer*: whoever
 * ordered the song picks the dancer that comes out for their song.
 *
 * v1.0.12: the modern girl was replaced by the sumo wrestler
 * （姿三四郎） and every dancer got a stage name. Keys stay stable —
 * only the sumo key is new; a stored "girl" selection (TV prefs or
 * a phone's localStorage) is simply no longer valid and falls back
 * to the default everywhere, exactly like any unknown key.
 */
object DancerKeys {
    const val MODERN = "modern"
    const val ANCIENT = "ancient"
    const val SUMO = "sumo"
    const val ANCIENT_GIRL = "ancient_girl"
    const val BABY = "baby"
    const val ROBOT = "robot"

    /** Selection cycle order on the phone page. */
    val ALL = listOf(MODERN, ANCIENT, SUMO, ANCIENT_GIRL, BABY, ROBOT)

    val LABELS = mapOf(
        MODERN to "Amy",
        ANCIENT to "秀颖",
        SUMO to "姿三四郎",
        ANCIENT_GIRL to "汉娜",
        BABY to "哪吒",
        ROBOT to "瓦力",
    )

    /**
     * English stage names (v1.0.13), the shared mapping behind the
     * phone page's English picker cards: William can't read the
     * Chinese names, so in English mode the cards read Amy /
     * Xiuying / Sanshiro / Hannah / Nezha / WALL-E. The phone
     * page is static HTML and carries the same six names inline;
     * tests pin both sides to this table.
     */
    val LABELS_EN = mapOf(
        MODERN to "Amy",
        ANCIENT to "Xiuying",
        SUMO to "Sanshiro",
        ANCIENT_GIRL to "Hannah",
        BABY to "Nezha",
        ROBOT to "WALL-E",
    )

    /** TV-ordered songs and unknown orderers get Amy （Amy）. */
    const val DEFAULT = MODERN

    fun isValid(key: String): Boolean = key in ALL

    fun label(key: String?): String = LABELS[key] ?: LABELS.getValue(DEFAULT)

    fun labelEn(key: String?): String = LABELS_EN[key] ?: LABELS_EN.getValue(DEFAULT)
}
