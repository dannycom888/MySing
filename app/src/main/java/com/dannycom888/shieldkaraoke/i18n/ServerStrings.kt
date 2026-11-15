package com.dannycom888.shieldkaraoke.i18n

import java.util.Locale

/** Every message the phone-ordering server sends back to a phone. */
enum class ServerKey {
    ORDER_NAME_REQUIRED,
    ORDER_NOT_FOUND,
    ORDER_START_NOW,
    ORDER_QUEUED,
    DANCER_INVALID,
}

/**
 * zh/en table for the HTTP server's replies (v1.0.13). The phone
 * page sends its own UI language with each order/dancer request;
 * the server answers in that language. No language in the request
 * (a page from before this release) means Chinese, as before.
 */
object ServerStrings {

    private val ZH: Map<ServerKey, String> = mapOf(
        ServerKey.ORDER_NAME_REQUIRED to "请先输入你的名字",
        ServerKey.ORDER_NOT_FOUND to "找不到这首歌",
        ServerKey.ORDER_START_NOW to "马上开唱！",
        ServerKey.ORDER_QUEUED to "已加入，前面还有 %d 首",
        ServerKey.DANCER_INVALID to "名字或伴舞选择不对",
    )

    private val EN: Map<ServerKey, String> = mapOf(
        ServerKey.ORDER_NAME_REQUIRED to "Please enter your name first",
        ServerKey.ORDER_NOT_FOUND to "Song not found",
        ServerKey.ORDER_START_NOW to "You're up — singing now!",
        ServerKey.ORDER_QUEUED to "Added — %d song(s) ahead of you",
        ServerKey.DANCER_INVALID to "Invalid name or dancer choice",
    )

    /** Key set of each language's table, for the parity test. */
    fun keys(lang: UiLang): Set<ServerKey> = table(lang).keys

    fun get(lang: UiLang, key: ServerKey, vararg args: Any): String {
        val template = table(lang)[key] ?: ZH.getValue(key)
        return if (args.isEmpty()) template else String.format(Locale.US, template, *args)
    }

    private fun table(lang: UiLang): Map<ServerKey, String> = if (lang == UiLang.EN) EN else ZH
}
