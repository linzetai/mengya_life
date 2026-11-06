package com.mengyalife.app.data.restore

import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.advisor.KeyDeleteResult
import com.mengyalife.app.data.consent.AiConsentRepository
import com.mengyalife.app.data.local.AppMetaEntity
import com.mengyalife.app.data.local.MengyaDatabase
import com.mengyalife.app.data.weekly.WeeklyReviewPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.put
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class RestoreStartupState { UNKNOWN, RECONCILING, READY, MAINTENANCE }

enum class RestoreRunStatus { SUCCESS, FAILED_BEFORE_COMMIT, COMMITTED_NEEDS_CONVERGENCE, MAINTENANCE }

data class RestoreRunResult(
    val status: RestoreRunStatus,
    val message: String,
)

/**
 * DB 事务、Gate、Journal、安全重置的唯一编排器。
 * 无恢复执行方法之外的写路径；DB 提交前绝不删除 Key/consent。
 */
class RestoreCoordinator(
    private val database: MengyaDatabase,
    private val gate: RestoreGate,
    private val journal: RestoreJournal,
    private val snapshotStore: RestoreSnapshotStore,
    private val transaction: RestoreTransaction,
    private val consentRepository: AiConsentRepository,
    private val configRepository: AdvisorConfigRepository,
    private val weeklyPreferences: WeeklyReviewPreferences,
    private val settingsApplier: RestoreSettingsApplier,
    private val reader: BackupRestoreReader,
    private val validator: RestoreValidator,
    private val evidenceDir: java.io.File? = null,
    private val writeBarrier: RestoreWriteBarrier = RestoreWriteBarrier(initiallyReady = false),
    private val onSuccess: (String) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
    private val newAttemptId: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Mutex()
    private val _startupState = MutableStateFlow(RestoreStartupState.UNKNOWN)
    val startupState: StateFlow<RestoreStartupState> = _startupState.asStateFlow()
    private val _gateActive = MutableStateFlow(true)
    val gateActive: StateFlow<Boolean> = _gateActive.asStateFlow()
    private val _completionMessage = MutableStateFlow<String?>(null)
    val completionMessage: StateFlow<String?> = _completionMessage.asStateFlow()


    suspend fun awaitReady(timeoutMillis: Long = 30_000): Boolean =
        kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) {
            _startupState.first { it == RestoreStartupState.READY }
            true
        } ?: false

    fun clearCompletionMessage() { _completionMessage.value = null }

    fun isGateActive(): Boolean = _startupState.value != RestoreStartupState.READY

    private fun finishReady() {
        writeBarrier.setReady(true)
        _startupState.value = RestoreStartupState.READY
        _gateActive.value = false
    }

    private fun finishMaintenance() {
        writeBarrier.setReady(false)
        _startupState.value = RestoreStartupState.MAINTENANCE
        _gateActive.value = true
    }

    suspend fun reconcileOnStartup() {
        writeBarrier.withExclusive { reconcileExclusive() }
    }

    private suspend fun reconcileExclusive() {
        _startupState.value = RestoreStartupState.RECONCILING
        _gateActive.value = true
        try {
            val gateState = gate.read()
            if (!gate.isActive()) {
                val journalState = journal.read()
                val metaResult = readMetaResult()
                if (metaResult.isFailure) { finishMaintenance(); return }
                val meta = metaResult.getOrNull()
                if (journalState == null) {
                    if (meta == null) { finishMaintenance(); return }
                    if (meta.restoreAttemptId != null && meta.restoreConvergedAt == null) {
                        // Gate/journal 都丢失但已提交：先用 app_meta 做安全收敛。
                        val state = RestoreGateState(meta.restoreAttemptId, meta.restoreBackupId.orEmpty(),
                            meta.restoreGeneration, RestoreGatePhase.RESET_PENDING, now())
                        runCatching { gate.write(state) }
                        if (!gate.isActive()) { finishMaintenance(); return }
                        val result = converge(state, meta)
                        if (result.status == RestoreRunStatus.SUCCESS) finishReady() else finishMaintenance()
                    } else {
                        finishReady()
                    }
                    return
                }
                // Gate 丢失但 journal 存在：只有 app_meta 明确匹配才继续，否则 fail-closed。
                if (meta?.restoreAttemptId == journalState.attemptId && meta.restoreConvergedAt != null) {
                    journal.clear()
                    finishReady()
                    return
                }
                if (meta?.restoreAttemptId == journalState.attemptId) {
                    val state = RestoreGateState(journalState.attemptId, journalState.backupId,
                        journalState.generation, RestoreGatePhase.RESET_PENDING, now())
                    runCatching { gate.write(state) }
                    if (!gate.isActive()) { finishMaintenance(); return }
                    val result = converge(state, meta)
                    if (result.status == RestoreRunStatus.SUCCESS) finishReady() else finishMaintenance()
                } else {
                    runCatching {
                        gate.write(RestoreGateState(journalState.attemptId, journalState.backupId,
                            journalState.generation, RestoreGatePhase.ARMED, now()))
                    }
                    finishMaintenance()
                }
                return
            }
            val state = gate.read()
            if (state == null) { finishMaintenance(); return }
            val metaResult = readMetaResult()
            if (metaResult.isFailure) { finishMaintenance(); return }
            val meta = metaResult.getOrNull()
            if (meta == null) { finishMaintenance(); return }
            if (meta.restoreAttemptId != state.attemptId) {
                // 能明确证明本次 attempt 未提交：保护旧数据，清理本 attempt 的 Gate/journal。
                try { gate.clear(); journal.clear(); finishReady() } catch (e: Exception) { finishMaintenance() }
                return
            }
            if (meta.restoreGeneration != state.generation) { finishMaintenance(); return }
            if (meta.restoreConvergedAt != null) {
                try { gate.clear(); journal.clear(); finishReady() } catch (e: Exception) { finishMaintenance() }
                return
            }
            val result = converge(state, meta)
            if (result.status == RestoreRunStatus.SUCCESS) finishReady() else finishMaintenance()
        } catch (e: Exception) {
            finishMaintenance()
        }
    }

    suspend fun execute(
        bytes: ByteArray,
        passphrase: CharArray,
        expectedPayloadSha256: String,
        expectedBackupId: String,
): RestoreRunResult = lock.withLock {
        writeBarrier.withExclusive {
            val result = executeExclusive(bytes, passphrase, expectedPayloadSha256, expectedBackupId)
            if (result.status == RestoreRunStatus.FAILED_BEFORE_COMMIT && !gate.isActive() &&
                _startupState.value != RestoreStartupState.MAINTENANCE
            ) finishReady()
            result
        }
    }

    private suspend fun executeExclusive(
        bytes: ByteArray,
        passphrase: CharArray,
        expectedPayloadSha256: String,
        expectedBackupId: String,
    ): RestoreRunResult {
        if (_startupState.value != RestoreStartupState.READY || gate.isActive()) {
            return RestoreRunResult(RestoreRunStatus.MAINTENANCE, "恢复尚未就绪或存在未完成恢复")
        }
        val opened = try {
            reader.open(bytes, passphrase)
        } catch (e: RestoreException) {
            return RestoreRunResult(RestoreRunStatus.FAILED_BEFORE_COMMIT, e.message ?: "备份校验失败")
        }
        if (opened.payloadSha256 != expectedPayloadSha256 ||
            (opened.payload["backupId"] as? kotlinx.serialization.json.JsonPrimitive)?.content != expectedBackupId
        ) {
            return RestoreRunResult(RestoreRunStatus.FAILED_BEFORE_COMMIT, "备份文件在预览后发生变化，请重新预览")
        }
        val validation = try { validator.validate(opened.payload) } catch (e: RestoreException) {
            return RestoreRunResult(RestoreRunStatus.FAILED_BEFORE_COMMIT, e.message ?: "备份校验失败")
        }
        try {
            snapshotStore.requireSpace(bytes.size.toLong())
        } catch (e: Exception) {
            writeQaStatus("FAILED_BEFORE_COMMIT", "preflight", 0L)
            return RestoreRunResult(RestoreRunStatus.FAILED_BEFORE_COMMIT, e.message ?: "可用空间不足")
        }
        val attemptId = newAttemptId()
        val metaResultBefore = readMetaResult()
        if (metaResultBefore.isFailure) {
            return RestoreRunResult(RestoreRunStatus.FAILED_BEFORE_COMMIT, "无法读取恢复元数据，未修改数据")
        }
        val metaBefore = metaResultBefore.getOrNull()
            ?: return RestoreRunResult(RestoreRunStatus.FAILED_BEFORE_COMMIT, "恢复元数据缺失，未修改数据")
        val generation = maxOf(metaBefore.restoreGeneration + 1, 1L)
        try {
            snapshotStore.create(attemptId) // 快照失败：不做任何覆盖
        } catch (e: Exception) {
            return RestoreRunResult(RestoreRunStatus.FAILED_BEFORE_COMMIT, "保护快照生成失败：${e.message ?: "未知错误"}")
        }
        val gateState = RestoreGateState(attemptId, expectedBackupId, generation, RestoreGatePhase.ARMED, now())
        try {
            gate.write(gateState)
            _startupState.value = RestoreStartupState.RECONCILING
            _gateActive.value = true
            journal.write(RestoreJournalState(attemptId, expectedBackupId, generation,
                RestoreJournalPhase.PREPARED, now()))
        } catch (e: Exception) {
            val cleared = runCatching { gate.clear() }.isSuccess
            journal.clear()
            if (cleared) finishReady() else finishMaintenance()
            return RestoreRunResult(RestoreRunStatus.FAILED_BEFORE_COMMIT, "无法持久化恢复门禁")
        }
        val plan = RestorePlanBuilder.build(opened.payload, validation, generation, attemptId, now())
        try {
            transaction.apply(plan, now())
        } catch (e: Exception) {
            // 事务异常自动回滚；本次 attempt 未提交，清理 Gate/journal，旧数据保持。
            val cleared = runCatching { gate.clear() }.isSuccess
            journal.clear()
            if (cleared) finishReady() else finishMaintenance()
            writeQaStatus("FAILED_BEFORE_COMMIT", attemptId, generation)
            return RestoreRunResult(RestoreRunStatus.FAILED_BEFORE_COMMIT,
                "恢复事务失败，旧数据已保留：${e.message ?: "未知错误"}")
        }
        if (com.mengyalife.app.BuildConfig.QA_BUILD) writeQaPhase("DB_COMMITTED", attemptId, generation)
        if (com.mengyalife.app.BuildConfig.QA_BUILD && QaRestoreFaultInjector.pauseAfterCommitMs > 0) {
            kotlinx.coroutines.delay(QaRestoreFaultInjector.pauseAfterCommitMs)
        }
        runCatching {
            journal.write(RestoreJournalState(attemptId, expectedBackupId, generation,
                RestoreJournalPhase.DB_COMMITTED, now()))
        }
        val committed = RestoreGateState(attemptId, expectedBackupId, generation, RestoreGatePhase.RESET_PENDING, now())
        try {
            gate.write(committed)
        } catch (e: Exception) {
            finishMaintenance()
            return RestoreRunResult(RestoreRunStatus.COMMITTED_NEEDS_CONVERGENCE, "数据库已提交，但 Gate 更新失败；重启后继续")
        }
        val metaAfter = readMetaResult()
        if (metaAfter.isFailure || metaAfter.getOrNull()?.restoreAttemptId != attemptId ||
            metaAfter.getOrNull()?.restoreGeneration != generation
        ) {
            finishMaintenance()
            return RestoreRunResult(RestoreRunStatus.COMMITTED_NEEDS_CONVERGENCE, "数据库已提交但元数据无法确认，保持维护状态")
        }
        return converge(committed, metaAfter.getOrNull())
    }

    private suspend fun converge(state: RestoreGateState, meta: AppMetaEntity?): RestoreRunResult {
        if (meta == null || meta.restoreAttemptId != state.attemptId || meta.restoreGeneration != state.generation) {
            finishMaintenance()
            return RestoreRunResult(RestoreRunStatus.MAINTENANCE, "恢复元数据无法确认提交状态；保持维护状态")
        }
        try {
            gate.write(state.copy(phase = RestoreGatePhase.RESET_PENDING))
            journal.write(RestoreJournalState(state.attemptId, state.backupId, state.generation,
                RestoreJournalPhase.DB_COMMITTED, now()))
            consentRepository.resetAfterRestore(state.generation)
            if (com.mengyalife.app.BuildConfig.QA_BUILD) writeQaPhase("CONSENT_DONE", state.attemptId, state.generation)
            if (com.mengyalife.app.BuildConfig.QA_BUILD && QaRestoreFaultInjector.pauseAfterConsentMs > 0) {
                kotlinx.coroutines.delay(QaRestoreFaultInjector.pauseAfterConsentMs)
            }
            if (com.mengyalife.app.BuildConfig.QA_BUILD && QaRestoreFaultInjector.failKeyDelete) {
                throw RestoreException("QA_KEY_DELETE", "QA injected key delete failure")
            }
            val keyResult = configRepository.deleteKeyConfirmed()
            if (keyResult == KeyDeleteResult.FAILED) {
                return RestoreRunResult(RestoreRunStatus.COMMITTED_NEEDS_CONVERGENCE, "数据库已提交，但 Key 删除未确认；正在维护状态")
            }
            weeklyPreferences.setAiEnabled(false)
            gate.write(state.copy(phase = RestoreGatePhase.SETTINGS_PENDING))
            journal.write(RestoreJournalState(state.attemptId, state.backupId, state.generation,
                RestoreJournalPhase.SECURITY_RESET, now()))
            settingsApplier.apply(meta?.restoreSettingsJson)
            database.appMetaDao().markConverged(now())
            val convergedMeta = database.appMetaDao().get()
                ?: throw RestoreException("META_VERIFY", "app_meta 收敛标记缺失")
            if (convergedMeta.restoreAttemptId != state.attemptId ||
                convergedMeta.restoreGeneration != state.generation || convergedMeta.restoreConvergedAt == null
            ) throw RestoreException("META_VERIFY", "app_meta 收敛状态不匹配")
            journal.write(RestoreJournalState(state.attemptId, state.backupId, state.generation,
                RestoreJournalPhase.SETTINGS_APPLIED, now()))
            gate.write(state.copy(phase = RestoreGatePhase.READY_TO_RELEASE))
            journal.write(RestoreJournalState(state.attemptId, state.backupId, state.generation,
                RestoreJournalPhase.DONE, now()))
            gate.clear(); journal.clear(); finishReady()
            writeQaEvidence(state)
            _completionMessage.value = "恢复完成，已撤权并删除旧 Key"
            onSuccess("恢复完成，已撤权并删除旧 Key")
            return RestoreRunResult(RestoreRunStatus.SUCCESS, "恢复完成，已撤权并删除旧 Key")
        } catch (e: Exception) {
            finishMaintenance()
            writeQaStatus("COMMITTED_NEEDS_CONVERGENCE", state.attemptId, state.generation)
            return RestoreRunResult(RestoreRunStatus.COMMITTED_NEEDS_CONVERGENCE,
                "数据库已提交，但安全收敛未完成；重启后将继续")
        }
    }

    private fun writeQaPhase(phase: String, attemptId: String, generation: Long) {
        val dir = evidenceDir ?: return
        runCatching {
            dir.mkdirs()
            java.io.File(dir, "qa-phase.json").writeText(
                "{\"phase\":\"$phase\",\"attemptId\":\"$attemptId\",\"generation\":$generation}",
            )
            java.io.File(dir, "qa-phase-$phase.flag").writeText("1")
        }
    }

    private suspend fun writeQaStatus(label: String, attemptId: String, generation: Long) {
        val dir = evidenceDir ?: return
        runCatching {
            val cfg = configRepository.state.first()
            val consent = consentRepository.current()
            val ai = weeklyPreferences.aiEnabled.first()
            val obj = kotlinx.serialization.json.buildJsonObject {
                put("label", label); put("attemptId", attemptId); put("generation", generation)
                put("keyPresent", cfg.hasKey); put("consentGranted", consent.granted)
                put("aiEnabled", ai); put("gateActive", gate.isActive())
            }
            java.io.File(dir, "qa-status.json").writeText(obj.toString())
        }
    }

    private suspend fun writeQaEvidence(state: RestoreGateState) {
        val dir = evidenceDir ?: return
        runCatching {
            dir.mkdirs()
            val cfg = configRepository.state.first()
            val consent = consentRepository.current()
            val ai = weeklyPreferences.aiEnabled.first()
            val obj = kotlinx.serialization.json.buildJsonObject {
                put("attemptId", state.attemptId); put("generation", state.generation)
                put("keyPresent", cfg.hasKey); put("consentGranted", consent.granted)
                put("aiEnabled", ai); put("gateActive", gate.isActive())
                put("snapshotExists", snapshotStore.exists(state.attemptId))
            }
            java.io.File(dir, "qa-after.json").writeText(obj.toString())
        }
    }

    private suspend fun readMetaResult(): Result<AppMetaEntity?> =
        runCatching { database.appMetaDao().get() }

}
