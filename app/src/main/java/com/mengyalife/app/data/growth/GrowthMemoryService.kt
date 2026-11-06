package com.mengyalife.app.data.growth

import com.mengyalife.app.data.local.JournalEntryDao
import com.mengyalife.app.data.local.growth.GrowthMemoryDao
import com.mengyalife.app.data.local.growth.GrowthMemoryEntity
import com.mengyalife.app.data.local.growth.GrowthSignalDao
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import com.mengyalife.app.domain.growth.DiaryContentHash
import com.mengyalife.app.domain.growth.GrowthCategory
import com.mengyalife.app.domain.growth.GrowthMemoryIds
import java.security.MessageDigest
import java.util.UUID

/**
 * 规则聚类 + 确定性摘要生成成长记忆。
 *
 * - 只用当前日记版本、仍 ACTIVE/CORRECTED 的线索；
 * - 同主题合并跨日记线索；无信号的主题记忆转 STALE；
 * - 用户纠正过的记忆不会被 AI 重新覆盖内容；
 * - DELETED 记忆以 fingerprint 作为墓碑，旧证据重分析不会无条件复活；
 * - 新证据会创建新记忆/新版本。
 */
class GrowthMemoryService(
    private val journalDao: JournalEntryDao,
    private val signalDao: GrowthSignalDao,
    private val memoryDao: GrowthMemoryDao,
    private val transaction: suspend (suspend () -> Unit) -> Unit = { it() },
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val canWrite: () -> Boolean = { true },
    private val writeBarrier: RestoreWriteBarrier? = null,
) {
    suspend fun rebuildAll() {
        val generation = writeBarrier?.generation()
        if (!canWrite()) return
        val work: suspend () -> Unit = {
        transaction {
            val journals = journalDao.loadAll().associateBy { it.id }
            val signals = signalDao.loadActive(300).filter { signal ->
                val journal = journals[signal.sourceId] ?: return@filter false
                signal.sourceType == "DIARY" &&
                    signal.sourceVersion == DiaryContentHash.of(journal.title, journal.body)
            }
            val byTheme = signals.groupBy(::themeFor)
            for ((theme, themed) in byTheme) {
                rebuildTheme(theme, themed, journals)
            }
            val liveThemes = byTheme.keys
            memoryDao.loadAll()
                .filter { it.status in setOf("ACTIVE", "CORRECTED") && it.theme !in liveThemes }
                .forEach { memoryDao.markStaleByTheme(it.theme, now()) }
        }
        }
        if (writeBarrier != null) writeBarrier.withWrite(generation) { work() } else work()
    }

    private suspend fun rebuildTheme(
        theme: String,
        signals: List<com.mengyalife.app.data.local.growth.GrowthSignalEntity>,
        journals: Map<Long, com.mengyalife.app.data.local.JournalEntryEntity>,
    ) {
        if (signals.isEmpty()) return
        val sorted = signals.sortedWith(
            compareBy<com.mengyalife.app.data.local.growth.GrowthSignalEntity> {
                journals[it.sourceId]?.updatedAt ?: it.extractedAt
            }.thenBy { it.id },
        )
        val signalIds = sorted.map { it.id }
        val diaryIds = sorted.map { it.sourceId }.distinct()
        val fingerprint = theme + "|" + sorted.joinToString("|") {
            it.sourceVersion + ":" + hash(it.category + "\u0000" + it.content)
        }
        val existing = memoryDao.findActiveByTheme(theme)
        if (existing == null) {
            if (memoryDao.findDeleted(theme, fingerprint) != null) return
            val content = composeContent(theme, sorted, journals)
            memoryDao.insert(
                GrowthMemoryEntity(
                    id = newId(),
                    theme = theme,
                    content = content.first,
                    isInference = content.second,
                    status = "ACTIVE",
                    createdAt = now(),
                    updatedAt = now(),
                    signalIdsJson = GrowthMemoryIds.encodeStrings(signalIds),
                    sourceDiaryIdsJson = GrowthMemoryIds.encodeLongs(diaryIds),
                    userCorrected = false,
                    version = 1,
                    confidence = averageConfidence(sorted),
                    insufficient = content.third,
                    recentChange = recentChange(sorted, journals),
                    fingerprint = fingerprint,
                ),
            )
            return
        }
        if (existing.fingerprint == fingerprint) return
        val corrected = existing.userCorrected || existing.status == "CORRECTED"
        val content = if (corrected) {
            Triple(existing.content, false, false)
        } else {
            composeContent(theme, sorted, journals)
        }
        memoryDao.update(
            existing.copy(
                content = content.first,
                isInference = content.second,
                status = if (corrected) "CORRECTED" else "ACTIVE",
                updatedAt = now(),
                signalIdsJson = GrowthMemoryIds.encodeStrings(signalIds),
                sourceDiaryIdsJson = GrowthMemoryIds.encodeLongs(diaryIds),
                userCorrected = corrected,
                version = existing.version + 1,
                confidence = if (corrected) existing.confidence else averageConfidence(sorted),
                insufficient = content.third,
                recentChange = if (corrected) {
                    "用户已纠正；新增了 ${signalIds.size - existing.let { GrowthMemoryIds.decodeStrings(it.signalIdsJson).size }} 条线索"
                } else {
                    recentChange(sorted, journals)
                },
                fingerprint = fingerprint,
            ),
        )
    }

    private fun composeContent(
        theme: String,
        signals: List<com.mengyalife.app.data.local.growth.GrowthSignalEntity>,
        journals: Map<Long, com.mengyalife.app.data.local.JournalEntryEntity>,
    ): Triple<String, Boolean, Boolean> {
        val diaries = signals.map { it.sourceId }.distinct()
        val insufficient = diaries.size < 2 || signals.size < 2
        val fact = signals.filterNot { it.isInference }.map { it.content }
        val inference = signals.filter { it.isInference }.map { it.content }
        val body = buildString {
            append("关于「$theme」：")
            if (fact.isNotEmpty()) append(fact.take(4).joinToString("；") { it.take(80) })
            if (inference.isNotEmpty()) {
                if (fact.isNotEmpty()) append("；")
                append("推测：")
                append(inference.take(2).joinToString("；") { it.take(60) })
            }
            if (insufficient) append("（信息不足，暂不作为确定结论）")
        }
        return Triple(
            body,
            signals.any { it.isInference },
            insufficient,
        )
    }

    private fun recentChange(
        signals: List<com.mengyalife.app.data.local.growth.GrowthSignalEntity>,
        journals: Map<Long, com.mengyalife.app.data.local.JournalEntryEntity>,
    ): String {
        val latest = signals.lastOrNull() ?: return "暂无变化"
        return if (signals.size >= 2) {
            "最近一次记录：${latest.content.take(80)}"
        } else {
            "首次记录：${latest.content.take(80)}"
        }
    }

    private fun averageConfidence(
        signals: List<com.mengyalife.app.data.local.growth.GrowthSignalEntity>,
    ): Double? {
        val values = signals.mapNotNull { it.confidence }
        return if (values.isEmpty()) null else values.average()
    }

    private fun themeFor(signal: com.mengyalife.app.data.local.growth.GrowthSignalEntity): String {
        val text = (signal.content + " " + signal.evidence).lowercase()
        val category = GrowthCategory.entries.firstOrNull { it.name == signal.category }
            ?: GrowthCategory.EVENT
        return when {
            listOf("工作", "职业", "汇报", "领导", "项目", "客户", "job", "career", "work", "present", "leader").any { text.contains(it) } ->
                "职业成长"
            listOf("睡眠", "健康", "运动", "跑步", "饮食", "sleep", "health", "run", "exercise", "food").any { text.contains(it) } ->
                "健康与习惯"
            category == GrowthCategory.BLOCKER -> "阻碍与反思"
            category == GrowthCategory.EMOTION -> "情绪与状态"
            category == GrowthCategory.HABIT || category == GrowthCategory.ACTION_CANDIDATE -> "行动与习惯"
            category == GrowthCategory.PROGRESS -> "进展与行动"
            else -> "生活记录"
        }.takeIf { it.isNotBlank() } ?: "生活记录"
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
