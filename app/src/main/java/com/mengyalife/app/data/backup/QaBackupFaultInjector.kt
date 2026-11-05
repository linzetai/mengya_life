package com.mengyalife.app.data.backup

/**
 * G4A-1.1 QA-only 故障注入开关。
 *
 * 只在 QA 构建由 MainActivity intent 设置；正式构建不设置、注入实现也会再检查
 * BuildConfig.QA_BUILD，避免影响真实用户。
 */
object QaBackupFaultInjector {
    var failTargetWrite = false
    var truncateTargetWrite = false
    var failTargetRead = false
    var failTargetDelete = false
    var failTempCleanup = false

    fun reset() {
        failTargetWrite = false
        truncateTargetWrite = false
        failTargetRead = false
        failTargetDelete = false
        failTempCleanup = false
    }
}
