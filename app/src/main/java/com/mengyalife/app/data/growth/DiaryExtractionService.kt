package com.mengyalife.app.data.growth

import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.advisor.AdvisorEngine
import com.mengyalife.app.data.consent.AiConsentRepository
import com.mengyalife.app.data.local.growth.GrowthSignalDao
import com.mengyalife.app.data.local.growth.GrowthSignalEntity
import com.mengyalife.app.data.repository.JournalRepository
import com.mengyalife.app.domain.advisor.AdvisorChatMessage
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.AdvisorError
import com.mengyalife.app.domain.advisor.AiConfigState
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.growth.DiaryAnalysisStatus
import com.mengyalife.app.domain.growth.DiaryExtractionParseResult
import com.mengyalife.app.domain.growth.DiaryExtractionProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.UUID

interface DiaryExtractor {
    suspend fun extract(diaryId: Long, force: Boolean = false): DiaryExtractionOutcome
}

sealed interface DiaryExtractionOutcome {
    data class Success(val inserted: Int, val total: Int) : DiaryExtractionOutcome
    data object AlreadyDone : DiaryExtractionOutcome
    data object Busy : DiaryExtractionOutcome
    data object TooSoon : DiaryExtractionOutcome
    data class Skipped(val reason: String) : DiaryExtractionOutcome
    data class Failed(val message: String) : DiaryExtractionOutcome
}

/**
 * 前台机会式日记提取。
 *
 * - 只在 AI 已配置、持续授权有效且 JOURNAL 开启时发请求；
 * - 按日记内容哈希幂等；编辑后旧线索 STALE，重新分析；
 * - 失败只更新日记分析状态，不影响日记保存；
 * - Mutex + 最小间隔 + 最多 6 条 + 4000 字输入，限制并发/体积/频率；
 * - 引擎返回后再次校验授权/配置/日记版本，撤回则不写线索。
 */
class DiaryExtractionService(
    private val journalRepository: JournalRepository,
    private val growthSignalDao: GrowthSignalDao,
    private val configRepository: AdvisorConfigRepository,
    private val consentRepository: AiConsentRepository,
    private val networkEngine: AdvisorEngine,
    private val fakeEngine: AdvisorEngine?,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val minIntervalMs: Long = 30_000,
) : DiaryExtractor {
    private val mutex = Mutex()

    override suspend fun extract(diaryId: Long, force: Boolean): DiaryExtractionOutcome =
        mutex.withLock {
            val journal = journalRepository.getEntry(diaryId)
                ?: return@withLock DiaryExtractionOutcome.Skipped("日记不存在")
            val hash = contentHash(journal.title, journal.body)
            if (journal.analysisStatus == DiaryAnalysisStatus.COMPLETED &&
                journal.analysisHash == hash
            ) {
                return@withLock DiaryExtractionOutcome.AlreadyDone
            }
            if (journal.analysisStatus == DiaryAnalysisStatus.ANALYZING) {
                return@withLock DiaryExtractionOutcome.Busy
            }
            val last = journal.analysisLastAttemptAt
            if (!force && last != null && now() - last < minIntervalMs) {
                return@withLock DiaryExtractionOutcome.TooSoon
            }
            val readiness = readiness()
            if (readiness != null) {
                return@withLock DiaryExtractionOutcome.Skipped(readiness)
            }
            val started = journalRepository.markAnalyzing(
                id = diaryId,
                expectedUpdatedAt = journal.updatedAt,
                hash = hash,
            )
            if (!started) return@withLock DiaryExtractionOutcome.Busy

            try {
                val config = configRepository.state.first()
                val runtime = configRepository.runtimeConfig()
                if (runtime == null || runtime.endpoint != config.endpoint) {
                    journalRepository.markAnalysisFailed(diaryId, hash, "模型服务配置已变化")
                    return@withLock DiaryExtractionOutcome.Failed("模型服务配置已变化，请重试")
                }
                val engine = if (config.useFakeEngine && fakeEngine != null) fakeEngine else networkEngine
                val prompt = DiaryExtractionProtocol.prompt(journal.title, journal.body)
                val result = engine.chat(
                    config = runtime,
                    messages = listOf(
                        AdvisorChatMessage(role = "system", content = "只输出严格 JSON；不要诊断或修改任何业务数据。"),
                        AdvisorChatMessage(role = "user", content = prompt),
                    ),
                    maxTokens = 1_200,
                )
                // 请求期间授权/配置/日记变化：丢弃结果
                val afterConfig = configRepository.state.first()
                val afterConsent = consentRepository.current()
                val afterJournal = journalRepository.getEntry(diaryId)
                if (afterJournal == null ||
                    contentHash(afterJournal.title, afterJournal.body) != hash
                ) {
                    return@withLock DiaryExtractionOutcome.Failed("日记已在分析期间变化，请重新分析")
                }
                if (afterConfig.endpoint != runtime.endpoint ||
                    !afterConsent.isValidFor(runtime.endpoint) ||
                    !afterConsent.enabled(ConsentCategory.JOURNAL)
                ) {
                    journalRepository.markAnalysisFailed(diaryId, hash, "授权或配置已变化")
                    return@withLock DiaryExtractionOutcome.Failed("授权或配置已变化，未写入线索")
                }
                when (result) {
                    is AdvisorChatResult.Failure -> {
                        journalRepository.markAnalysisFailed(
                            diaryId, hash, result.error.userMessage,
                        )
                        return@withLock DiaryExtractionOutcome.Failed(result.error.userMessage)
                    }
                    is AdvisorChatResult.Success -> {
                        when (val parsed = DiaryExtractionProtocol.parse(
                            result.content, journal.title, journal.body,
                        )) {
                            is DiaryExtractionParseResult.Failure -> {
                                journalRepository.markAnalysisFailed(diaryId, hash, parsed.message)
                                return@withLock DiaryExtractionOutcome.Failed(parsed.message)
                            }
                            is DiaryExtractionParseResult.Success -> {
                                growthSignalDao.markStaleOtherVersions(diaryId, hash, now())
                                var inserted = 0
                                parsed.signals.forEach { signal ->
                                    val entity = GrowthSignalEntity(
                                        id = newId(),
                                        sourceType = "DIARY",
                                        sourceId = diaryId,
                                        sourceVersion = hash,
                                        category = signal.category.name,
                                        content = signal.content,
                                        evidence = signal.evidence,
                                        isInference = signal.isInference,
                                        confidence = signal.confidence,
                                        extractedAt = now(),
                                        status = "ACTIVE",
                                        origin = "AI",
                                        sourceTitle = journal.title,
                                        contentHash = signalHash(signal.category.name, signal.content),
                                        updatedAt = now(),
                                    )
                                    if (growthSignalDao.insertIgnore(entity) > 0) inserted++
                                }
                                journalRepository.markAnalysisCompleted(diaryId, hash)
                                DiaryExtractionOutcome.Success(inserted, parsed.signals.size)
                            }
                        }
                    }
                }
            } catch (cancel: CancellationException) {
                journalRepository.markAnalysisFailed(diaryId, hash, "分析已取消")
                throw cancel
            } catch (error: Throwable) {
                journalRepository.markAnalysisFailed(diaryId, hash, error.message ?: "分析失败")
                DiaryExtractionOutcome.Failed(error.message ?: "分析失败")
            }
        }

    /** 不读取/发送前的本地前置条件；返回 null 表示可以分析。 */
    private suspend fun readiness(): String? {
        val config = configRepository.state.first()
        if (!config.configured) return "尚未配置 AI 服务"
        val runtime = configRepository.runtimeConfig() ?: return "API Key 不可用"
        if (runtime.endpoint != config.endpoint) return "模型服务配置已变化"
        val consent = consentRepository.current()
        if (!consent.isValidFor(runtime.endpoint)) return "授权已失效或接收方已变化"
        if (!consent.enabled(ConsentCategory.JOURNAL)) return "未开启日记数据授权"
        return null
    }

    private fun contentHash(title: String?, body: String): String =
        sha256((title.orEmpty() + "\u0000" + body).trim())

    private fun signalHash(category: String, content: String): String =
        sha256(category + "\u0000" + content.trim())

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
