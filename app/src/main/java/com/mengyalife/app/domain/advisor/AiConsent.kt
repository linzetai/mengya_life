package com.mengyalife.app.domain.advisor

/** 个人成长数据类别。首次授权默认四类全开，之后可在设置中单独关闭。 */
enum class ConsentCategory(val label: String, val description: String) {
    GOALS("目标", "长期目标、阶段目标、短期行动"),
    PROGRESS("进展", "目标进展记录与数值"),
    JOURNAL("日记", "日记标题与正文摘录"),
    CONVERSATION("历史对话", "过去的顾问消息（按授权 epoch 隔离）"),
}

/**
 * 持续授权状态。
 *
 * - [textVersion]：授权文案版本；文案变更后必须重新授权；
 * - [categoriesVersion]：类别集合版本；新增敏感类别后必须重新授权；
 * - [recipient]：数据接收服务（规范化后的 endpoint）；
 * - [epoch]：每次授权/类别变更/撤回/更换接收方都 +1；旧 epoch 的消息不会进入新请求。
 */
data class AiConsentState(
    val granted: Boolean = false,
    val textVersion: Int = 0,
    val categoriesVersion: Int = 0,
    val grantedAt: Long? = null,
    val recipient: String = "",
    val categories: Set<ConsentCategory> = emptySet(),
    val epoch: Long = 0L,
) {
    fun enabled(category: ConsentCategory): Boolean = granted && category in categories

    fun isValidFor(endpoint: String): Boolean =
        granted &&
            textVersion == AiConsentRules.TEXT_VERSION &&
            categoriesVersion == AiConsentRules.CATEGORIES_VERSION &&
            recipient == AiConsentRules.normalizeRecipient(endpoint)
}

object AiConsentRules {
    /** 授权文案版本：修改首次授权说明文字时必须 +1。 */
    const val TEXT_VERSION = 1

    /** 类别集合版本：新增数据类别时必须 +1。 */
    const val CATEGORIES_VERSION = 1

    val allCategories: Set<ConsentCategory> = ConsentCategory.entries.toSet()

    /**
     * 规范化接收方：只保留 scheme://host[:port] + 去掉末尾 / 的 path。
     * 避免 `/v1` 与 `/v1/` 被当成两个接收方。
     */
    fun normalizeRecipient(endpoint: String): String {
        val raw = endpoint.trim().removeSuffix("/")
        val uri = runCatching { java.net.URI(raw) }.getOrNull() ?: return raw
        val scheme = uri.scheme?.lowercase() ?: return raw
        val host = uri.host?.lowercase() ?: return raw
        val port = if (uri.port > 0) ":${uri.port}" else ""
        val path = uri.path.orEmpty().removeSuffix("/")
        return "$scheme://$host$port$path"
    }

    fun consentIntroLines(recipient: String): List<String> = listOf(
        "AI 顾问可以按需使用你的目标、进展、日记与历史对话。",
        "这些信息可能会发送到你配置的第三方 AI 服务：$recipient。",
        "不会把整个本地数据库一次性发送给模型。",
        "授权长期有效；你可以在设置 → AI 与隐私中按类别关闭或整体撤回。",
        "AI 只有建议权；修改目标、日记或进展仍需你本人确认。",
        "关闭授权只影响未来请求，不会删除本机已有的日记、目标与聊天记录；",
        "但已经发送给第三方模型服务的数据无法从对方撤回。",
    )

    fun categoriesSummary(categories: Set<ConsentCategory>): String =
        if (categories.isEmpty()) "未授权任何类别"
        else categories.sortedBy { it.ordinal }.joinToString("、") { it.label }
}
