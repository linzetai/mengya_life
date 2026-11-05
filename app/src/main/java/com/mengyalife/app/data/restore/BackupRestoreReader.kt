package com.mengyalife.app.data.restore

import com.mengyalife.app.BuildConfig
import com.mengyalife.app.data.backup.BackupCrypto
import com.mengyalife.app.data.backup.BackupException
import com.mengyalife.app.data.backup.BackupFileType
import com.mengyalife.app.data.backup.BackupFormatV2
import com.mengyalife.app.data.backup.BackupValidator
import com.mengyalife.app.data.backup.CanonicalJson
import kotlinx.serialization.json.JsonObject

/**
 * 只读读取/解密/格式校验。没有 Context、DAO、DataStore 写入口。
 *
 * 生产默认 enforceCryptoPolicy=true，拒绝低于 210k 的迭代；JVM 向量测试可显式关闭。
 */
class BackupRestoreReader(
    private val enforceCryptoPolicy: Boolean = true,
) {
    data class Opened(
        val payload: JsonObject,
        val payloadSha256: String,
        val kdfIterations: Int,
        val fileSize: Int,
    )

    fun validateFileSize(size: Long) {
        if (size <= 0) throw RestoreException("FILE_EMPTY", "备份文件为空")
        if (size > MAX_FILE_BYTES) {
            throw RestoreException("FILE_TOO_LARGE", "备份文件超过 64 MiB")
        }
    }

    fun detect(bytes: ByteArray): BackupFileType = BackupFormatV2.detect(bytes)

    fun open(bytes: ByteArray, passphrase: CharArray): Opened {
        validateFileSize(bytes.size.toLong())
        when (val type = detect(bytes)) {
            BackupFileType.V1_LEGACY -> throw RestoreException(
                "V1_UNSUPPORTED",
                "检测到旧版 v1 导出；本阶段只支持 v2 完整加密备份，v1 不恢复",
            )
            BackupFileType.UNKNOWN -> throw RestoreException(
                "FORMAT_UNSUPPORTED",
                "不是可识别的萌芽人生 v2 备份文件",
            )
            BackupFileType.V2_ENCRYPTED -> Unit
        }
        val envelope = try {
            BackupFormatV2.parseEnvelope(bytes)
        } catch (e: BackupException) {
            throw RestoreException(e.code, e.message ?: "备份头无效", e)
        }
        val plaintext = try {
            BackupCrypto.decrypt(
                ciphertextWithTag = envelope.ciphertext,
                passphrase = passphrase,
                aad = BackupFormatV2.aad(envelope.payloadSha256, envelope.params),
                params = envelope.params,
                enforceProduction = enforceCryptoPolicy,
            )
        } catch (e: BackupException) {
            val code = if (e.code == "KDF_PARAMS") "KDF_PARAMS" else "DECRYPT_FAILED"
            val message = if (code == "KDF_PARAMS") "备份 KDF 参数超出允许范围，已拒绝" else "口令错误或备份文件损坏"
            throw RestoreException(code, message, e)
        }
        if (plaintext.size > BackupValidator.MAX_PAYLOAD_BYTES) {
            throw RestoreException("PAYLOAD_TOO_LARGE", "解密后载荷超过 48 MiB")
        }
        val actualSha = CanonicalJson.sha256Hex(plaintext)
        if (actualSha != envelope.payloadSha256) {
            throw RestoreException("PAYLOAD_HASH", "payloadSha256 不匹配")
        }
        val payload = try {
            BackupFormatV2.decodePayload(plaintext)
        } catch (e: BackupException) {
            throw RestoreException(e.code, e.message ?: "载荷不是合法 JSON", e)
        }
        val validation = BackupValidator.validatePayload(payload, envelope.payloadSha256, plaintext)
        if (!validation.ok) {
            throw RestoreException(
                "PAYLOAD_INVALID",
                "备份结构校验失败：" + validation.errors.take(8).joinToString("；"),
            )
        }
        return Opened(payload, envelope.payloadSha256, envelope.params.iterations, bytes.size)
    }

    companion object {
        const val MAX_FILE_BYTES = 64L * 1024 * 1024
        val EXPECTED_APP_INFO get() = Pair(BuildConfig.APPLICATION_ID, BuildConfig.QA_BUILD)
    }
}
