package com.mengyalife.app.data.restore

/** QA-only restore fault injection; production ignores because call sites check BuildConfig.QA_BUILD. */
object QaRestoreFaultInjector {
    var failTransaction = false
    var failKeyDelete = false
    var failSettings = false
    var pauseAfterCommitMs = 0L
    var pauseAfterConsentMs = 0L

    fun reset() {
        failTransaction = false; failKeyDelete = false; failSettings = false
        pauseAfterCommitMs = 0L; pauseAfterConsentMs = 0L
    }
}
