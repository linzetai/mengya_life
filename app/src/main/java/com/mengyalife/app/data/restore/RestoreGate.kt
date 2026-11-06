package com.mengyalife.app.data.restore

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.FileOutputStream
import android.system.Os
import android.system.OsConstants

enum class RestoreGatePhase { ARMED, RESET_PENDING, SETTINGS_PENDING, READY_TO_RELEASE }

data class RestoreGateState(
    val attemptId: String,
    val backupId: String,
    val generation: Long,
    val phase: RestoreGatePhase,
    val createdAt: Long,
)

/** noBackupFilesDir/restore/gate.json；临时文件 + fsync + rename。 */
class RestoreGate(private val dir: File, private val syncParent: (File) -> Unit = ::syncParentDirectory) {
    private val file = File(dir, "gate.json")
    private val json = Json

    fun isActive(): Boolean = file.exists()

    fun read(): RestoreGateState? {
        if (!file.exists()) return null
        return runCatching {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            RestoreGateState(
                attemptId = root["attemptId"]?.jsonPrimitive?.contentOrNull ?: return null,
                backupId = root["backupId"]?.jsonPrimitive?.contentOrNull ?: return null,
                generation = root["generation"]?.jsonPrimitive?.longOrNull ?: return null,
                phase = RestoreGatePhase.valueOf(root["phase"]?.jsonPrimitive?.contentOrNull ?: return null),
                createdAt = root["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }.getOrNull()
    }

    fun write(state: RestoreGateState) {
        dir.mkdirs()
        val obj = buildJsonObject {
            put("attemptId", state.attemptId); put("backupId", state.backupId)
            put("generation", state.generation); put("phase", state.phase.name)
            put("createdAt", state.createdAt)
        }
        val bytes = obj.toString().toByteArray(Charsets.UTF_8)
        val tmp = File(dir, "gate.json.tmp")
        try {
            FileOutputStream(tmp).use { out -> out.write(bytes); out.flush(); out.fd.sync() }
            if (!tmp.renameTo(file)) throw RestoreException("GATE_WRITE", "无法持久化恢复门禁")
            syncParent(dir)
        } catch (e: RestoreException) {
            throw e
        } catch (e: Exception) {
            tmp.delete()
            throw RestoreException("GATE_WRITE", "无法持久化恢复门禁", e)
        }
    }

    fun clear() {
        if (!file.exists()) return
        if (!file.delete()) throw RestoreException("GATE_CLEAR", "无法删除恢复门禁")
        syncParent(dir)
    }

    companion object {
        private fun syncParentDirectory(dir: File) {
            val fd = Os.open(dir.absolutePath, OsConstants.O_RDONLY, 0)
            try { Os.fsync(fd) } finally { Os.close(fd) }
        }
    }
}
