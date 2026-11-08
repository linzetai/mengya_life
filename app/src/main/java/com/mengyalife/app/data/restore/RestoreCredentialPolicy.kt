package com.mengyalife.app.data.restore

/**
 * 恢复的凭据边界：
 * - endpoint 不变：保留本机 Key 与授权（Key 从不导出）。
 * - endpoint 变化：删除旧 Key，并撤销授权，必须重新配置/授权。
 * - 恢复未携带 endpoint：保持现状。
 */
object RestoreCredentialPolicy {
    data class Decision(val deleteKey: Boolean, val resetConsent: Boolean, val reason: String)

    fun decide(beforeEndpoint: String, afterEndpoint: String, hadKey: Boolean): Decision {
        if (afterEndpoint.isBlank()) {
            return Decision(false, false, "恢复未携带 endpoint，保留本机配置")
        }
        if (beforeEndpoint.trim() == afterEndpoint.trim()) {
            return Decision(false, false, "endpoint 未变化，保留 Key 与授权")
        }
        return Decision(
            deleteKey = hadKey,
            resetConsent = true,
            reason = "endpoint 变化，旧 Key/授权不得复用到新接收方",
        )
    }
}
