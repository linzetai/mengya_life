package com.mengyalife.app.domain.advisor

/** 顾问消息角色。 */
enum class AdvisorRole(val wire: String) {
    USER("user"),
    ASSISTANT("assistant"),
}

/** 消息状态。FAILED / CANCELLED 的消息不是 AI 回复，只用于展示失败与重试。 */
enum class AdvisorMessageStatus {
    SENT,
    FAILED,
    CANCELLED,
}

/** 本地保存的一条顾问消息。 */
data class AdvisorMessage(
    val id: String,
    val role: AdvisorRole,
    val content: String,
    val createdAt: Long,
    val status: AdvisorMessageStatus = AdvisorMessageStatus.SENT,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    /** 用户消息发出时附带的目标上下文（仅记录 id，便于用户回看；不保存目标快照）。 */
    val contextGoalId: Long? = null,
    /** 产生这条消息时的授权 epoch。 */
    val contextEpoch: Long = 0L,
    /** 助手回复实际引用的来源（仅引用，不含正文副本）。 */
    val sources: List<AdvisorSource> = emptyList(),
    /** 草案解析失败时的错误。 */
    val proposalError: String? = null,
)

/** 可安全展示的错误。不要把 API Key、请求头或响应原文塞进来。 */
data class AdvisorError(
    val code: String,
    val userMessage: String,
) {
    companion object {
        const val NOT_CONFIGURED = "not_configured"
        const val INVALID_ENDPOINT = "invalid_endpoint"
        const val INVALID_CONFIG = "invalid_config"
        const val UNAUTHORIZED = "unauthorized"
        const val RATE_LIMITED = "rate_limited"
        const val TIMEOUT = "timeout"
        const val OFFLINE = "offline"
        const val SERVER = "server"
        const val INVALID_RESPONSE = "invalid_response"
        const val HTTP = "http"
        const val CANCELLED = "cancelled"
        const val UNKNOWN = "unknown"

        fun unauthorized() = AdvisorError(UNAUTHORIZED, "API Key 无效或已过期（HTTP 401），请到设置页检查密钥。")
        fun rateLimited() = AdvisorError(RATE_LIMITED, "请求过于频繁或额度不足（HTTP 429），请稍后再试。")
        fun timeout() = AdvisorError(TIMEOUT, "请求超时，模型服务没有在限定时间内返回。")
        fun offline() = AdvisorError(OFFLINE, "网络不可用，请检查网络后重试。")
        fun server() = AdvisorError(SERVER, "模型服务暂时不可用（服务器错误），请稍后重试。")
        fun invalidResponse() = AdvisorError(INVALID_RESPONSE, "服务返回的格式无法识别（不是 OpenAI-compatible 响应）。")
        fun cancelled() = AdvisorError(CANCELLED, "已取消本次请求。")
        fun notConfigured() = AdvisorError(NOT_CONFIGURED, "还没有配置模型服务，请先到设置页完成配置。")
        fun invalidEndpoint() = AdvisorError(INVALID_ENDPOINT, "服务地址必须是有效的 HTTPS 地址。")
        fun http(code: Int) = AdvisorError(HTTP, "模型服务返回错误（HTTP $code）。")
        fun unknown() = AdvisorError(UNKNOWN, "请求失败，请稍后重试。")
    }
}

/** 网络/引擎返回。成功只代表拿到了一段非空文本，不代表内容是事实。 */
sealed interface AdvisorChatResult {
    data class Success(val content: String) : AdvisorChatResult
    data class Failure(val error: AdvisorError) : AdvisorChatResult
}

data class AdvisorTestResult(
    val success: Boolean,
    val message: String,
)

/** 设置页展示的配置状态；**不包含密钥明文**。 */
data class AiConfigState(
    val endpoint: String = "",
    val model: String = "",
    val hasKey: Boolean = false,
    /** 仅 QA 构建使用：不发起网络请求的离线引擎。 */
    val useFakeEngine: Boolean = false,
) {
    val configured: Boolean get() = endpoint.isNotBlank() && model.isNotBlank() && hasKey
}

/** 运行时配置（只在内存中短暂存在）。 */
data class AdvisorRuntimeConfig(
    val endpoint: String,
    val model: String,
    val apiKey: String,
) {
    override fun toString(): String =
        "AdvisorRuntimeConfig(endpoint=$endpoint, model=$model, apiKey=***)"
}

/** 发送给 OpenAI-compatible Chat Completions 的一条消息。 */
data class AdvisorChatMessage(
    val role: String,
    val content: String,
)

/** 用户选择附带的目标上下文（已生成可发送文本与摘要）。 */
data class AdvisorGoalContext(
    val goalId: Long,
    val title: String,
    val description: String?,
    val stageTitles: List<String>,
    val actionTitles: List<String>,
    val recentCheckinLines: List<String>,
    val totalCheckins: Int,
) {
    val stageCount: Int get() = stageTitles.size
    val actionCount: Int get() = actionTitles.size
    val summaryLine: String
        get() = "目标「$title」：阶段 $stageCount 个、短期行动 $actionCount 个、进展 $totalCheckins 条"
}

/** 顾问页可选的目标。 */
data class AdvisorGoalChoice(
    val id: Long,
    val title: String,
)

/** 发送带个人数据的消息前的授权摘要。 */
data class AdvisorConsentRequest(
    val input: String,
    val context: AdvisorGoalContext,
)

/** 当前模型服务的安全展示名（不含 path / key）。 */
fun safeEndpointLabel(endpoint: String): String = runCatching {
    val uri = java.net.URI(endpoint.trim())
    uri.host ?: endpoint.trim()
}.getOrDefault(endpoint.trim())
