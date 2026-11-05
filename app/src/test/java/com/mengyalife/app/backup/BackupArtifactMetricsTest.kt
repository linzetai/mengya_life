package com.mengyalife.app.backup

import com.mengyalife.app.data.backup.BackupExporter
import com.mengyalife.app.data.backup.BackupSettingsSource
import com.mengyalife.app.data.backup.BackupSnapshotSource
import com.mengyalife.app.data.backup.CryptoParams
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/** 记录 JVM 合成样本的产物大小/构建耗时；不代表真机真实数据量。 */
class BackupArtifactMetricsTest {
    private object Source : BackupSnapshotSource {
        override suspend fun load() = BackupTestFixtures.snapshot()
    }
    private object Settings : BackupSettingsSource {
        override suspend fun load() = BackupTestFixtures.settings()
    }

    @Test
    fun metric() = runTest {
        val params = CryptoParams(310_000, ByteArray(16) { (it * 7).toByte() }, ByteArray(12) { (it * 5).toByte() })
        val exporter = BackupExporter(
            snapshotSource = Source,
            settingsSource = Settings,
            appInfo = BackupTestFixtures.app(),
            enforceCryptoPolicy = true,
            now = { BackupTestFixtures.T },
            newBackupId = { "metric-backup-id" },
            newCryptoParams = { params },
        )
        val start = System.nanoTime()
        val artifact = exporter.build("metric-passphrase-123".toCharArray())
        val ms = (System.nanoTime() - start) / 1_000_000
        println("G4A1_METRIC artifactBytes=${artifact.sizeBytes} buildMs=$ms records=${artifact.counts.total()}")
        assertTrue(artifact.sizeBytes > 0)
        assertTrue(artifact.counts.total() > 0)
    }
}
