package com.mengyalife.app.restore

import com.mengyalife.app.data.restore.RestoreException
import com.mengyalife.app.data.restore.RestoreSnapshotStore
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RestoreSnapshotSpaceTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `missing restore dir probes existing parent`() {
        val parent = temp.newFolder("no-backup")
        val restoreDir = java.io.File(parent, "restore")
        assertFalse(restoreDir.exists())
        assertEquals(parent, RestoreSnapshotStore.spaceProbeDirectory(restoreDir))
        restoreDir.mkdir()
        assertEquals(restoreDir, RestoreSnapshotStore.spaceProbeDirectory(restoreDir))
    }

    @Test
    fun `preflight requires snapshot copy and safety margin`() {
        val required = RestoreSnapshotStore.requiredBytes(10L * 1024 * 1024)
        assertTrue(required >= 2 * 10L * 1024 * 1024 + RestoreSnapshotStore.MIN_MARGIN_BYTES)
        RestoreSnapshotStore.requireAvailableSpace(10L * 1024 * 1024, required)
        assertThrows(RestoreException::class.java) {
            RestoreSnapshotStore.requireAvailableSpace(10L * 1024 * 1024, required - 1)
        }
    }
}
