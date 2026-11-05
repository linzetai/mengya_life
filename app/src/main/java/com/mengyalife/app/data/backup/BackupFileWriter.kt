package com.mengyalife.app.data.backup

import kotlinx.serialization.json.JsonObject

/** 应用私有临时文件（不参与系统备份）。 */
interface BackupTempStore {
    fun write(bytes: ByteArray): String
    fun read(ref: String): ByteArray
    fun delete(ref: String)
}

/** 用户选择的目标（SAF）；ref 是 uri.toString()，便于 JVM 用 fake 测试。 */
interface BackupDestination {
    fun write(ref: String, bytes: ByteArray)
    fun read(ref: String): ByteArray
    fun delete(ref: String): Boolean
}

data class BackupWriteResult(
    val ok: Boolean,
    val fileMayBeInvalid: Boolean = false,
    val errorCode: String? = null,
    val message: String? = null,
)

/**
 * 写临时文件 → 校验 → 写 SAF → 回读 → 解密/结构校验 → 清理。
 *
 * 成功条件不是“流没有异常”，而是回读后能通过完整解密与 payload 校验。
 * 只允许删除本次通过 CreateDocument 新建的目标；删除失败时明确告知文件可能无效。
 */
class BackupFileWriter(
    private val tempStore: BackupTempStore,
    private val destination: BackupDestination,
    private val verifier: (ByteArray, CharArray) -> JsonObject,
) {
    fun commit(ref: String, artifact: BackupArtifact, passphrase: CharArray): BackupWriteResult {
        var tempRef: String? = null
        var wroteDestination = false
        return try {
            tempRef = tempStore.write(artifact.bytes)
            val tempBytes = tempStore.read(tempRef)
            if (!tempBytes.contentEquals(artifact.bytes)) {
                return BackupWriteResult(false, errorCode = "TEMP_VERIFY", message = "临时文件校验失败")
            }
            verifier(tempBytes, passphrase)

            wroteDestination = true
            destination.write(ref, artifact.bytes)
            val readBack = destination.read(ref)
            if (!readBack.contentEquals(artifact.bytes)) {
                return failure(ref, "READBACK_MISMATCH", "回读内容与写入不一致")
            }
            verifier(readBack, passphrase)
            BackupWriteResult(true)
        } catch (e: BackupException) {
            failure(ref, e.code, e.message ?: "备份校验失败", wroteDestination)
        } catch (e: Exception) {
            failure(ref, "WRITE_FAILED", e.message ?: "无法写入所选位置", wroteDestination)
        } finally {
            tempRef?.let { runCatching { tempStore.delete(it) } }
        }
    }

    private fun failure(ref: String, code: String, message: String, attemptedWrite: Boolean = true): BackupWriteResult {
        var mayBeInvalid = false
        if (attemptedWrite) {
            val deleted = runCatching { destination.delete(ref) }.getOrDefault(false)
            mayBeInvalid = !deleted
        }
        return BackupWriteResult(
            ok = false,
            fileMayBeInvalid = mayBeInvalid,
            errorCode = code,
            message = if (mayBeInvalid) "$message；目标文件可能无效，请删除后重试" else message,
        )
    }
}
