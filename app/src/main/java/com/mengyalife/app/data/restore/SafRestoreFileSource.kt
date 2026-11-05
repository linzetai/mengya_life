package com.mengyalife.app.data.restore

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** SAF ACTION_OPEN_DOCUMENT 的只读访问；不申请广泛存储权限。 */
interface RestoreFileSource {
    fun inspect(uri: String): RestoreFileInfo
    fun read(uri: String): ByteArray
}

class SafRestoreFileSource(private val context: Context) : RestoreFileSource {
    override fun inspect(uri: String): RestoreFileInfo {
        val parsed = Uri.parse(uri)
        var name = "backup.mengyalife"
        var size = 0L
        context.contentResolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !c.isNull(nameIndex)) name = c.getString(nameIndex)
                if (sizeIndex >= 0 && !c.isNull(sizeIndex)) size = c.getLong(sizeIndex)
            }
        }
        if (size <= 0) {
            size = runCatching {
                context.contentResolver.openFileDescriptor(parsed, "r")?.use { it.statSize }
            }.getOrNull() ?: 0L
        }
        if (size > BackupRestoreReader.MAX_FILE_BYTES) {
            throw RestoreException("FILE_TOO_LARGE", "备份文件超过 64 MiB")
        }
        return RestoreFileInfo(name, size)
    }

    override fun read(uri: String): ByteArray {
        val input = context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw RestoreException("FILE_READ", "无法读取所选文件")
        return input.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                total += n
                if (total > BackupRestoreReader.MAX_FILE_BYTES) {
                    throw RestoreException("FILE_TOO_LARGE", "备份文件超过 64 MiB")
                }
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
    }
}
