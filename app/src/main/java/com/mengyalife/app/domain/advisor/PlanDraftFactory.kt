package com.mengyalife.app.domain.advisor

/**
 * 小计划的客户端兜底：当模型返回 null/格式错误时，仍能根据用户原话生成最小草案。
 * 只生成待确认草稿；不写数据库。
 */
object PlanDraftFactory {

    private val TIME_SCOPES = listOf("今天", "明天", "今晚", "这周", "本周", "下周", "周末", "每天")

    fun explicitCreate(text: String): Boolean {
        val value = text.trim()
        return value.contains("小计划") ||
            value.contains("记成计划") ||
            value.contains("记为计划") ||
            value.contains("记一条计划") ||
            value.contains("记个计划")
    }

    fun statusIntent(text: String): String? {
        val value = text.trim()
        return when {
            listOf("都练完", "都做完", "完成了", "练完了", "做完了", "搞定了").any { value.contains(it) } ->
                "COMPLETED"
            listOf("先暂停", "暂停一下", "先不练", "先不做", "缓一缓").any { value.contains(it) } ->
                "PAUSED"
            listOf("不打算继续", "取消了", "取消这个", "不做了").any { value.contains(it) } ->
                "CANCELLED"
            else -> null
        }
    }

    fun buildCreate(text: String, intent: RecordIntent): RecordProposalDraft? {
        val raw = text.trim()
        if (raw.isEmpty()) return null
        val quoted = Regex("[「『\"]([^」』\"]+)[」』\"]").find(raw)?.groupValues?.getOrNull(1)
        var content = quoted?.trim().orEmpty()
        if (content.isEmpty()) {
            content = raw
                .replace("帮我把", "")
                .replace("帮我", "")
                .replace("记成一个小计划", "")
                .replace("记成小计划", "")
                .replace("记成计划", "")
                .replace("记为计划", "")
                .replace("记一条计划", "")
                .replace("记个计划", "")
                .replace("小计划", "")
                .trim()
                .trimEnd('。', '！', '!', '，', ',')
                .trim()
        }
        if (content.isEmpty() || content.length > 60) return null
        val scope = TIME_SCOPES.firstOrNull { raw.contains(it) }
        return RecordProposalDraft(
            kind = RecordKind.PLAN,
            goalId = null,
            goalTitle = null,
            goalCandidates = emptyList(),
            amountKind = RecordAmountKind.NONE,
            metricValue = null,
            metricUnit = null,
            note = content,
            occurredAt = null,
            pendingFields = emptyList(),
            intent = intent,
            originalText = raw,
            planChange = PlanChangeDraft(
                change = PlanChangeKind.CREATE,
                title = content,
                timeScope = scope,
            ),
        )
    }

    fun buildStatus(
        plans: List<ContextPlanRecord>,
        newStatus: String,
        text: String,
        intent: RecordIntent,
    ): RecordProposalDraft? {
        val candidates = plans.filter { it.status == "ACTIVE" || it.status == "PAUSED" }
        val chosen = candidates.filter { it.status == "ACTIVE" }.ifEmpty { candidates }
        if (chosen.size != 1) return null
        val plan = chosen.single()
        return RecordProposalDraft(
            kind = RecordKind.PLAN,
            goalId = null,
            goalTitle = null,
            goalCandidates = emptyList(),
            amountKind = RecordAmountKind.NONE,
            metricValue = null,
            metricUnit = null,
            note = text.trim(),
            occurredAt = null,
            pendingFields = emptyList(),
            intent = intent,
            originalText = text.trim(),
            planChange = PlanChangeDraft(
                change = PlanChangeKind.STATUS,
                targetPlanId = plan.id,
                title = plan.title,
                newStatus = newStatus,
                note = text.trim(),
            ),
        )
    }
}
