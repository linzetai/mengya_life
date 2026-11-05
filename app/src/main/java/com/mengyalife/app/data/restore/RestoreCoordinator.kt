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
    private val onSuccess: (String) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
    private val newAttemptId: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Mutex()
    private val _gateActive = MutableStateFlow(gate.isActive())
    val gateActive: StateFlow<Boolean> = _gateActive.asStateFlow()
    private val _completionMessage = MutableStateFlow<String?>(null)
    val completionMessage: StateFlow<String?> = _completionMessage.asStateFlow()

    fun clearCompletionMessage() { _completionMessage.value = null }

    fun isGateActive(): Boolean = gate.isActive()

    suspend fun reconcileOnStartup() {
        _gateActive.value = gate.isActive()
        if (!gate.isActive()) {
            val orphanJournal = journal.read()
            if (orphanJournal == null) {
                val meta = readMetaOrNull()
                if (meta?.restoreAttemptId != null && meta.restoreConvergedAt == null) {
                    // 已提交但 Gate/journal 都丢失：不能判断安全收敛，fail-closed。
                    runCatching {
                        gate.write(RestoreGateState(meta.restoreAttemptId, meta.restoreBackupId.orEmpty(),
                            meta.restoreGeneration, RestoreGatePhase.ARMED, now()))
                    }
                    _gateActive.value = gate.isActive()
                }
                return
            }
            if (orphanJournal != null) {
                // Gate 丢失但 journal 存在：只有 app_meta 明确匹配才继续，否则 fail-closed。
                val meta = readMetaOrNull()
                if (meta?.restoreAttemptId == orphanJournal.attemptId) {
                    val state = RestoreGateState(orphanJournal.attemptId, orphanJournal.backupId,
                        orphanJournal.generation, RestoreGatePhase.RESET_PENDING, now())
                    runCatching { gate.write(state) }
                    _gateActive.value = gate.isActive()
                    if (_gateActive.value) converge(state, meta)
                } else {
                    runCatching {
                        gate.write(RestoreGateState(orphanJournal.attemptId, orphanJournal.backupId,
                            orphanJournal.generation, RestoreGatePhase.ARMED, now()))
                    }
                    _gateActive.value = gate.isActive()
                }
            }
            return
        }
        val state = gate.read() ?: run { _gateActive.value = true; return }
        val meta = readMetaOrNull() ?: run { _gateActive.value = true; return }
        if (meta.restoreAttemptId != state.attemptId) {
            // 能明确证明本次 attempt 未提交：保护旧数据，清理本 attempt 的 Gate/journal。
            gate.clear(); journal.clear(); _gateActive.value = false
            return
        }
        if (meta.restoreGeneration != state.generation) {
            _gateActive.value = true // 异常，fail-closed
            return
        }
        _gateActive.value = true
        converge(state, meta)
    }

    suspend fun execute(
        bytes: ByteArray,
        passphrase: CharArray,
        expectedPayloadSha256: String,
        expectedBackupId: String,
    ): RestoreRunResult = lock.withLock {
        if (gate.isActive()) return@withLock RestoreRunResult(RestoreRunStatus.MAINTENANCE, "存在未完成的恢复，需先收敛")
        val opened = try {
            reader.open(bytes, passphrase)
        } catch (e: RestoreException) {
            return@withLock RestoreRunResult(RestoreRunStatus.FAILED_BEFORE_COMMIT, e.message ?: "备份校验失败")
        }
        if (opened.payloadSha256 != expectedPayloadSha256 ||
            (opened.payload["backupId"] as? kotlinx.serialization.json.JsonPrimitive)?.content != expectedBackupId
        ) {
            return@withLock RestoreRunResult(RestoreRunStatus.FAILED_BEFORE_COMMIT, "备份文件在预览后发生变化，请重新预览")
        }
        val validation = try { validator.validate(opened.payload) } catch (e: RestoreException) {
            return@withLock RestoreRunResult(RestoreRunStatus.FAILED_BEFORE_COMMIT, e.message ?: "备份校验失败")
        }
        val attemptId = newAttemptId()
        val metaBefore = readMetaOrNull()
        val generation = maxOf((metaBefore?.restoreGeneration ?: 0L) + 1, 1L)
        try {
            snapshotStore.create(attemptId) // 快照失败：不做任何覆盖
        } catch (e: Exception) {
            return@withLock RestoreRunResult(RestoreRunStatus.FAILED_BEFORE_COMMIT, "保护快照生成失败：${e.message ?: "未知错误"}")
        }
        val gateState = RestoreGateState(attemptId, expectedBackupId, generation, RestoreGatePhase.ARMED, now())
        try {
            gate.write(gateState)
            _gateActive.value = true
            journal.write(RestoreJournalState(attemptId, expectedBackupId, generation,
                RestoreJournalPhase.PREPARED, now()))
        } catch (e: Exception) {
            gate.clear(); journal.clear(); _gateActive.value = false
            return@withLock RestoreRunResult(RestoreRunStatus.FAILED_BEFORE_COMMIT, "无法持久化恢复门禁")
        }
        val plan = RestorePlanBuilder.build(opened.payload, validation, generation, attemptId, now())
        try {
            transaction.apply(plan, now())
        } catch (e: Exception) {
            // 事务异常自动回滚；本次 attempt 未提交，清理 Gate/journal，旧数据保持。
            gate.clear(); journal.clear(); _gateActive.value = false
            writeQaStatus("FAILED_BEFORE_COMMIT", attemptId, generation)
            return@withLock RestoreRunResult(RestoreRunStatus.FAILED_BEFORE_COMMIT,
                "恢复事务失败，旧数据已保留：${e.message ?: "未知错误"}")
        }
        if (com.mengyalife.app.BuildConfig.QA_BUILD && QaRestoreFaultInjector.pauseAfterCommitMs > 0) {
            kotlinx.coroutines.delay(QaRestoreFaultInjector.pauseAfterCommitMs)
        }
        journal.write(RestoreJournalState(attemptId, expectedBackupId, generation,
            RestoreJournalPhase.DB_COMMITTED, now()))
        val committed = RestoreGateState(attemptId, expectedBackupId, generation, RestoreGatePhase.RESET_PENDING, now())
        gate.write(committed)
        return@withLock converge(committed, readMetaOrNull())
    }

    private suspend fun converge(state: RestoreGateState, meta: AppMetaEntity?): RestoreRunResult {
        try {
            gate.write(state.copy(phase = RestoreGatePhase.RESET_PENDING))
            journal.write(RestoreJournalState(state.attemptId, state.backupId, state.generation,
                RestoreJournalPhase.DB_COMMITTED, now()))
            consentRepository.resetAfterRestore(state.generation)
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
            journal.write(RestoreJournalState(state.attemptId, state.backupId, state.generation,
                RestoreJournalPhase.SETTINGS_APPLIED, now()))
            gate.write(state.copy(phase = RestoreGatePhase.READY_TO_RELEASE))
            journal.write(RestoreJournalState(state.attemptId, state.backupId, state.generation,
                RestoreJournalPhase.DONE, now()))
            gate.clear(); journal.clear(); _gateActive.value = false
            writeQaEvidence(state)
            _completionMessage.value = "恢复完成，已撤权并删除旧 Key"
            onSuccess("恢复完成，已撤权并删除旧 Key")
            return RestoreRunResult(RestoreRunStatus.SUCCESS, "恢复完成，已撤权并删除旧 Key")
        } catch (e: Exception) {
            _gateActive.value = true
            writeQaStatus("COMMITTED_NEEDS_CONVERGENCE", state.attemptId, state.generation)
            return RestoreRunResult(RestoreRunStatus.COMMITTED_NEEDS_CONVERGENCE,
                "数据库已提交，但安全收敛未完成；重启后将继续")
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

    private suspend fun readMetaOrNull(): AppMetaEntity? =
        runCatching { database.appMetaDao().get() }.getOrNull()
}
