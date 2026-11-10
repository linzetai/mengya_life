package com.mengyalife.app.data.restore

/** QA-only restore fault injection; production ignores because call sites check BuildConfig.QA_BUILD. */
object QaRestoreFaultInjector {
    var failTransaction = false
    var failKeyDelete = false
    var failSettings = false
    var pauseAfterCommitMs = 0L
    var pauseAfterConsentMs = 0L

    /** DB 已提交、安全收敛前直接终止进程，用于崩溃重试验证。 */
    var haltAfterCommitBeforeConverge = false

    fun reset() {
        failTransaction = false; failKeyDelete = false; failSettings = false
        pauseAfterCommitMs = 0L; pauseAfterConsentMs = 0L
        haltAfterCommitBeforeConverge = false
    }
}
