package com.mengyalife.app.data.restore

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream

enum class RestoreJournalPhase { PREPARED, DB_COMMITTED, SECURITY_RESET, SETTINGS_APPLIED, DONE }

data class RestoreJournalState(
    val attemptId: String,
    val backupId: String,
    val generation: Long,
    val phase: RestoreJournalPhase,
    val updatedAt: Long,
)

/** 仅辅助收敛；绝不作为 DB 是否提交的依据。 */
class RestoreJournal(private val dir: File) {
    private val file = File(dir, "journal.json")
    private val json = Json

    fun read(): RestoreJournalState? = runCatching {
        if (!file.exists()) null else {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            RestoreJournalState(
                root["attemptId"]?.jsonPrimitive?.contentOrNull ?: return null,
                root["backupId"]?.jsonPrimitive?.contentOrNull ?: return null,
                root["generation"]?.jsonPrimitive?.longOrNull ?: return null,
                RestoreJournalPhase.valueOf(root["phase"]?.jsonPrimitive?.contentOrNull ?: return null),
                root["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }
    }.getOrNull()

    fun write(state: RestoreJournalState) {
        dir.mkdirs()
        val obj = buildJsonObject {
            put("attemptId", state.attemptId); put("backupId", state.backupId)
            put("generation", state.generation); put("phase", state.phase.name)
            put("updatedAt", state.updatedAt)
        }
        val tmp = File(dir, "journal.json.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(obj.toString().toByteArray(Charsets.UTF_8)); out.flush()
            runCatching { out.fd.sync() }
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw RestoreException("JOURNAL_WRITE", "无法写恢复日志")
        }
    }

    fun clear() { runCatching { file.delete() } }
}
