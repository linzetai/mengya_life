package com.mengyalife.app.data.care

import com.mengyalife.app.BuildConfig
import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.advisor.AdvisorDataRepository
import com.mengyalife.app.data.advisor.AdvisorEngine
import com.mengyalife.app.data.advisor.ConversationRepository
import com.mengyalife.app.data.consent.AiConsentRepository
import com.mengyalife.app.data.repository.SmallPlanRepository
import com.mengyalife.app.domain.advisor.AdvisorChatMessage
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.AdvisorRules
import com.mengyalife.app.domain.advisor.AdvisorSource
import com.mengyalife.app.domain.advisor.AdvisorSourceType
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.care.CareAction
import com.mengyalife.app.domain.care.CareCandidate
import com.mengyalife.app.domain.care.CareOpportunityFactory
import com.mengyalife.app.domain.care.CareParser
import com.mengyalife.app.domain.care.CarePolicy
import androidx.room.withTransaction
import com.mengyalife.app.domain.goals.PlanTime
import com.mengyalife.app.ui.care.CareCard
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 可被 UI 与 WorkManager 共用的关心执行器：
 * 本地筛候选 → AI 决策 → 硬策略 → 写顾问消息/决策 → 返回待投递卡片。
 * 不直接发系统通知；通知由调用方决定（前台卡片或后台通知）。
 */
class CareRunner(
    private val careRepository: CareRepository,
    private val planRepository: SmallPlanRepository,
    private val dataRepository: AdvisorDataRepository,
    private val consentRepository: AiConsentRepository,
    private val configRepository: AdvisorConfigRepository,
    private val conversations: ConversationRepository,
    private val engine: AdvisorEngine,
    private val settings: CareSettings,
    private val preferences: CarePreferenceRepository,
    private val restoreGeneration: () -> Long = { 0L },
    private val database: com.mengyalife.app.data.local.MengyaDatabase? = null,
    private val writeBarrier: com.mengyalife.app.data.restore.RestoreWriteBarrier? = null,
    /** QA/instrumented 专用事务挂起点；生产为 null。 */
    private val beforeCommit: (suspend () -> Unit)? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun runOnce(): CareCard? {
        android.util.Log.i("CareRun", "start")
        val nowMs = if (BuildConfig.QA_BUILD && settings.testNow > 0) settings.testNow else now()
        if (!settings.enabled) { android.util.Log.i("CareRun", "disabled"); return null }
        val consent = consentRepository.current()
        if (!consent.enabled(ConsentCategory.GOALS)) { android.util.Log.i("CareRun", "no consent"); return null }
        // QA 构建使用独立测试预算，但不放宽免打扰/授权/静音等生产约束。
        val boundEpoch = consent.epoch
        val boundGeneration = restoreGeneration()
        val startupConfig = configRepository.runtimeConfig() ?: return null
        val boundEndpoint = startupConfig.endpoint
        suspend fun boundOk(): Boolean {
            if (writeBarrier != null && !writeBarrier.isReady()) return false
            if (!settings.enabled) return false
            if (restoreGeneration() != boundGeneration) return false
            val latest = consentRepository.current()
            if (!latest.granted || latest.epoch != boundEpoch) return false
            if (!latest.enabled(ConsentCategory.GOALS)) return false
            if (!latest.isValidFor(boundEndpoint)) return false
            val cfg = configRepository.state.first()
            return cfg.endpoint == boundEndpoint
        }
        if (!boundOk()) return null
        suspend fun <T> fenced(block: suspend () -> T): T? {
            if (!boundOk()) return null
            return if (writeBarrier != null) {
                runCatching { writeBarrier!!.withWrite(boundGeneration) { block() } }
                    .onFailure { android.util.Log.w("CareRun", "stale write dropped") }
                    .getOrNull()
            } else block()
        }
        suspend fun saveDecisionFenced(
            opportunityId: Long, action: String, reason: String, sourceRefs: String,
            suggestedText: String?, avoidJson: String?, nextStart: Long?, nextEnd: Long?, modelOk: Boolean,
        ): Long? = fenced {
            careRepository.saveDecision(
                opportunityId, action, reason, sourceRefs, suggestedText, avoidJson, nextStart, nextEnd, modelOk,
            )
        }
        val prefs = settings.policyPrefs().let {
            if (BuildConfig.QA_BUILD) it.copy(dailyDecisionBudget = 20) else it
        }
        if (CarePolicy.inDnd(nowMs, prefs, zone)) { android.util.Log.i("CareRun", "dnd"); return null }

        val snapshot = dataRepository.snapshot(consent)
        val activePrefs = preferences.active()
        fun suppressed(planId: Long?, goalId: Long?, title: String): Boolean = activePrefs.any { pref ->
            if (pref.value != "PAUSED" && pref.value != "SILENCED") return@any false
            when {
                pref.topicKey == null -> false
                planId != null && pref.topicKey == "plan:$planId" -> true
                goalId != null && pref.topicKey == "goal:$goalId" -> true
                pref.topicKey.startsWith("topic:") &&
                    title.contains(pref.topicKey.removePrefix("topic:")) -> true
                else -> false
            }
        }
        val globalBusy = activePrefs.any { it.kind == "BUSY" && it.topicKey == null }
        fun localBusy(planId: Long?, goalId: Long?, title: String): Boolean = activePrefs.any { pref ->
            pref.kind == "BUSY" && pref.topicKey != null && (
                pref.topicKey == planId?.let { "plan:$it" } ||
                    pref.topicKey == goalId?.let { "goal:$it" } ||
                    (pref.topicKey.startsWith("topic:") && title.contains(pref.topicKey.removePrefix("topic:")))
                )
        }
        val timeNote = activePrefs.firstOrNull { it.kind == "TIME" }?.windowNote
        val progressAuthorized = consent.enabled(ConsentCategory.PROGRESS)
        val progressCounts = if (progressAuthorized) planRepository.observeProgressCounts().first() else emptyMap()
        val drafts = buildList {
            snapshot.plans.filter { it.status == "ACTIVE" && !suppressed(it.id, it.goalId, it.title) }.forEach { plan ->
                val range = PlanTime.resolve(plan.timeScope, plan.createdAt, zone) ?: return@forEach
                if (nowMs < range.startAt || nowMs > range.endAt) return@forEach
                add(
                    CareOpportunityFactory.Draft(
                        type = "PLAN_CHECKIN", sourceId = plan.id, planId = plan.id, goalId = plan.goalId,
                        title = plan.title,
                        dedupeKey = "plan:${plan.id}:${range.startAt / (7L * 24 * 3600_000)}",
                        windowStart = nowMs, windowEnd = range.endAt, expiresAt = range.endAt + 24 * 3600_000,
                    ),
                )
            }
            val lastByGoal = snapshot.checkins.groupBy { it.goalId }.mapValues { it.value.maxOf { c -> c.recordedAt } }
            snapshot.goals.filter { it.status.name == "IN_PROGRESS" && !suppressed(null, it.id, it.title) }.forEach { goal ->
                val last = lastByGoal[goal.id]
                if (last == null || nowMs - last >= 7L * 24 * 3600_000) {
                    add(
                        CareOpportunityFactory.Draft(
                            type = "GOAL_STALL", sourceId = goal.id, planId = null, goalId = goal.id,
                            title = goal.title,
                            dedupeKey = "goal:${goal.id}:${Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()}",
                            windowStart = nowMs, windowEnd = nowMs + 24 * 3600_000,
                            expiresAt = nowMs + 48 * 3600_000,
                        ),
                    )
                }
            }
        }
        if (drafts.isNotEmpty()) fenced { careRepository.ensure(drafts) }
        fun suppressedOpportunity(oppId: Long, planId: Long?, goalId: Long?, title: String): Boolean {
            return suppressed(planId, goalId, title)
        }
        val pending = buildList {
            for (opp in careRepository.pending()) {
                if (opp.expiresAt < nowMs) continue
                if (suppressedOpportunity(opp.id, opp.planId, opp.goalId, opp.title)) continue
                val latest = careRepository.latestDecision(opp.id)
                if (latest != null && (latest.action == "SCHEDULE" || latest.action == "DEFER")) {
                    val start = latest.nextWindowStart ?: 0L
                    val end = latest.nextWindowEnd ?: 0L
                    if (start > nowMs) continue
                    if (end > 0L && nowMs > end) {
                        fenced { careRepository.markStatus(opp.id, "EXPIRED") }
                        continue
                    }
                }
                val fb = careRepository.latestFeedback(opp.id)
                if (fb?.kind == "DEFERRED" && nowMs - fb.createdAt < 24L * 3600_000) continue
                add(opp)
                if (size >= 5) break
            }
        }
        android.util.Log.i("CareRun", "pending=${pending.size} drafts=${drafts.size} now=$nowMs")
        if (pending.isEmpty()) return null

        val dayStart = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val decisionsToday = careRepository.decisionsSince(dayStart)
        if (decisionsToday >= prefs.dailyDecisionBudget) return null

        val candidates = pending.map { opp ->
            CareCandidate(
                opportunityId = opp.id, type = opp.type, title = opp.title,
                planId = opp.planId, goalId = opp.goalId, status = opp.status,
                timeScope = null,
                progressCount = if (!progressAuthorized) null
                else opp.planId?.let { progressCounts[it] ?: 0 } ?: 0,
                lastProgressAt = null, windowEnd = opp.windowEnd, expiresAt = opp.expiresAt,
            )
        }
        val candidateLines = candidates.map { c ->
            val plan = snapshot.plans.firstOrNull { it.id == c.planId }
            "[机会#${c.opportunityId}] 类型=${c.type}｜${c.title}｜状态=${plan?.status ?: "目标"}" +
                "｜时间=${plan?.timeScope ?: "未定"}｜执行次数=" +
                (c.progressCount?.let { "$it 次" } ?: "UNKNOWN_DUE_TO_CONSENT") +
                "｜窗口结束=${fmt(c.windowEnd)}"
        }
        val config = configRepository.runtimeConfig()
        if (config == null) { android.util.Log.i("CareRun", "no config"); return null }
        val history = careRepository.sentSince(nowMs - 14L * 24 * 3600_000)
            .take(5)
            .map { "已发过：机会#${it.opportunityId}｜${fmt(it.createdAt)}｜${it.suggestedText.orEmpty().take(24)}" }
        val prompt = AdvisorRules.carePlannerPrompt(
            candidateLines = candidateLines + buildList {
                if (globalBusy) add("用户临时较忙：除非确实重要，否则不要 SEND，选择 DEFER/SCHEDULE。")
                if (!timeNote.isNullOrBlank()) add("用户偏好时段：$timeNote（周回顾）")
            },
            recentDecisionLines = history,
            nowText = fmt(nowMs),
            userZone = zone.id,
        )
        if (!boundOk()) return null
        val result = if (writeBarrier != null) {
            // Hold the shared barrier over the entire outbound request. A restore may
            // not enter maintenance after the readiness check but before chat starts,
            // nor while the request is in flight. This serializes restore behind chat.
            try {
                writeBarrier.withWrite(boundGeneration) {
                    engine.chat(
                        config = config,
                        messages = listOf(
                            AdvisorChatMessage(role = "system", content = prompt),
                            AdvisorChatMessage(role = "user", content = "请基于候选自主决定是否关心用户，只输出 JSON。"),
                        ),
                        maxTokens = 1500,
                    )
                }
            } catch (_: com.mengyalife.app.data.restore.RestoreException) {
                return null
            }
        } else {
            engine.chat(
                config = config,
                messages = listOf(
                    AdvisorChatMessage(role = "system", content = prompt),
                    AdvisorChatMessage(role = "user", content = "请基于候选自主决定是否关心用户，只输出 JSON。"),
                ),
                maxTokens = 1500,
            )
        }
        android.util.Log.i("CareRun", "engine=${result is AdvisorChatResult.Success}")
        if (result !is AdvisorChatResult.Success) {
            saveDecisionFenced(
                opportunityId = pending.first().id, action = "DROP", reason = "模型调用失败，安全退出",
                sourceRefs = "engine", suggestedText = null, avoidJson = null,
                nextStart = null, nextEnd = null, modelOk = false,
            )
            return null
        }
        if (!boundOk()) return null
        val decision = CareParser.parse(result.content, candidates.map { it.opportunityId }.toSet())
            ?: run {
                saveDecisionFenced(
                    opportunityId = pending.first().id, action = "DROP",
                    reason = "模型输出无法解析，安全退出：" + result.content.take(240),
                    sourceRefs = "planner", suggestedText = null, avoidJson = null,
                    nextStart = null, nextEnd = null, modelOk = false,
                )
                return null
            }
        android.util.Log.i("CareRun", "action=${decision.action} id=${decision.opportunityId}")
        val opp = pending.first { it.id == decision.opportunityId }
        if (decision.action == CareAction.SEND &&
            (globalBusy || localBusy(opp.planId, opp.goalId, opp.title))
        ) {
            saveDecisionFenced(
                opportunityId = opp.id, action = "DEFER",
                reason = decision.reason + "｜用户临时较忙，降低打扰",
                sourceRefs = opp.type, suggestedText = decision.suggestedText,
                avoidJson = null, nextStart = null, nextEnd = null, modelOk = true,
            )
            return null
        }
        if (decision.action == CareAction.SEND) {
            val sentToday = careRepository.sentSince(dayStart).size
            val lastSent = careRepository.sentSince(0).firstOrNull()
            val topicLast = careRepository.sentSince(0).firstOrNull { d ->
                careRepository.find(d.opportunityId)?.let { o ->
                    (opp.planId != null && o.planId == opp.planId) || (opp.goalId != null && o.goalId == opp.goalId)
                } ?: false
            }
            val silenced = careRepository.silencedTopics()
            val topicSilenced = (opp.planId?.let { "plan:$it" } in silenced) ||
                (opp.goalId?.let { "goal:$it" } in silenced)
            val gate = CarePolicy.allowSend(
                now = nowMs, prefs = prefs, granted = true,
                candidate = candidates.first { it.opportunityId == opp.id },
                sentToday = sentToday, lastSentAt = lastSent?.createdAt,
                lastTopicSentAt = topicLast?.createdAt, silencedTopic = topicSilenced,
                decisionsToday = decisionsToday, zone = zone,
            )
            if (gate is CarePolicy.Result.Allow) {
                val freshNow = if (BuildConfig.QA_BUILD && settings.testNow > 0) settings.testNow else now()
                if (CarePolicy.inDnd(freshNow, settings.policyPrefs(), zone)) {
                    saveDecisionFenced(
                        opportunityId = opp.id, action = "DEFER",
                        reason = decision.reason + "｜发送前复核进入免打扰",
                        sourceRefs = opp.type, suggestedText = decision.suggestedText,
                        avoidJson = null, nextStart = null, nextEnd = null, modelOk = true,
                    )
                    return null
                }
                if (!boundOk()) return null
                val latestPrefs = preferences.active()
                val stillSuppressed = latestPrefs.any { pref ->
                    pref.topicKey != null &&
                        (pref.value == "PAUSED" || pref.value == "SILENCED") &&
                        (pref.topicKey == opp.planId?.let { "plan:$it" } ||
                            pref.topicKey == opp.goalId?.let { "goal:$it" } ||
                            (pref.topicKey?.startsWith("topic:") == true &&
                                opp.title.contains(pref.topicKey.removePrefix("topic:"))))
                }
                if (stillSuppressed) {
                    fenced { careRepository.markStatus(opp.id, "DROPPED") }
                    return null
                }
                val freshBusy = latestPrefs.any { pref ->
                    pref.kind == "BUSY" && (
                        pref.topicKey == null ||
                            pref.topicKey == opp.planId?.let { "plan:$it" } ||
                            pref.topicKey == opp.goalId?.let { "goal:$it" } ||
                            (pref.topicKey.startsWith("topic:") && opp.title.contains(pref.topicKey.removePrefix("topic:")))
                        )
                }
                val freshFeedback = careRepository.latestFeedback(opp.id)
                if (freshFeedback?.kind == "DEFERRED" &&
                    freshNow - freshFeedback.createdAt < 24L * 3600_000
                ) {
                    saveDecisionFenced(
                        opportunityId = opp.id, action = "DEFER",
                        reason = decision.reason + "｜发送前复核：用户刚点稍后（24h 内不打扰）",
                        sourceRefs = opp.type, suggestedText = decision.suggestedText,
                        avoidJson = null, nextStart = null, nextEnd = null, modelOk = true,
                    )
                    fenced { careRepository.markStatus(opp.id, "PENDING") }
                    return null
                }
                if (freshBusy) {
                    saveDecisionFenced(
                        opportunityId = opp.id, action = "DEFER",
                        reason = decision.reason + "｜发送前复核：用户刚设为忙碌",
                        sourceRefs = opp.type, suggestedText = decision.suggestedText,
                        avoidJson = null, nextStart = null, nextEnd = null, modelOk = true,
                    )
                    return null
                }
                suspend fun commitSend(): CareCard? {
                    if (!careRepository.claimSending(opp.id)) return null
                    beforeCommit?.invoke()
                    val text = decision.suggestedText ?: defaultText(opp.title)
                    val message = conversations.appendAssistant(
                        content = text, contextEpoch = consent.epoch,
                        sources = listOf(AdvisorSource(AdvisorSourceType.CARE, opp.id.toString(), opp.title)),
                        proposalMode = false, expectedRestoreGeneration = boundGeneration,
                    )
                    if (!boundOk()) throw com.mengyalife.app.data.restore.RestoreException(
                        "RESTORE_STALE_RESULT", "恢复后旧关怀结果作废",
                    )
                    val decisionId = saveDecisionFenced(
                        opportunityId = opp.id, action = "SEND", reason = decision.reason,
                        sourceRefs = opp.type, suggestedText = decision.suggestedText,
                        avoidJson = decision.avoid.joinToString(",").takeIf { it.isNotEmpty() },
                        nextStart = null, nextEnd = null, modelOk = true,
                    ) ?: throw com.mengyalife.app.data.restore.RestoreException(
                        "RESTORE_STALE_RESULT", "恢复后旧关怀结果作废",
                    )
                    careRepository.markDecisionDelivered(decisionId, message.id)
                    careRepository.markStatus(opp.id, "SENT")
                    return CareCard(opp.id, opp.title, text, opp.type, isNew = true, decisionId = decisionId)
                }
                val write: suspend () -> CareCard? = {
                    if (database != null) database.withTransaction { commitSend() } else commitSend()
                }
                return if (writeBarrier != null) writeBarrier.withWrite(boundGeneration) { write() } else write()
            }
            saveDecisionFenced(
                opportunityId = opp.id, action = "DEFER",
                reason = decision.reason + "｜策略拒绝：" + (gate as CarePolicy.Result.Deny).reason,
                sourceRefs = opp.type, suggestedText = decision.suggestedText,
                avoidJson = null, nextStart = null, nextEnd = null, modelOk = true,
            )
            return null
        }
        saveDecisionFenced(
            opportunityId = opp.id, action = decision.action.name, reason = decision.reason,
            sourceRefs = opp.type, suggestedText = decision.suggestedText,
            avoidJson = decision.avoid.joinToString(",").takeIf { it.isNotEmpty() },
            nextStart = decision.nextWindowStart, nextEnd = decision.nextWindowEnd, modelOk = true,
        )
        if (decision.action == CareAction.DROP) fenced { careRepository.markStatus(opp.id, "DROPPED") }
        return null
    }

    private fun defaultText(title: String) = "上次说的「$title」，最近有没有新进展？"

    private fun fmt(at: Long): String =
        DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(zone).format(Instant.ofEpochMilli(at))
}
