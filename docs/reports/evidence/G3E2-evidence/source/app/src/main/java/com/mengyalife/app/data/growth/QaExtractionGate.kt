package com.mengyalife.app.data.growth

/**
 * 仅 QA 构建使用的提取时序注入；正式包不读取 Intent extra。
 * 用于真机复现「授权撤回与请求发起之间」和「旧版本结果」竞态。
 */
object QaExtractionGate {
    @Volatile var withdrawBeforeRequest: Boolean = false
    @Volatile var delayBeforeRequestMs: Long = 0L

    fun reset() {
        withdrawBeforeRequest = false
        delayBeforeRequestMs = 0L
    }
}
