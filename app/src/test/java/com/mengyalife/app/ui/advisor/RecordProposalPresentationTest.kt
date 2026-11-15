package com.mengyalife.app.ui.advisor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordProposalPresentationTest {
    @Test fun invalidSourceMakesReevaluationTheRecoveryAction() {
        listOf(
            "洞察来源链无法验证，草案未保存",
            "原始记录或授权范围已变化，这条建议需要重新评估；草案未保存",
            "洞察报告已更新，请重新生成",
        ).forEach { assertTrue(it, recordNeedsReevaluation(it)) }
    }

    @Test fun ordinaryFailuresDoNotMasqueradeAsSourceInvalidation() {
        listOf(null, "", "保存失败，请重试", "恢复维护中，暂不能修改目标")
            .forEach { assertFalse(it, recordNeedsReevaluation(it)) }
    }
}
