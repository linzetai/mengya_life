package com.mengyalife.app.data.backup

import kotlinx.serialization.json.JsonObject

/**
 * 数据收集 → 格式编码 → 校验 → 加密 的编排层。
 *
 * 不写文件、不碰 SAF；由 BackupFileWriter 负责落盘。
 */
class BackupExporter(
    private val snapshotSource: BackupSnapshotSource,
    private val settingsSource: BackupSettingsSource,
    private val appInfo: BackupAppInfo,
    private val enforceCryptoPolicy: Boolean = true,
    private val now: () -> Long = System::currentTimeMillis,
    private val newBackupId: () -> String = BackupFormatV2::newBackupId,
    private val newCryptoParams: () -> CryptoParams = { BackupCrypto.newParams() },
) {

    suspend fun build(passphrase: CharArray): BackupArtifact {
        if (passphrase.isEmpty()) throw BackupException("PASSPHRASE_EMPTY", "备份口令不能为空")
        val settingsBefore = settingsSource.load()
        val snapshot = snapshotSource.load()
        val settingsAfter = settingsSource.load()
        if (settingsBefore != settingsAfter) {
            throw BackupException("SETTINGS_CHANGED", "导出期间设置发生变化，请重试")
        }
        val createdAt = now()
        val backupId = newBackupId()
        val built = BackupFormatV2.encodePayload(snapshot, settingsAfter, appInfo, backupId, createdAt)
        val validation = BackupValidator.validatePayload(
            built.payload, built.payloadSha256, built.payloadBytes,
        )
        if (!validation.ok) {
            throw BackupException("PAYLOAD_INVALID", validation.errors.firstOrNull() ?: "备份数据校验失败")
        }
        if (built.payloadBytes.size > BackupValidator.MAX_PAYLOAD_BYTES) {
            throw BackupException("PAYLOAD_TOO_LARGE", "备份数据超过大小限制")
        }
        val params = newCryptoParams()
        if (enforceCryptoPolicy) BackupCryptoPolicy.validateProduction(params.iterations)
        val encrypted = BackupCrypto.encrypt(
            plaintext = built.payloadBytes,
            passphrase = passphrase,
            aad = BackupFormatV2.aad(built.payloadSha256, params),
            params = params,
            enforceProduction = enforceCryptoPolicy,
        )
        // 设置可能在加密期间变化；在返回前再核对一次（仍不能替代全局锁，但缩小窗口）。
        if (settingsSource.load() != settingsAfter) {
            throw BackupException("SETTINGS_CHANGED", "导出期间设置发生变化，请重试")
        }
        val bytes = BackupFormatV2.envelope(built.payloadSha256, encrypted, params)
        val sizeCheck = BackupValidator.validateEnvelopeSize(bytes)
        if (!sizeCheck.ok) {
            throw BackupException("FILE_TOO_LARGE", sizeCheck.errors.firstOrNull() ?: "备份文件过大")
        }
        return BackupArtifact(
            bytes = bytes,
            backupId = backupId,
            createdAt = createdAt,
            payloadSha256 = built.payloadSha256,
            counts = BackupCounts(snapshot.counts()),
            sizeBytes = bytes.size,
        )
    }

    /**
     * 回读校验：解密 → 重算 payload hash → 结构/计数/引用。
     * 不依赖“写入流没有抛异常”。
     */
    fun verify(bytes: ByteArray, passphrase: CharArray): JsonObject {
        val envelope = BackupFormatV2.parseEnvelope(bytes)
        val plaintext = BackupCrypto.decrypt(
            ciphertextWithTag = envelope.ciphertext,
            passphrase = passphrase,
            aad = BackupFormatV2.aad(envelope.payloadSha256, envelope.params),
            params = envelope.params,
            enforceProduction = enforceCryptoPolicy,
        )
        val actualPayloadSha = CanonicalJson.sha256Hex(plaintext)
        if (actualPayloadSha != envelope.payloadSha256) {
            throw BackupException("PAYLOAD_HASH", "备份载荷哈希不匹配")
        }
        if (plaintext.size > BackupValidator.MAX_PAYLOAD_BYTES) {
            throw BackupException("PAYLOAD_TOO_LARGE", "备份载荷超过大小限制")
        }
        val payload = BackupFormatV2.decodePayload(plaintext)
        val result = BackupValidator.validatePayload(payload, envelope.payloadSha256, plaintext)
        if (!result.ok) {
            throw BackupException("PAYLOAD_INVALID", result.errors.firstOrNull() ?: "备份文件校验失败")
        }
        return payload
    }
}
