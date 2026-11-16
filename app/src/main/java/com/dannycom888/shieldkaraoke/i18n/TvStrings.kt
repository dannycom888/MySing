package com.dannycom888.shieldkaraoke.i18n

import java.util.Locale

/** Every user-facing string key of the TV interface. */
enum class TvKey {
    // Home
    SCANNING, PICK_FOLDER, LIBRARY_COUNT, SERVER_DOWN, SCAN_QR, QR_DESC,
    ENTRY_SINGERS, ENTRY_SONGS, ENTRY_CHINESE, ENTRY_ENGLISH,
    ENTRY_FAVORITES, ENTRY_RECENTS, PICK_FOLDER_BTN, RESCAN,
    CREDIT,
    // Browse
    TAB_SONGS, TAB_SINGERS, FILTER_ALL, FILTER_CHINESE, FILTER_ENGLISH,
    FILTER_ANY_GENDER, FILTER_MALE, FILTER_FEMALE, SINGER_CHIP,
    // Player
    NEXT_UP, INTERLUDE_COUNTDOWN, NOW_SINGING_LINE,
    PAUSE, RESUME, REPLAY, ACCOMP_ON, ACCOMP_OFF,
    PITCH_DOWN, PITCH_LABEL, PITCH_UP, DANCERS_ON, DANCERS_OFF,
    QUIT, GO_ORDER, QUIT_TITLE, QUIT_TEXT, QUIT_CONFIRM, QUIT_CANCEL,
    PLAY_ERROR, CORNER_QR_CAPTION, CORNER_QR_DESC,
    // Queue panel on the player screen (v1.0.19)
    QUEUE_PANEL_TITLE, QUEUE_PANEL_NEXT, QUEUE_PANEL_DELETE,
    QUEUE_PANEL_NO_WAITING,
    // The panel's third action (v1.0.27): cut straight to this song.
    QUEUE_PANEL_PLAY_NOW,
    // The current-row label for a filler song (v1.0.20)
    QUEUE_PANEL_RANDOM,
    // The orderer name shown for songs ordered from the TV itself.
    TV_ORDERER,
    // TV admin page: rename the dancers (v1.0.21)
    ADMIN_TITLE, ADMIN_HINT, ADMIN_RENAME_TITLE, ADMIN_NAME_HINT,
    ADMIN_DEFAULT, ADMIN_RESTORE, ADMIN_RESTORE_TITLE,
    ADMIN_RESTORE_TEXT, ADMIN_RESTORE_CONFIRM, ADMIN_BACK,
    SAVE, CANCEL,
    // Settings move into the admin page (v1.0.26): the library
    // section, the sweep-colour section, the home screen's
    // no-folder guidance chip and the gear button's description.
    ADMIN_LIBRARY_TITLE, ADMIN_LIBRARY_CURRENT, ADMIN_LIBRARY_NONE,
    ADMIN_SWEEP_TITLE, SWEEP_GOLD, SWEEP_GREEN, SWEEP_PINK,
    ADMIN_GEAR_DESC, ADMIN_VERSION,
    // The diagnostics section on the admin page (v1.0.35): the
    // line moved off the player stage gets a section title here.
    ADMIN_DIAG_TITLE,
}

/**
 * The TV interface's zh/en string table (v1.0.13). One table, keyed
 * by [TvKey] — the UI never branches on language inline. Templates
 * use Formatter placeholders (%s / %d / %1$s …); [get] fills them.
 * Song and singer names are file-name data and are never in here:
 * they pass through the %s slots untranslated, by design.
 *
 * The diagnostics line itself (on the admin page since v1.0.35)
 * is deliberately NOT part of this table — it is technical small
 * print and stays as-is; only its section title is tabled.
 */
object TvStrings {

    private val ZH: Map<TvKey, String> = mapOf(
        TvKey.SCANNING to "正在扫描歌库…",
        TvKey.PICK_FOLDER to "先选歌库文件夹（里面有四个分类文件夹）",
        TvKey.LIBRARY_COUNT to "歌库共 %d 首",
        TvKey.SERVER_DOWN to "⚠ 点歌服务未启动，手机暂时扫不了码",
        TvKey.SCAN_QR to "手机扫码点歌",
        TvKey.QR_DESC to "手机点歌二维码",
        TvKey.ENTRY_SINGERS to "🎤 按歌手",
        TvKey.ENTRY_SONGS to "🎵 按歌名",
        TvKey.ENTRY_CHINESE to "🇨🇳 中文",
        TvKey.ENTRY_ENGLISH to "🇬🇧 英文",
        TvKey.ENTRY_FAVORITES to "❤ 收藏",
        TvKey.ENTRY_RECENTS to "🕘 最近唱过",
        TvKey.PICK_FOLDER_BTN to "📁 选择歌库文件夹",
        TvKey.RESCAN to "🔄 重新扫描",
        TvKey.CREDIT to "本软件由 Muse 协助制作",
        TvKey.TAB_SONGS to "歌曲",
        TvKey.TAB_SINGERS to "歌手",
        TvKey.FILTER_ALL to "全部",
        TvKey.FILTER_CHINESE to "中文",
        TvKey.FILTER_ENGLISH to "英文",
        TvKey.FILTER_ANY_GENDER to "不限男女",
        TvKey.FILTER_MALE to "男歌手",
        TvKey.FILTER_FEMALE to "女歌手",
        TvKey.SINGER_CHIP to "歌手：%s ✕",
        TvKey.NEXT_UP to "下一首：%2\$s点的 %1\$s",
        TvKey.INTERLUDE_COUNTDOWN to "%d 秒后开始",
        TvKey.NOW_SINGING_LINE to "正在唱：%1\$s（%2\$s点的）",
        TvKey.PAUSE to "⏸ 暂停",
        TvKey.RESUME to "▶ 继续",
        TvKey.REPLAY to "↺ 重唱",
        TvKey.ACCOMP_ON to "🎙 伴奏中",
        TvKey.ACCOMP_OFF to "🎤 原唱",
        TvKey.PITCH_DOWN to "降调 −",
        TvKey.PITCH_LABEL to "调 %d",
        TvKey.PITCH_UP to "升调 ＋",
        TvKey.DANCERS_ON to "💃 伴舞开",
        TvKey.DANCERS_OFF to "💃 伴舞关",
        TvKey.QUIT to "🚪 不唱了",
        TvKey.GO_ORDER to "🎵 去点歌",
        TvKey.QUIT_TITLE to "不唱了？",
        TvKey.QUIT_TEXT to "结束当前这首，接着唱下一首。",
        TvKey.QUIT_CONFIRM to "不唱了",
        TvKey.QUIT_CANCEL to "继续唱",
        TvKey.PLAY_ERROR to "⚠ 播放错误：%s",
        TvKey.CORNER_QR_CAPTION to "扫码点歌",
        TvKey.CORNER_QR_DESC to "扫码点歌二维码",
        TvKey.QUEUE_PANEL_TITLE to "已点歌单",
        TvKey.QUEUE_PANEL_NEXT to "下一首唱",
        TvKey.QUEUE_PANEL_PLAY_NOW to "现在唱",
        TvKey.QUEUE_PANEL_DELETE to "删除",
        TvKey.QUEUE_PANEL_NO_WAITING to "暂无排队",
        TvKey.QUEUE_PANEL_RANDOM to "随机",
        TvKey.TV_ORDERER to "电视",
        TvKey.ADMIN_TITLE to "⚙ 后台",
        TvKey.ADMIN_HINT to "选一位伴舞，按 OK 给他改名字",
        TvKey.ADMIN_RENAME_TITLE to "给 %s 改名",
        TvKey.ADMIN_NAME_HINT to "输入新名字",
        TvKey.ADMIN_DEFAULT to "默认：%s",
        TvKey.ADMIN_RESTORE to "恢复默认名",
        TvKey.ADMIN_RESTORE_TITLE to "恢复默认名？",
        TvKey.ADMIN_RESTORE_TEXT to "六位伴舞的名字都改回默认。",
        TvKey.ADMIN_RESTORE_CONFIRM to "恢复",
        TvKey.ADMIN_BACK to "返回",
        TvKey.SAVE to "保存",
        TvKey.CANCEL to "取消",
        TvKey.ADMIN_LIBRARY_TITLE to "歌库",
        TvKey.ADMIN_LIBRARY_CURRENT to "当前歌库：%s",
        TvKey.ADMIN_LIBRARY_NONE to "尚未选择歌库文件夹",
        TvKey.ADMIN_SWEEP_TITLE to "扫光颜色",
        TvKey.SWEEP_GOLD to "金色",
        TvKey.SWEEP_GREEN to "浅绿",
        TvKey.SWEEP_PINK to "浅粉",
        TvKey.ADMIN_GEAR_DESC to "后台设置",
        TvKey.ADMIN_VERSION to "版本 v%s",
        TvKey.ADMIN_DIAG_TITLE to "诊断",
    )

    private val EN: Map<TvKey, String> = mapOf(
        TvKey.SCANNING to "Scanning library…",
        TvKey.PICK_FOLDER to "Pick the library folder first (the one with the four category folders inside)",
        TvKey.LIBRARY_COUNT to "%d songs in the library",
        TvKey.SERVER_DOWN to "⚠ Ordering service is down — phones can't scan to order right now",
        TvKey.SCAN_QR to "Scan with your phone to order",
        TvKey.QR_DESC to "QR code for phone ordering",
        TvKey.ENTRY_SINGERS to "🎤 By singer",
        TvKey.ENTRY_SONGS to "🎵 By title",
        TvKey.ENTRY_CHINESE to "🇨🇳 Chinese",
        TvKey.ENTRY_ENGLISH to "🇬🇧 English",
        TvKey.ENTRY_FAVORITES to "❤ Favorites",
        TvKey.ENTRY_RECENTS to "🕘 Recently sung",
        TvKey.PICK_FOLDER_BTN to "📁 Choose library folder",
        TvKey.RESCAN to "🔄 Rescan",
        TvKey.CREDIT to "Built with assistance from Claude",
        TvKey.TAB_SONGS to "Songs",
        TvKey.TAB_SINGERS to "Singers",
        TvKey.FILTER_ALL to "All",
        TvKey.FILTER_CHINESE to "Chinese",
        TvKey.FILTER_ENGLISH to "English",
        TvKey.FILTER_ANY_GENDER to "Any singer",
        TvKey.FILTER_MALE to "Male singers",
        TvKey.FILTER_FEMALE to "Female singers",
        TvKey.SINGER_CHIP to "Singer: %s ✕",
        TvKey.NEXT_UP to "Next up: %1\$s (ordered by %2\$s)",
        TvKey.INTERLUDE_COUNTDOWN to "Starting in %d s",
        TvKey.NOW_SINGING_LINE to "Now singing: %1\$s (ordered by %2\$s)",
        TvKey.PAUSE to "⏸ Pause",
        TvKey.RESUME to "▶ Resume",
        TvKey.REPLAY to "↺ Replay",
        TvKey.ACCOMP_ON to "🎙 Backing on",
        TvKey.ACCOMP_OFF to "🎤 Original",
        TvKey.PITCH_DOWN to "Key −",
        TvKey.PITCH_LABEL to "Key %d",
        TvKey.PITCH_UP to "Key ＋",
        TvKey.DANCERS_ON to "💃 Dancers on",
        TvKey.DANCERS_OFF to "💃 Dancers off",
        TvKey.QUIT to "🚪 End song",
        TvKey.GO_ORDER to "🎵 Order songs",
        TvKey.QUIT_TITLE to "End this song?",
        TvKey.QUIT_TEXT to "End the current song and play the next one.",
        TvKey.QUIT_CONFIRM to "End song",
        TvKey.QUIT_CANCEL to "Keep singing",
        TvKey.PLAY_ERROR to "⚠ Playback error: %s",
        TvKey.CORNER_QR_CAPTION to "Scan to order",
        TvKey.CORNER_QR_DESC to "Scan-to-order QR code",
        TvKey.QUEUE_PANEL_TITLE to "Queue",
        TvKey.QUEUE_PANEL_NEXT to "Play next",
        TvKey.QUEUE_PANEL_PLAY_NOW to "Play now",
        TvKey.QUEUE_PANEL_DELETE to "Delete",
        TvKey.QUEUE_PANEL_NO_WAITING to "Nothing queued",
        TvKey.QUEUE_PANEL_RANDOM to "Random",
        TvKey.TV_ORDERER to "TV",
        TvKey.ADMIN_TITLE to "⚙ Admin",
        TvKey.ADMIN_HINT to "Pick a dancer and press OK to rename them",
        TvKey.ADMIN_RENAME_TITLE to "Rename %s",
        TvKey.ADMIN_NAME_HINT to "New name",
        TvKey.ADMIN_DEFAULT to "Default: %s",
        TvKey.ADMIN_RESTORE to "Restore default names",
        TvKey.ADMIN_RESTORE_TITLE to "Restore default names?",
        TvKey.ADMIN_RESTORE_TEXT to "All six dancers go back to their default names.",
        TvKey.ADMIN_RESTORE_CONFIRM to "Restore",
        TvKey.ADMIN_BACK to "Back",
        TvKey.SAVE to "Save",
        TvKey.CANCEL to "Cancel",
        TvKey.ADMIN_LIBRARY_TITLE to "Library",
        TvKey.ADMIN_LIBRARY_CURRENT to "Library folder: %s",
        TvKey.ADMIN_LIBRARY_NONE to "No library folder chosen yet",
        TvKey.ADMIN_SWEEP_TITLE to "Sweep color",
        TvKey.SWEEP_GOLD to "Gold",
        TvKey.SWEEP_GREEN to "Light green",
        TvKey.SWEEP_PINK to "Light pink",
        TvKey.ADMIN_GEAR_DESC to "Settings",
        TvKey.ADMIN_VERSION to "Version %s",
        TvKey.ADMIN_DIAG_TITLE to "Diagnostics",
    )

    /** Key set of each language's table, for the parity test. */
    fun keys(lang: UiLang): Set<TvKey> = table(lang).keys

    fun get(lang: UiLang, key: TvKey, vararg args: Any): String {
        val template = table(lang)[key] ?: ZH.getValue(key)
        return if (args.isEmpty()) template else String.format(Locale.US, template, *args)
    }

    private fun table(lang: UiLang): Map<TvKey, String> = if (lang == UiLang.EN) EN else ZH
}
