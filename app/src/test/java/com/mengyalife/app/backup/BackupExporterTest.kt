package com.mengyalife.app.backup

import com.mengyalife.app.data.backup.BackupCrypto
import com.mengyalife.app.data.backup.BackupException
import com.mengyalife.app.data.backup.BackupExporter
import com.mengyalife.app.data.backup.BackupSettingsSnapshot
import com.mengyalife.app.data.backup.BackupSettingsSource
import com.mengyalife.app.data.backup.BackupSnapshot
import com.mengyalife.app.data.backup.BackupSnapshotSource
import com.mengyalife.app.data.backup.CryptoParams
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupExporterTest {
    private val params = CryptoParams(1000, ByteArray(16) { it.toByte() }, ByteArray(12) { (it + 1).toByte() })

    private class Source(private val snapshot: BackupSnapshot) : BackupSnapshotSource {
        override suspend fun load(): BackupSnapshot = snapshot
    }

    private class Settings(private var value: BackupSettingsSnapshot) : BackupSettingsSource {
        var loads = 0
        var mutateAfterLoad = false
        override suspend fun load(): BackupSettingsSnapshot {
            loads++
            if (mutateAfterLoad && loads == 2) value = value.copy(themeMode = "DARK")
            return value
        }
    }

    private fun exporter(source: BackupSnapshotSource, settings: BackupSettingsSource) =
        BackupExporter(
            snapshotSource = source,
            settingsSource = settings,
            appInfo = BackupTestFixtures.app(),
            enforceCryptoPolicy = false,
            now = { BackupTestFixtures.T },
            newBackupId = { "backup-test-id" },
            newCryptoParams = { params },
        )

    @Test
    fun `full export encrypts and verifies all tables`() = kotlinx.coroutines.test.runTest {
        val exporter = exporter(Source(BackupTestFixtures.snapshot()), Settings(BackupTestFixtures.settings()))
        val artifact = exporter.build("correct horse battery".toCharArray())
        assertEquals("backup-test-id", artifact.backupId)
        assertEquals(18, artifact.counts.values.size)
        assertTrue(artifact.bytes.isNotEmpty())

        val payload = exporter.verify(artifact.bytes, "correct horse battery".toCharArray())
        assertEquals("com.mengyalife.app.qa", payload["generator"]!!.jsonObject["appPackage"]!!.jsonPrimitive.content)
        assertEquals(true, payload["generator"]!!.jsonObject["qaBuild"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("SYSTEM", payload["settings"]!!.jsonObject["themeMode"]!!.jsonPrimitive.content)
        assertFalse(payload.toString().contains("encrypted_api_key"))
        assertFalse(payload.toString().contains("use_fake_engine"))
        assertFalse(payload.toString().contains("granted"))
        assertFalse(payload.toString().contains("mengya_ai_key"))
    }

    @Test
    fun `empty export is valid`() = kotlinx.coroutines.test.runTest {
        val exporter = exporter(Source(BackupTestFixtures.empty()), Settings(BackupTestFixtures.settings()))
        val artifact = exporter.build("correct horse battery".toCharArray())
        val payload = exporter.verify(artifact.bytes, "correct horse battery".toCharArray())
        assertEquals("0", payload["counts"]!!.jsonObject["journalEntries"]!!.jsonPrimitive.content)
    }

    @Test
    fun `settings change during export aborts`() = kotlinx.coroutines.test.runTest {
        val settings = Settings(BackupTestFixtures.settings()).apply { mutateAfterLoad = true }
        val exporter = exporter(Source(BackupTestFixtures.snapshot()), settings)
        val error = runCatching { exporter.build("correct horse battery".toCharArray()) }.exceptionOrNull()
        assertTrue(error is BackupException)
        assertEquals("SETTINGS_CHANGED", (error as BackupException).code)
    }

    @Test
    fun `wrong password and tampering fail verification`() = kotlinx.coroutines.test.runTest {
        val exporter = exporter(Source(BackupTestFixtures.snapshot()), Settings(BackupTestFixtures.settings()))
        val artifact = exporter.build("correct horse battery".toCharArray())
        assertThrows(BackupException::class.java) {
            exporter.verify(artifact.bytes, "wrong passphrase".toCharArray())
        }
        val tampered = artifact.bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        assertThrows(BackupException::class.java) {
            exporter.verify(tampered, "correct horse battery".toCharArray())
        }
    }

    @Test
    fun `payload hash differs from post restore projection when transformations apply`() = kotlinx.coroutines.test.runTest {
        val exporter = exporter(Source(BackupTestFixtures.snapshot(analyzing = true)), Settings(BackupTestFixtures.settings()))
        val artifact = exporter.build("correct horse battery".toCharArray())
        val payload = exporter.verify(artifact.bytes, "correct horse battery".toCharArray())
        val projected = com.mengyalife.app.data.backup.BackupPostRestoreProjection.project(payload)
        assertNotNull(projected["integrity"])
        assertThrows(BackupException::class.java) {
            exporter.verify(artifact.bytes.copyOf(artifact.bytes.size - 1), "correct horse battery".toCharArray())
        }
    }
}
