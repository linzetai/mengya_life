package com.mengyalife.app.restore

import com.mengyalife.app.data.restore.RestoreException
import com.mengyalife.app.data.restore.RestoreGate
import com.mengyalife.app.data.restore.RestoreGatePhase
import com.mengyalife.app.data.restore.RestoreGateState
import com.mengyalife.app.data.restore.RestoreJournal
import com.mengyalife.app.data.restore.RestoreJournalPhase
import com.mengyalife.app.data.restore.RestoreJournalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RestoreGateJournalTest {
    @Test
    fun `gate writes atomically reads back and clears`() {
        val dir = Files.createTempDirectory("gate").toFile()
        val gate = RestoreGate(dir) { }
        val state = RestoreGateState("a", "b", 2, RestoreGatePhase.ARMED, 1)
        gate.write(state)
        assertEquals(state, gate.read())
        assertTrue(gate.isActive())
        gate.clear()
        assertFalse(gate.isActive())
        dir.delete()
    }

    @Test
    fun `gate clear failure is not silent`() {
        val dir = Files.createTempDirectory("gate-fail").toFile()
        val gate = RestoreGate(dir) { }
        // 用非空目录占据 gate.json，File.delete() 必然 false。
        val file = File(dir, "gate.json")
        assertTrue(file.mkdir())
        File(file, "child").writeText("x")
        assertThrows(RestoreException::class.java) { gate.clear() }
        File(file, "child").delete(); file.delete(); dir.delete()
    }

    @Test
    fun `gate write failure is surfaced`() {
        val dir = Files.createTempDirectory("gate-write-fail").toFile()
        // 占据 tmp 路径为目录，rename 无法成功。
        assertTrue(File(dir, "gate.json.tmp").mkdir())
        val gate = RestoreGate(dir) { }
        assertThrows(RestoreException::class.java) {
            gate.write(RestoreGateState("a", "b", 1, RestoreGatePhase.ARMED, 1))
        }
        dir.delete()
    }

    @Test
    fun `journal write failure is surfaced`() {
        val dir = Files.createTempDirectory("journal-write-fail").toFile()
        assertTrue(File(dir, "journal.json.tmp").mkdir())
        val journal = RestoreJournal(dir) { }
        assertThrows(RestoreException::class.java) {
            journal.write(RestoreJournalState("a", "b", 1, RestoreJournalPhase.PREPARED, 1))
        }
        dir.delete()
    }

    @Test
    fun `journal round trips`() {
        val dir = Files.createTempDirectory("journal").toFile()
        val journal = RestoreJournal(dir) { }
        val state = RestoreJournalState("a", "b", 3, RestoreJournalPhase.DB_COMMITTED, 2)
        journal.write(state)
        assertEquals(state, journal.read())
        journal.clear()
        assertFalse(File(dir, "journal.json").exists())
        dir.delete()
    }
}
