package com.mengyalife.app.backup

import com.mengyalife.app.data.backup.FileBackupTempStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class FileBackupTempStoreTest {
    @Test
    fun `delete throws when File delete really returns false`() {
        val dir = Files.createTempDirectory("g4a2a-temp").toFile()
        val store = FileBackupTempStore(dir)
        val ref = store.write(byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), store.read(ref))

        // 把临时文件替换成“非空目录”，File.delete() 必然返回 false。
        assertTrue(File(ref).delete())
        assertTrue(File(ref).mkdir())
        File(ref, "child").writeText("x")

        assertThrows(IOException::class.java) { store.delete(ref) }
        assertTrue(File(ref).exists())
        assertFalse(File(ref).delete())
        File(ref, "child").delete()
        File(ref).delete()
        dir.delete()
    }

    @Test
    fun `delete succeeds and removes a normal temp file`() {
        val dir = Files.createTempDirectory("g4a2a-temp-ok").toFile()
        val store = FileBackupTempStore(dir)
        val ref = store.write(byteArrayOf(9))
        store.delete(ref)
        assertFalse(File(ref).exists())
        dir.delete()
    }
}
