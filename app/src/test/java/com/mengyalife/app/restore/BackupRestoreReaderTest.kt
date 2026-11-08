package com.mengyalife.app.restore

import com.mengyalife.app.backup.BackupTestFixtures
import com.mengyalife.app.data.backup.BackupAppInfo
import com.mengyalife.app.data.backup.CanonicalJson
import com.mengyalife.app.data.restore.BackupRestoreReader
import com.mengyalife.app.data.restore.RestoreException
import com.mengyalife.app.data.restore.RestoreFileInfo
import com.mengyalife.app.data.restore.RestorePlanner
import com.mengyalife.app.data.restore.RestoreValidator
import com.mengyalife.app.data.local.DB_SCHEMA_VERSION
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreReaderTest {
    private val reader = BackupRestoreReader(enforceCryptoPolicy = false)
    private val validator = RestoreValidator(BackupTestFixtures.app())
    private val pass = RestoreTestSupport.PASS.toCharArray()

    private fun error(block: () -> Unit): RestoreException =
        (runCatching(block).exceptionOrNull() as RestoreException)

    @Test
    fun `correct password opens and preview is read only`() = runTest {
        val artifact = RestoreTestSupport.artifact()
        val opened = reader.open(artifact.bytes, pass)
        val validation = validator.validate(opened.payload)
        val preview = RestorePlanner(RestoreTestSupport.Current()).preview(
            opened, validation, RestoreFileInfo("full.mengyalife", artifact.bytes.size.toLong()),
        )
        assertTrue(preview.canExecute)
        assertFalse(preview.executed)
        assertEquals(18, preview.counts.size)
        assertEquals("com.mengyalife.app.qa", preview.sourcePackage)
        assertTrue(preview.sourceQa)
        assertTrue(preview.notRestored.any { it.contains("API Key") })
        assertTrue(preview.notRestored.any { it.contains("weekly AI") })
        assertTrue(preview.counts.first { it.key == "weeklyAiReviews" }.let { !it.willRestore })
        assertEquals(0, preview.repairs.size)
        // payloadSha256 is independently checked by reader; canonical hash is stable.
        assertEquals(opened.payloadSha256, CanonicalJson.sha256Hex(opened.payload))
    }

    @Test
    fun `empty backup is valid`() = runTest {
        val artifact = RestoreTestSupport.artifact(BackupTestFixtures.empty())
        val opened = reader.open(artifact.bytes, pass)
        val preview = RestorePlanner(RestoreTestSupport.Current()).preview(
            opened, validator.validate(opened.payload), RestoreFileInfo("empty", artifact.bytes.size.toLong()),
        )
        assertTrue(preview.counts.all { it.backupCount == 0 })
    }

    @Test
    fun `wrong password truncation and tamper are rejected`() = runTest {
        val artifact = RestoreTestSupport.artifact()
        assertEquals("DECRYPT_FAILED", error { reader.open(artifact.bytes, "wrong".toCharArray()) }.code)
        val truncated = artifact.bytes.copyOf(artifact.bytes.size - 1)
        assertTrue(error { reader.open(truncated, pass) }.code.isNotBlank())
        val tampered = artifact.bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        assertTrue(error { reader.open(tampered, pass) }.code.isNotBlank())
    }

    @Test
    fun `v1 and unknown versions are not opened`() {
        val v1 = """{"format":"mengyalife-backup","schemaVersion":1,"data":{}}""".toByteArray()
        assertEquals("V1_UNSUPPORTED", error { reader.open(v1, pass) }.code)
        val unknown = """{"format":"mengyalife-backup","backupFormatVersion":3,"encryption":{},"payloadSha256":"${"0".repeat(64)}"}""".toByteArray()
        assertTrue(error { reader.open(unknown, pass) }.code.isNotBlank())
    }

    @Test
    fun `production crypto policy rejects low iteration backups`() = runTest {
        val artifact = RestoreTestSupport.artifact()
        val strict = BackupRestoreReader(enforceCryptoPolicy = true)
        assertEquals("KDF_PARAMS", error { strict.open(artifact.bytes, pass) }.code)
    }

    @Test
    fun `file size limit is enforced before reading`() {
        assertEquals("FILE_TOO_LARGE", error { reader.validateFileSize(BackupRestoreReader.MAX_FILE_BYTES + 1) }.code)
        assertEquals("FILE_EMPTY", error { reader.validateFileSize(0) }.code)
    }

    @Test
    fun `future schema and source mismatch are rejected`() = runTest {
        val artifact = RestoreTestSupport.artifact()
        val opened = reader.open(artifact.bytes, pass)
        val future = RestoreTestSupport.mutate(opened.payload) { map ->
            val compatibility = (map["compatibility"] as kotlinx.serialization.json.JsonObject).toMutableMap()
            compatibility["dbSchemaVersion"] = JsonPrimitive(DB_SCHEMA_VERSION + 1)
            map["compatibility"] = kotlinx.serialization.json.JsonObject(compatibility)
        }
        assertEquals("FUTURE_SCHEMA", error { validator.validate(future) }.code)

        val prodExpected = BackupAppInfo("com.mengyalife.app", false, "0.14.1", 1, 35)
        assertTrue(error { RestoreValidator(prodExpected).validate(opened.payload) }.code in setOf("SOURCE_PACKAGE", "SOURCE_QA"))
    }

    @Test
    fun `counts and per table hash are fail closed`() = runTest {
        val artifact = RestoreTestSupport.artifact()
        val opened = reader.open(artifact.bytes, pass)
        val badCounts = RestoreTestSupport.mutate(opened.payload) { map ->
            val counts = (map["counts"] as kotlinx.serialization.json.JsonObject).toMutableMap()
            counts["journalEntries"] = JsonPrimitive(99)
            map["counts"] = kotlinx.serialization.json.JsonObject(counts)
        }
        assertEquals("PAYLOAD_INVALID", error { validator.validate(badCounts) }.code)

        val badHash = RestoreTestSupport.mutateTable(opened.payload, "journalEntries") { rows ->
            rows[0]["body"] = JsonPrimitive("tampered")
        }.let { payload ->
            // restore original integrity exactly (mutateTable recomputes; force wrong hash)
            RestoreTestSupport.mutate(payload) { map ->
                val integrity = (map["integrity"] as kotlinx.serialization.json.JsonObject).toMutableMap()
                val hashes = (integrity["perTableSha256"] as kotlinx.serialization.json.JsonObject).toMutableMap()
                hashes["journalEntries"] = JsonPrimitive("0".repeat(64))
                integrity["perTableSha256"] = kotlinx.serialization.json.JsonObject(hashes)
                map["integrity"] = kotlinx.serialization.json.JsonObject(integrity)
            }
        }
        assertEquals("PAYLOAD_INVALID", error { validator.validate(badHash) }.code)
    }
}
