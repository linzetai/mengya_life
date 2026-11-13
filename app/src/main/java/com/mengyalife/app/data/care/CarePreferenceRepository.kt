package com.mengyalife.app.data.care

import com.mengyalife.app.data.local.care.CarePreferenceDao
import com.mengyalife.app.data.local.care.CarePreferenceEntity
import com.mengyalife.app.data.restore.RestoreWriteBarrier
import kotlinx.coroutines.flow.Flow

class CarePreferenceRepository(
    private val dao: CarePreferenceDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val writeBarrier: RestoreWriteBarrier? = null,
) {
    private suspend fun <T> write(block: suspend () -> T): T =
        writeBarrier?.withWrite { block() } ?: block()

    suspend fun save(
        kind: String,
        topicKey: String?,
        topicLabel: String?,
        value: String,
        source: String,
        sourceMessageId: String?,
        sourceExcerpt: String?,
        windowNote: String? = null,
        expiresAt: Long? = null,
    ): Long = write {
        dao.insert(
            CarePreferenceEntity(
                kind = kind, topicKey = topicKey, topicLabel = topicLabel, value = value,
                source = source, sourceMessageId = sourceMessageId, sourceExcerpt = sourceExcerpt,
                windowNote = windowNote, effectiveAt = now(), expiresAt = expiresAt,
                revokedAt = null, createdAt = now(), updatedAt = now(),
            ),
        )
    }

    suspend fun active(): List<CarePreferenceEntity> = dao.active(now())
    suspend fun all(): List<CarePreferenceEntity> = dao.all()
    suspend fun revoke(id: Long) = write { dao.revoke(id, now()) }
    suspend fun revokeByTopic(key: String) = write { dao.revokeByTopic(key, now()) }
    suspend fun forTopic(key: String): CarePreferenceEntity? = dao.activeForTopic(key, now())
    suspend fun timePreference(): CarePreferenceEntity? = dao.activeTimePreference(now())
    suspend fun busy(): CarePreferenceEntity? = dao.activeBusy(now())
    fun observeActive(): Flow<List<CarePreferenceEntity>> = dao.observeActive(now())
    /** 只收敛超过 lease 的遗留 SENDING，避免误伤活跃任务。 */
    suspend fun reconcileSending(leaseMillis: Long = 10 * 60_000L): Int = write {
        dao.reconcileSending(now(), now() - leaseMillis)
    }
}
