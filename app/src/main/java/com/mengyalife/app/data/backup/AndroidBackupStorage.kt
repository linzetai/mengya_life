package com.mengyalife.app.data.backup

import android.content.Context
import com.mengyalife.app.BuildConfig
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** 真实临时文件：noBackupFilesDir/backup-tmp，fsync 后返回绝对路径。 */
class AndroidBackupTempStore(private val context: Context) : BackupTempStore {
    private val dir: File by lazy {
        File(context.noBackupFilesDir, "backup-tmp").apply { mkdirs() }
    }

    override fun write(bytes: ByteArray): String {
        dir.mkdirs()
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
        if (BuildConfig.QA_BUILD && QaBackupFaultInjector.failTempCleanup) {
            throw java.io.IOException("QA temp cleanup failure")
        }
        runCatching { File(ref).delete() }
    }
}

/** SAF 目标；只在用户通过 CreateDocument 新建的文件上调用 delete。 */
class SafBackupDestination(private val context: Context) : BackupDestination {
    private val resolver get() = context.contentResolver

    override fun write(ref: String, bytes: ByteArray) {
        val uri = Uri.parse(ref)
        val stream = resolver.openOutputStream(uri, "wt")
            ?: throw BackupException("SAF_OPEN", "无法打开所选保存位置")
        stream.use { out ->
            val payload = when {
                BuildConfig.QA_BUILD && QaBackupFaultInjector.failTargetWrite -> bytes.copyOf(minOf(8, bytes.size))
                BuildConfig.QA_BUILD && QaBackupFaultInjector.truncateTargetWrite -> bytes.copyOf(maxOf(0, bytes.size - 1))
                else -> bytes
            }
            out.write(payload)
            out.flush()
            runCatching {
                (out as? FileOutputStream)?.fd?.sync()
            }
            if (BuildConfig.QA_BUILD && QaBackupFaultInjector.failTargetWrite) {
                throw java.io.IOException("QA target write failure")
            }
        }
    }

    override fun read(ref: String): ByteArray {
        if (BuildConfig.QA_BUILD && QaBackupFaultInjector.failTargetRead) {
            throw java.io.IOException("QA target read failure")
        }
        val uri = Uri.parse(ref)
        val input = resolver.openInputStream(uri)
            ?: throw BackupException("SAF_READ", "无法回读备份文件")
        return input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            val output = java.io.ByteArrayOutputStream()
            var total = 0
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                total += n
                if (total > BackupValidator.MAX_FILE_BYTES + 1) {
                    throw BackupException("SAF_TOO_LARGE", "备份文件超过大小限制")
                }
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        }
    }

    override fun delete(ref: String): Boolean {
        if (BuildConfig.QA_BUILD && QaBackupFaultInjector.failTargetDelete) return false
        return runCatching {
            val uri = Uri.parse(ref)
        if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.deleteDocument(resolver, uri)
        } else {
            false
            }
        }.getOrDefault(false)
    }
}
