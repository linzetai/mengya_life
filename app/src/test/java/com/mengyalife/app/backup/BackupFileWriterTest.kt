package com.mengyalife.app.backup

import com.mengyalife.app.data.backup.BackupArtifact
import com.mengyalife.app.data.backup.BackupCounts
import com.mengyalife.app.data.backup.BackupDestination
import com.mengyalife.app.data.backup.BackupFileWriter
import com.mengyalife.app.data.backup.BackupTempStore
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFileWriterTest {
    private class Temp : BackupTempStore {
        val files = mutableMapOf<String, ByteArray>()
        override fun write(bytes: ByteArray): String {
            val ref = "/tmp/${files.size}"
            files[ref] = bytes.copyOf()
            return ref
        }
        override fun read(ref: String): ByteArray = files[ref] ?: error("missing temp")
        override fun delete(ref: String) { files.remove(ref) }
    }

    private class Dest(
        var failWrite: Boolean = false,
        var failReadback: Boolean = false,
        var deleteResult: Boolean = true,
    ) : BackupDestination {
        val files = mutableMapOf<String, ByteArray>()
        var deleted = false
        override fun write(ref: String, bytes: ByteArray) {
            if (failWrite) error("disk full")
            files[ref] = if (failReadback) bytes.copyOf(bytes.size - 1) else bytes.copyOf()
        }
        override fun read(ref: String): ByteArray = files[ref] ?: error("missing")
        override fun delete(ref: String): Boolean {
            deleted = true
            files.remove(ref)
            return deleteResult
        }
    }

    private val artifact = BackupArtifact(
        bytes = ByteArray(32) { it.toByte() },
        backupId = "b", createdAt = 1, payloadSha256 = "a".repeat(64),
        counts = BackupCounts(mapOf()), sizeBytes = 32,
    )
    private val pass = "passphrase-123".toCharArray()
    private val verifier: (ByteArray, CharArray) -> JsonObject = { bytes, _ ->
        if (bytes.size != 32) error("bad")
        JsonObject(emptyMap())
    }

    @Test
    fun `success requires readback verification and cleans temp`() {
        val temp = Temp(); val dest = Dest()
        val result = BackupFileWriter(temp, dest, verifier).commit("content://new/file", artifact, pass)
        assertTrue(result.ok)
        assertEquals(1, dest.files.size)
        assertTrue(temp.files.isEmpty())
        assertFalse(dest.deleted)
    }

    @Test
    fun `write failure deletes new target and reports invalidity when deletion fails`() {
        val temp = Temp(); val dest = Dest(failWrite = true, deleteResult = false)
        val result = BackupFileWriter(temp, dest, verifier).commit("content://new/file", artifact, pass)
        assertFalse(result.ok)
        assertTrue(result.fileMayBeInvalid)
        assertTrue(dest.deleted)
        assertTrue(temp.files.isEmpty())
    }

    @Test
    fun `readback mismatch is not success`() {
        val temp = Temp(); val dest = Dest(failReadback = true)
        val result = BackupFileWriter(temp, dest, verifier).commit("content://new/file", artifact, pass)
        assertFalse(result.ok)
        assertEquals("READBACK_MISMATCH", result.errorCode)
        assertTrue(temp.files.isEmpty())
    }

    @Test
    fun `verifier failure prevents success`() {
        val temp = Temp(); val dest = Dest()
        val result = BackupFileWriter(temp, dest) { _, _ -> error("bad crypto") }
            .commit("content://new/file", artifact, pass)
        assertFalse(result.ok)
        assertTrue(temp.files.isEmpty())
    }
}
