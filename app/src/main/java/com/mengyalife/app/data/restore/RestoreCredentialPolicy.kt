package com.mengyalife.app.data.restore

/**
 * 恢复的凭据边界：
 * - endpoint 不变：保留本机 Key 与授权（Key 从不导出）。
 * - endpoint 变化：删除旧 Key，并撤销授权，必须重新配置/授权。
 * - 恢复未携带 endpoint：保持现状。
 */
object RestoreCredentialPolicy {
    data class Decision(val deleteKey: Boolean, val resetConsent: Boolean, val reason: String)

    fun decideFromIntent(intent: CredentialIntent): Decision = PersistedCredentialPolicy.decide(intent)

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

/**
 * 崩溃重试使用的完整凭据意图。
 *
 * 原实现只比较“当前 endpoint”，进程在 DB 提交后、安全收敛前重启时，
 * 当前 endpoint 可能已经是恢复后的值，从而误判为“地址未变”并保留旧 Key。
 * 这里把恢复前 endpoint / 目标 endpoint / 是否有 Key 持久化进 Gate，
 * 重试时只认持久化意图；缺失时 fail-closed。
 */
data class CredentialIntent(
    val beforeEndpoint: String?,
    val targetEndpoint: String?,
    val hadKey: Boolean?,
)

object PersistedCredentialPolicy {
    fun decide(intent: CredentialIntent?): RestoreCredentialPolicy.Decision {
        if (intent == null) {
            return RestoreCredentialPolicy.Decision(
                deleteKey = true,
                resetConsent = true,
                reason = "缺少持久化凭据意图，fail closed：删除本机 Key 并撤销授权",
            )
        }
        val target = intent.targetEndpoint
        if (target == null) {
            return RestoreCredentialPolicy.Decision(
                deleteKey = intent.hadKey != false,
                resetConsent = true,
                reason = "凭据意图不完整，fail closed：删除本机 Key 并撤销授权",
            )
        }
        if (target.isBlank()) {
            return RestoreCredentialPolicy.Decision(false, false, "备份未携带 endpoint，保持本机配置")
        }
        val before = intent.beforeEndpoint
        if (before == null) {
            return RestoreCredentialPolicy.Decision(
                deleteKey = intent.hadKey != false,
                resetConsent = true,
                reason = "原 endpoint 未知，fail closed：删除本机 Key 并撤销授权",
            )
        }
        return RestoreCredentialPolicy.decide(before, target, intent.hadKey ?: true)
    }
}
