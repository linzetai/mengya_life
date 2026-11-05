package com.mengyalife.app.data.backup

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * 纯文件实现（可 JVM 测试）：删除必须真正成功，`File.delete()` 返回 false 时抛错。
 * 不吞异常，避免“清理成功”误报。
 */
class FileBackupTempStore(private val dir: File) : BackupTempStore {

    override fun write(bytes: ByteArray): String {
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("无法创建备份临时目录")
        }
        val file = File(dir, "backup-${UUID.randomUUID()}.tmp")
        FileOutputStream(file).use { out ->
            out.write(bytes)
            out.flush()
            runCatching { out.fd.sync() }
        }
        return file.absolutePath
    }

    override fun read(ref: String): ByteArray = File(ref).readBytes()

    override fun delete(ref: String) {
        val file = File(ref)
        if (!file.exists()) return
        if (!file.delete()) {
            throw IOException("临时文件删除失败")
        }
    }
}
