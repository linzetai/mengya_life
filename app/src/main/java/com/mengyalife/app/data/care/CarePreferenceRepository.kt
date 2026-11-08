package com.mengyalife.app.data.care

import com.mengyalife.app.data.local.care.CarePreferenceDao
import com.mengyalife.app.data.local.care.CarePreferenceEntity
import kotlinx.coroutines.flow.Flow

class CarePreferenceRepository(
    private val dao: CarePreferenceDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
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
    ): Long = dao.insert(
        CarePreferenceEntity(
            kind = kind, topicKey = topicKey, topicLabel = topicLabel, value = value,
            source = source, sourceMessageId = sourceMessageId, sourceExcerpt = sourceExcerpt,
            windowNote = windowNote, effectiveAt = now(), expiresAt = expiresAt,
            revokedAt = null, createdAt = now(), updatedAt = now(),
        ),
    )

    suspend fun active(): List<CarePreferenceEntity> = dao.active(now())
    suspend fun all(): List<CarePreferenceEntity> = dao.all()
    suspend fun revoke(id: Long) = dao.revoke(id, now())
    suspend fun revokeByTopic(key: String) = dao.revokeByTopic(key, now())
    suspend fun forTopic(key: String): CarePreferenceEntity? = dao.activeForTopic(key, now())
    suspend fun timePreference(): CarePreferenceEntity? = dao.activeTimePreference(now())
    suspend fun busy(): CarePreferenceEntity? = dao.activeBusy(now())
    fun observeActive(): Flow<List<CarePreferenceEntity>> = dao.observeActive(now())
    suspend fun reconcileSending(): Int = dao.reconcileSending(now())
}
