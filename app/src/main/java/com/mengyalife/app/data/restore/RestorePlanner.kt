package com.mengyalife.app.data.restore

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 生成只读预览；没有 DAO 写入、没有 DataStore 写入。 */
class RestorePlanner(private val currentStateSource: RestoreCurrentStateSource) {
    suspend fun preview(
        opened: BackupRestoreReader.Opened,
        validation: RestoreValidator.Result,
        fileInfo: RestoreFileInfo,
    ): RestorePreview {
        val payload = opened.payload
        val counts = (payload["counts"] as? JsonObject) ?: throw RestoreException("COUNTS_MISSING", "缺少 counts")
        val backupCounts = RestoreTables.ALL.associateWith { key ->
            counts[key]?.jsonPrimitive?.intOrNull
                ?: throw RestoreException("COUNTS_MISSING", "缺少计数 $key")
        }
        val current = currentStateSource.counts()
        val tableCounts = RestoreTables.ALL.map { key ->
            RestoreTableCount(
                key = key,
                label = RestoreTables.LABELS[key] ?: key,
                backupCount = backupCounts[key] ?: 0,
                currentCount = current[key] ?: 0,
                willRestore = key in RestoreTables.RESTORED,
            )
        }
        return RestorePreview(
            backupId = payload["backupId"]?.jsonPrimitive?.content ?: "",
            createdAt = payload["createdAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            sourcePackage = validation.sourcePackage,
            sourceQa = validation.sourceQa,
            sourceVersionName = validation.sourceVersionName,
            sourceVersionCode = validation.sourceVersionCode,
            backupFormatVersion = payload["backupFormatVersion"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            dbSchemaVersion = validation.dbSchemaVersion,
            currentDbSchemaVersion = currentSchema,
            fileSizeBytes = fileInfo.sizeBytes,
            encrypted = true,
            kdfIterations = opened.kdfIterations,
            payloadSha256 = opened.payloadSha256,
            counts = tableCounts,
            warnings = validation.warnings,
            repairs = validation.repairs,
            notRestored = NOT_RESTORED,
            canExecute = false,
            executed = false,
        )
    }

    private val currentSchema: Int
        get() = com.mengyalife.app.data.local.DB_SCHEMA_VERSION

    companion object {
        val NOT_RESTORED = listOf(
            "API Key / encrypted_api_key / Keystore",
            "AI 授权同意与旧接收方",
            "AI 自动发送开关",
            "系统通知权限",
            "历史 weekly AI 结果（仅归档，不恢复）",
        )
    }
}
