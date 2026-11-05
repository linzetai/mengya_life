package com.mengyalife.app.restore

import com.mengyalife.app.backup.BackupTestFixtures
import com.mengyalife.app.data.restore.BackupRestoreReader
import com.mengyalife.app.data.restore.RestoreFileInfo
import com.mengyalife.app.data.restore.RestorePlanner
import com.mengyalife.app.data.restore.RestoreValidator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorePlannerTest {
    @Test
    fun `preview shows backup and current counts and never enables execution`() = runTest {
        val reader = BackupRestoreReader(enforceCryptoPolicy = false)
        val opened = reader.open(RestoreTestSupport.artifact().bytes, RestoreTestSupport.PASS.toCharArray())
        val validation = RestoreValidator(BackupTestFixtures.app()).validate(opened.payload)
        val preview = RestorePlanner(RestoreTestSupport.Current()).preview(
            opened, validation, RestoreFileInfo("x", 123),
        )
        assertEquals(1, preview.counts.first { it.key == "journalEntries" }.backupCount)
        assertEquals(1, preview.counts.first { it.key == "journalEntries" }.currentCount)
        assertEquals(2, preview.counts.first { it.key == "goals" }.backupCount)
        assertTrue(preview.counts.filter { it.key != "weeklyAiReviews" }.all { it.willRestore })
        assertFalse(preview.counts.first { it.key == "weeklyAiReviews" }.willRestore)
        assertFalse(preview.canExecute)
        assertFalse(preview.executed)
        assertTrue(preview.backupTimeLabel.isNotBlank())
        assertTrue(preview.notRestored.any { it.contains("通知权限") })
    }
}
