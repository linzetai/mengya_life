package com.mengyalife.app.restore

import com.mengyalife.app.backup.BackupTestFixtures
import com.mengyalife.app.data.backup.BackupArtifact
import com.mengyalife.app.data.backup.BackupExporter
import com.mengyalife.app.data.backup.BackupSettingsSource
import com.mengyalife.app.data.backup.BackupSnapshot
import com.mengyalife.app.data.backup.BackupSnapshotSource
import com.mengyalife.app.data.backup.CanonicalJson
import com.mengyalife.app.data.backup.CryptoParams
import com.mengyalife.app.data.restore.RestoreCurrentStateSource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

object RestoreTestSupport {
    const val PASS = "BackupFull123!"
    val params = CryptoParams(1000, ByteArray(16) { (it + 1).toByte() }, ByteArray(12) { (it + 3).toByte() })

    object Source : BackupSnapshotSource {
        override suspend fun load(): BackupSnapshot = BackupTestFixtures.snapshot()
    }
    object Settings : BackupSettingsSource {
        override suspend fun load() = BackupTestFixtures.settings()
    }

    suspend fun artifact(snapshot: BackupSnapshot = BackupTestFixtures.snapshot()): BackupArtifact {
        val exporter = BackupExporter(
            snapshotSource = object : BackupSnapshotSource { override suspend fun load() = snapshot },
            settingsSource = Settings,
            appInfo = BackupTestFixtures.app(),
            enforceCryptoPolicy = false,
            now = { BackupTestFixtures.T },
            newBackupId = { "restore-test-id" },
            newCryptoParams = { params },
        )
        return exporter.build(PASS.toCharArray())
    }

    fun mutateTable(payload: JsonObject, table: String, block: (MutableList<MutableMap<String, JsonElement>>) -> Unit): JsonObject {
        val data = payload["data"]!!.jsonObject.toMutableMap()
        val rows = data[table]!!.jsonArray.map { it.jsonObject.toMutableMap() }.toMutableList()
        block(rows)
        val array = JsonArray(rows.map { JsonObject(it) })
        data[table] = array
        val counts = payload["counts"]!!.jsonObject.toMutableMap()
        counts[table] = JsonPrimitive(rows.size)
        val integrity = payload["integrity"]!!.jsonObject.toMutableMap()
        val hashes = integrity["perTableSha256"]!!.jsonObject.toMutableMap()
        hashes[table] = JsonPrimitive(CanonicalJson.sha256Hex(array))
        integrity["perTableSha256"] = JsonObject(hashes)
        return JsonObject(payload.toMutableMap() + ("data" to JsonObject(data)) +
            ("counts" to JsonObject(counts)) + ("integrity" to JsonObject(integrity)))
    }

    fun mutate(payload: JsonObject, block: (MutableMap<String, kotlinx.serialization.json.JsonElement>) -> Unit): JsonObject {
        val map = payload.toMutableMap(); block(map); return JsonObject(map)
    }

    class Current(private val counts: Map<String, Int> = mapOf(
        "journalEntries" to 1, "goals" to 1, "goalCheckins" to 0, "advisorMessages" to 0,
        "goalProposals" to 0, "growthSignals" to 0, "growthMemories" to 0,
        "insightReports" to 0, "weeklyReviews" to 0, "weeklyAiReviews" to 0,
    )) : RestoreCurrentStateSource {
        override suspend fun counts() = counts
    }
}
