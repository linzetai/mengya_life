package com.mengyalife.app.backup

import com.mengyalife.app.data.backup.BackupFileType
import com.mengyalife.app.data.backup.BackupFormatV2
import com.mengyalife.app.data.backup.BackupPostRestoreProjection
import com.mengyalife.app.data.backup.BackupValidator
import com.mengyalife.app.data.backup.CanonicalJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatV2Test {
    private val app = BackupTestFixtures.app()
    private val settings = BackupTestFixtures.settings()
    private val built = BackupFormatV2.encodePayload(
        BackupTestFixtures.snapshot(), settings, app, "b1", BackupTestFixtures.T,
    )

    @Test
    fun `full snapshot encodes all ten tables with counts and hashes`() {
        val result = BackupValidator.validatePayload(built.payload, built.payloadSha256, built.payloadBytes)
        assertTrue(result.errors.joinToString(), result.ok)
        assertEquals(17, built.perTableSha256.size)
        assertEquals(17, (built.payload["counts"] as JsonObject).size)
        val data = built.payload["data"]!!.jsonObject
        assertEquals(1, data["journalEntries"]!!.jsonArray.size)
        assertEquals(1, data["weeklyAiReviews"]!!.jsonArray.size)
        val weekly = data["weeklyAiReviews"]!!.jsonArray[0].jsonObject
        assertEquals(2, weekly["factIds"]!!.jsonArray.size)
        assertTrue(weekly["legacyFactIdsRaw"]!!.jsonPrimitive.content.contains("\u001f"))
    }

    @Test
    fun `empty snapshot is valid and every table is present`() {
        val empty = BackupFormatV2.encodePayload(
            BackupTestFixtures.empty(), settings, app, "b-empty", BackupTestFixtures.T,
        )
        assertTrue(BackupValidator.validatePayload(empty.payload, empty.payloadSha256).ok)
        assertEquals(17, (empty.payload["data"] as JsonObject).size)
        assertEquals("0", (empty.payload["counts"] as JsonObject)["journalEntries"]!!.jsonPrimitive.content)
    }

    @Test
    fun `unknown table feature and future version are rejected`() {
        val data = built.payload["data"]!!.jsonObject.toMutableMap()
        data["unknownTable"] = kotlinx.serialization.json.JsonArray(emptyList())
        val payloadWithTable = JsonObject(built.payload.toMutableMap() + ("data" to JsonObject(data)))
        assertFalse(BackupValidator.validatePayload(payloadWithTable).ok)

        val compatibility = built.payload["compatibility"]!!.jsonObject.toMutableMap()
        compatibility["requiredFeatures"] = kotlinx.serialization.json.buildJsonArray {
            add(JsonPrimitive("core")); add(JsonPrimitive("future-feature"))
        }
        val payloadWithFeature = JsonObject(built.payload.toMutableMap() + ("compatibility" to JsonObject(compatibility)))
        assertFalse(BackupValidator.validatePayload(payloadWithFeature).ok)

        val future = JsonObject(built.payload.toMutableMap() + ("backupFormatVersion" to JsonPrimitive(3)))
        assertFalse(BackupValidator.validatePayload(future).ok)
    }

    @Test
    fun `counts and per table hash mismatches are rejected`() {
        val counts = built.payload["counts"]!!.jsonObject.toMutableMap()
        counts["journalEntries"] = JsonPrimitive(99)
        val badCounts = JsonObject(built.payload.toMutableMap() + ("counts" to JsonObject(counts)))
        assertFalse(BackupValidator.validatePayload(badCounts).ok)

        val integrity = built.payload["integrity"]!!.jsonObject.toMutableMap()
        val hashes = integrity["perTableSha256"]!!.jsonObject.toMutableMap()
        hashes["journalEntries"] = JsonPrimitive("0".repeat(64))
        val badHash = JsonObject(
            built.payload.toMutableMap() + ("integrity" to JsonObject(integrity + ("perTableSha256" to JsonObject(hashes))))
        )
        assertFalse(BackupValidator.validatePayload(badHash).ok)
    }

    @Test
    fun `file size limit is testable without allocating the file`() {
        assertFalse(BackupValidator.validateEnvelopeSize(BackupValidator.MAX_FILE_BYTES + 1).ok)
        assertTrue(BackupValidator.validateEnvelopeSize(1024).ok)
    }

    @Test
    fun `post restore projection changes the hash and applies transformations`() {
        val analyzing = BackupFormatV2.encodePayload(
            BackupTestFixtures.snapshot(analyzing = true), settings, app, "b2", BackupTestFixtures.T,
        )
        val projected = BackupPostRestoreProjection.project(analyzing.payload)
        val originalHash = CanonicalJson.sha256Hex(analyzing.payload)
        val projectedHash = CanonicalJson.sha256Hex(projected)
        assertNotEquals(originalHash, projectedHash)
        assertEquals("FAILED", projected["data"]!!.jsonObject["journalEntries"]!!.jsonArray[0]
            .jsonObject["analysisStatus"]!!.jsonPrimitive.content)
        assertEquals(0, projected["data"]!!.jsonObject["weeklyAiReviews"]!!.jsonArray.size)
        assertTrue(projected["data"]!!.jsonObject["weeklyReviews"]!!.jsonArray[0]
            .jsonObject["notifiedAt"]!!.toString().contains("null"))
        assertTrue(BackupValidator.validatePayload(projected).ok)
    }

    @Test
    fun `v1 is recognized but not treated as v2`() {
        val v1 = """{"format":"mengyalife-backup","schemaVersion":1,"data":{}}""".toByteArray()
        assertEquals(BackupFileType.V1_LEGACY, BackupFormatV2.detect(v1))
        assertEquals(BackupFileType.V2_ENCRYPTED, BackupFormatV2.detect(
            BackupFormatV2.envelope(
                built.payloadSha256,
                com.mengyalife.app.data.backup.BackupCrypto.encrypt(
                    built.payloadBytes, "passphrase-123".toCharArray(),
                    BackupFormatV2.aad(built.payloadSha256, com.mengyalife.app.data.backup.CryptoParams(
                        1000, ByteArray(16), ByteArray(12),
                    )),
                    com.mengyalife.app.data.backup.CryptoParams(1000, ByteArray(16), ByteArray(12)),
                    enforceProduction = false,
                ),
                com.mengyalife.app.data.backup.CryptoParams(1000, ByteArray(16), ByteArray(12)),
            ),
        ))
        assertEquals(BackupFileType.UNKNOWN, BackupFormatV2.detect("{\"format\":\"other\"}".toByteArray()))
    }
}
