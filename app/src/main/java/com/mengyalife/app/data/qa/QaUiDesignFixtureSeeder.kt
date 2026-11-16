package com.mengyalife.app.data.qa

import androidx.room.withTransaction
import com.mengyalife.app.BuildConfig
import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.data.local.insight.InsightReportEntity
import com.mengyalife.app.data.local.plan.PlanEventEntity
import com.mengyalife.app.data.local.plan.SmallPlanEntity
import com.mengyalife.app.data.repository.SmallPlanStatus
import com.mengyalife.app.di.AppContainer
import com.mengyalife.app.domain.advisor.AdvisorMessage
import com.mengyalife.app.domain.advisor.AdvisorSource
import com.mengyalife.app.domain.advisor.AdvisorSourceType
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
import com.mengyalife.app.domain.goals.ProgressKind
import com.mengyalife.app.domain.insight.GrowthInsight
import com.mengyalife.app.domain.insight.InsightCodec
import com.mengyalife.app.domain.insight.InsightReportStatus
import com.mengyalife.app.domain.insight.InsightType
import java.util.UUID

/**
 * Persisted design-preview fixtures, NOT personal growth data and NOT model output.
 * Only the explicitly isolated .qa0254 app may call this; no settings/authorization changes here.
 * No delete, replace, network, restore bypass, or second conversation store.
 */
object QaUiDesignFixtureSeeder {
    private const val MARKER = "QA0254_UI_DESIGN_V1"
    private const val DAY = 24L * 60 * 60 * 1000
    private const val LABEL = "[QA 设计样例]"

    data class Result(
        val rootGoalId: Long,
        val stageGoalId: Long,
        val actionGoalId: Long,
        val independentGoalId: Long,
        val linkedPlanId: Long,
        val independentPlanId: Long,
        val journalIds: List<Long>,
        val checkinIds: List<Long>,
        val messageIds: List<String>,
        val reportId: String,
        val dataVersion: String,
        val reusedCurrentReport: Boolean,
    )

    /**
     * Call only after the isolated QA setup has used existing grant/config APIs.
     * Fails closed before inserting when required existing authorization is absent.
     * Repeated calls reuse marked rows and exact-version reports; edited rows are never reset.
     */
    suspend fun seed(container: AppContainer, now: Long = System.currentTimeMillis()): Result {
        check(BuildConfig.QA_BUILD && BuildConfig.APPLICATION_ID.endsWith(".qa0254")) {
            "设计夹具仅允许在隔离 .qa0254 QA 应用中写入"
        }
        check(container.restoreCoordinator.awaitReady()) { "恢复未就绪，未写入设计夹具" }
        val barrier = container.restoreWriteBarrier
        val generation = barrier.generation()
        return barrier.withWrite(generation) {
            check(!container.restoreCoordinator.isGateActive()) { "恢复处理中，未写入设计夹具" }
            val consent = container.aiConsentRepository.current()
            check(consent.enabled(ConsentCategory.GOALS) && consent.enabled(ConsentCategory.PROGRESS)) {
                "请先通过隔离 QA 的现有授权入口启用目标和进展；设计夹具不自行授权"
            }
            val database = container.databaseHandle
            database.withTransaction {
                val goalDao = database.goalDao()
                val planDao = database.smallPlanDao()
                val journalDao = database.journalEntryDao()
                val reportDao = database.insightReportDao()
                val conversations = container.conversationRepository

                suspend fun ensureGoal(
                    key: String,
                    title: String,
                    level: GoalLevel,
                    parentId: Long?,
                    status: GoalStatus,
                ): Long {
                    val marker = "$MARKER goal:$key"
                    val existing = goalDao.loadAllGoals().singleOrNull { it.description?.startsWith(marker) == true }
                    if (existing != null) return existing.id
                    return goalDao.insertGoal(GoalEntity(
                        title = title,
                        description = "$marker\n仅用于隔离 QA 设计候选；不是用户真实目标或成长成果。",
                        level = level, parentId = parentId, domain = GoalDomain.LEARNING,
                        status = status, startDate = now - 14 * DAY,
                        createdAt = now - 14 * DAY, updatedAt = now - DAY,
                    ))
                }
                val root = ensureGoal("root", "建立稳定的阅读习惯", GoalLevel.LONG_TERM, null, GoalStatus.IN_PROGRESS)
                val stage = ensureGoal("stage", "本月读完一本书", GoalLevel.STAGE, root, GoalStatus.IN_PROGRESS)
                val action = ensureGoal("action", "睡前阅读十分钟", GoalLevel.ACTION, stage, GoalStatus.IN_PROGRESS)
                val independent = ensureGoal("independent", "给生活留一点散步的时间", GoalLevel.LONG_TERM,
                    null, GoalStatus.NOT_STARTED)

                suspend fun ensureCheckin(
                    key: String, value: Double, recordedAt: Long, note: String, legacyNote: String,
                ): Long {
                    val original = "$MARKER snapshot:$key"
                    val existing = goalDao.loadAllCheckins().singleOrNull {
                        it.goalId == action && it.source == MARKER && it.originalText == original
                    }
                    if (existing != null) {
                        // Exact legacy text + own source/original marker + actual ID only.
                        // Values, record time, IDs, and any user-edited note remain untouched.
                        if (existing.note == "$LABEL $legacyNote") {
                            database.openHelper.writableDatabase.execSQL(
                                "UPDATE goal_checkins SET note = ? WHERE id = ? AND goal_id = ? " +
                                    "AND source = ? AND original_text = ? AND note = ?",
                                arrayOf<Any>("$LABEL $note", existing.id, action, MARKER, original, existing.note),
                            )
                        }
                        return existing.id
                    }
                    return goalDao.insertCheckin(GoalCheckinEntity(
                        goalId = action, recordedAt = recordedAt, note = "$LABEL $note",
                        metricValue = value, metricUnit = "分钟", source = MARKER,
                        amountKind = ProgressKind.SNAPSHOT, originalText = original,
                    ))
                }
                val firstCheckin = ensureCheckin("first", 10.0, now - 7 * DAY,
                    note = "阅读累计记录更新到十分钟。",
                    legacyNote = "这次睡前阅读时长为十分钟。")
                val latestCheckin = ensureCheckin("latest", 20.0, now - DAY,
                    note = "阅读累计记录更新到二十分钟，上次累计十分钟；计划仍是每周两次、每次十分钟。",
                    legacyNote = "这次睡前阅读时长为二十分钟，读得很投入。")

                fun sources(turn: String) = listOf(AdvisorSource(
                    AdvisorSourceType.GOAL, action.toString(), "睡前阅读十分钟",
                    metadata = mapOf("qaFixture" to MARKER, "fixtureTurn" to turn),
                ))
                suspend fun ensureMessage(
                    turn: String, user: Boolean, text: String, legacyText: String? = null,
                ): AdvisorMessage {
                    val existing = conversations.loadAll().singleOrNull { message ->
                        message.sources.any { source -> source.metadata["qaFixture"] == MARKER &&
                            source.metadata["fixtureTurn"] == turn }
                    }
                    if (existing != null) {
                        val legacy = legacyText?.let { if (user) it else "$LABEL $it" }
                        if (legacy != null && existing.content == legacy) {
                            val raw = database.advisorMessageDao().loadAll().single { it.id == existing.id }
                            // Marker was decoded/verified above; matching the original complete sources JSON
                            // at the UPDATE boundary preserves every source and epoch without reconstruction.
                            if (raw.content == legacy && raw.sourcesJson != null) {
                                database.openHelper.writableDatabase.execSQL(
                                    "UPDATE advisor_messages SET content = ? WHERE id = ? AND content = ? " +
                                        "AND sources_json = ? AND context_epoch = ? AND role = ?",
                                    arrayOf<Any>(if (user) text else "$LABEL $text", raw.id, legacy,
                                        raw.sourcesJson, raw.contextEpoch, raw.role),
                                )
                            }
                            return conversations.loadAll().single { it.id == existing.id }
                        }
                        return existing
                    }
                    return if (user) conversations.appendUser(
                        content = text, contextGoalId = action, contextEpoch = consent.epoch,
                        expectedRestoreGeneration = generation, sources = sources(turn),
                    ) else conversations.appendAssistant(
                        content = "$LABEL $text", contextEpoch = consent.epoch, sources = sources(turn),
                        proposalMode = false, expectedRestoreGeneration = generation,
                    )
                }
                val user1 = ensureMessage("1-user", true, "最近想把阅读重新放回生活里，不求读得快，先从睡前十分钟开始。")
                val assistant1 = ensureMessage("1-assistant", false,
                    text = "把起点放轻一点。你想先选哪两个晚上？",
                    legacyText = "那就把起点放轻一点。你想把这十分钟放在哪两个晚上？这里的对话是设计样例，不是真实模型回复。")
                val user2 = ensureMessage("2-user", true,
                    "确认这周周二和周四睡前读十分钟，关联“睡前阅读十分钟”。另外周末整理书桌，作为独立计划。")
                val assistant2 = ensureMessage("2-assistant", false,
                    text = "已确认：阅读计划关联当前目标，整理书桌是独立计划。",
                    legacyText = "两个小计划已经记下了。阅读连着你的阅读目标，整理书桌则单独保留；不需要把每件小事都变成长期目标。")
                val user3 = ensureMessage("3-user", true,
                    text = "阅读累计记录更新到二十分钟，上次累计十分钟。我想先保持每周两次、每次十分钟。",
                    legacyText = "昨晚读了二十分钟，比上次十分钟更投入，不过我想先保持每周两次。")
                val assistant3 = ensureMessage("3-assistant", false,
                    text = "你提到想维持每周两次、每次十分钟。累计时长从 10 分钟更新到 20 分钟，是累计快照，不代表单次读了 20 分钟。",
                    legacyText = "可以先保持每周两次、每次十分钟的节奏。这两条是累计记录快照：从十分钟更新到二十分钟，不代表单次读了二十分钟，也不需要马上增加频率。以上为 QA 合成对话。")

                suspend fun ensurePlan(goalId: Long?, title: String, updatedAt: Long): Long {
                    val existing = planDao.loadAll().singleOrNull {
                        it.sourceMessageId == user2.id && it.goalId == goalId
                    }
                    if (existing != null) return existing.id
                    val id = planDao.insert(SmallPlanEntity(
                        title = title, timeScope = "本周", goalId = goalId, status = SmallPlanStatus.ACTIVE,
                        sourceMessageId = user2.id, createdAt = now, updatedAt = updatedAt,
                    ))
                    planDao.insertEvent(PlanEventEntity(planId = id, status = SmallPlanStatus.ACTIVE,
                        note = "$LABEL 来自隔离设计候选的明确确认，不是真实用户承诺。", createdAt = now))
                    return id
                }
                val independentPlan = ensurePlan(null, "周末整理书桌", now)
                // One millisecond ordering is synthetic fixture metadata, not an execution event.
                val linkedPlan = ensurePlan(action, "周二和周四睡前读十分钟", now + 1)

                suspend fun ensureJournal(
                    key: String, title: String, body: String, at: Long, legacyBody: String? = null,
                ): Long {
                    val marker = "$MARKER journal:$key"
                    val suffix = "\n\n[$marker｜仅用于隔离 QA 设计候选，非真实个人日记。]"
                    val existing = journalDao.loadAll().singleOrNull { it.body.contains(marker) }
                    if (existing != null) {
                        if (legacyBody != null && existing.body == legacyBody + suffix) {
                            database.openHelper.writableDatabase.execSQL(
                                "UPDATE journal_entries SET body = ? WHERE id = ? AND body = ?",
                                arrayOf<Any>(body + suffix, existing.id, existing.body),
                            )
                        }
                        return existing.id
                    }
                    return journalDao.insert(JournalEntryEntity(
                        title = title,
                        body = body + suffix,
                        createdAt = at, updatedAt = at,
                    ))
                }
                val journal1 = ensureJournal("first", "把书放在枕边",
                    "今晚把书放在床头，手机留在书桌上。读了十分钟，内容不多，但终于给自己留了一小段安静的时间。", now - 7 * DAY)
                val journal2 = ensureJournal("latest", "不用急着多读一点",
                    body = "睡前阅读的累计记录更新到二十分钟，上次累计十分钟。我更想保住每周两次、每次十分钟的从容节奏，而不是马上给自己加码。",
                    at = now - DAY,
                    legacyBody = "昨晚读到喜欢的章节，不知不觉读了二十分钟。今天回想起来，更想保住这个从容的节奏，而不是马上给自己加码。")

                // Facts, source IDs, observation window and version all come from the real service.
                // No engine call is made; only the suggestion text is explicitly synthetic QA material.
                val current = container.insightService.currentInput()
                check(container.aiConsentRepository.current() == consent && barrier.generation() == generation) {
                    "授权或恢复代际变化，设计夹具事务已取消"
                }
                check(current.hasEnoughData) { "真实已授权数据不足，未伪造洞察缓存" }
                val records = goalDao.loadAllCheckins().associateBy { it.id }
                val trend = current.facts.singleOrNull { fact ->
                    fact.id.startsWith("trend-$action-") && !fact.isInference && fact.sourceRefs.size >= 2 &&
                        fact.sourceRefs.all { ref ->
                            val record = ref.recordId.toLongOrNull()?.let(records::get)
                            ref.type == "CHECKIN" && record != null && record.goalId == action &&
                                record.metricUnit == "分钟" && record.amountKind == ProgressKind.SNAPSHOT &&
                                record.recordedAt in current.periodStart..current.periodEnd
                        }
                } ?: error("未找到该行动真实可核验的分钟 SNAPSHOT 趋势；没有伪造 factId 或来源")
                val cached = reportDao.findByDataVersion(current.dataVersion)
                val reportId = cached?.id ?: "$MARKER-report-${UUID.randomUUID()}"
                if (cached == null) {
                    val insight = GrowthInsight(
                        id = "$MARKER-insight-reading", type = InsightType.SUGGESTION,
                        title = "阅读时间比上次增加",
                        body = "如果睡前仍方便，可以先维持每周两次，每次十分钟；不必马上增加频率。",
                        isInference = true, confidence = null,
                        sources = trend.sourceRefs, factIds = listOf(trend.id),
                    )
                    reportDao.insert(InsightReportEntity(
                        id = reportId, dataVersion = current.dataVersion, status = InsightReportStatus.READY.name,
                        summary = "$LABEL 合成建议，仅用于设计候选；事实来自隔离 Room 夹具的真实规则计算，不是真实成长结论或模型输出。",
                        factsJson = InsightCodec.factsToJson(current.facts),
                        insightsJson = InsightCodec.insightsToJson(listOf(insight)),
                        periodStart = current.periodStart, periodEnd = current.periodEnd,
                        generatedAt = current.periodEnd, error = null, model = "QA_SYNTHETIC_DESIGN_FIXTURE_NO_MODEL",
                    ))
                }
                Result(root, stage, action, independent, linkedPlan, independentPlan,
                    listOf(journal1, journal2), listOf(firstCheckin, latestCheckin),
                    listOf(user1.id, assistant1.id, user2.id, assistant2.id, user3.id, assistant3.id),
                    reportId, current.dataVersion, reusedCurrentReport = cached != null)
            }
        }
    }
}
