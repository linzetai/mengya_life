package com.mengyalife.app.domain

/**
 * 日记的业务规则（纯函数，可直接在 JVM 单元测试里验证，不依赖 Android）。
 */
object JournalRules {

    const val ERROR_BODY_REQUIRED = "正文不能为空"

    /** 正文最大长度：防止一条记录把数据库和列表撑坏（本轮为软性护栏）。 */
    const val MAX_BODY_LENGTH = 20_000

    const val ERROR_BODY_TOO_LONG = "正文过长，请精简到 $MAX_BODY_LENGTH 字以内"

    /**
     * 校验正文。
     * @return 错误文案；合法时返回 null。
     */
    fun validateBody(rawBody: String): String? {
        val body = rawBody.trim()
        return when {
            body.isEmpty() -> ERROR_BODY_REQUIRED
            body.length > MAX_BODY_LENGTH -> ERROR_BODY_TOO_LONG
            else -> null
        }
    }

    /** 列表里的摘要：压掉换行与多余空白，超长截断。 */
    fun excerpt(rawBody: String, limit: Int = 60): String {
        val oneLine = rawBody.trim().replace(Regex("\\s+"), " ")
        return if (oneLine.length <= limit) oneLine else oneLine.take(limit).trimEnd() + "…"
    }
}
