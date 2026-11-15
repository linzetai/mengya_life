package com.mengyalife.app.ui.advisor

/** Presentation only: never authorizes a write or repairs a stored source reference. */
fun recordNeedsReevaluation(error: String?): Boolean = error?.let {
    it.contains("来源") || it.contains("重新生成") || it.contains("重新评估")
} == true
