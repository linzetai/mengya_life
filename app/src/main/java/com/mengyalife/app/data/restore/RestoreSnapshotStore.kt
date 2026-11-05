package com.mengyalife.app.data.restore

import com.mengyalife.app.data.advisor.SecretCipher
import com.mengyalife.app.data.backup.BackupExporter
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.Base64

/**
 * 恢复前保护快照：v2 加密备份 + 独立 Keystore alias 保护的随机口令。
 * 该 alias 与 AI API Key alias 不同，因此恢复后删除 API Key 不会使快照不可解。
 */
class RestoreSnapshotStore(
    private val dir: File,
    private val exporter: BackupExporter,
    private val keyCipher: SecretCipher,
) {
    private val random = SecureRandom()

    suspend fun create(attemptId: String): File {
        dir.mkdirs()
        val pass = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32).also(random::nextBytes))
        val chars = pass.toCharArray()
        try {
            val artifact = exporter.build(chars)
            // 写前回读验证；失败绝不进入 DB。
            exporter.verify(artifact.bytes, chars)
            val file = File(dir, "pre-restore-$attemptId.mengyalife")
            writeAtomic(file, artifact.bytes)
            writeAtomic(File(dir, "pre-restore-$attemptId.key"), keyCipher.encrypt(pass).toByteArray(Charsets.UTF_8))
            // 再读一次 key/文件，确认可重新打开。
            val reopened = readPassphrase(attemptId) ?: throw RestoreException("SNAPSHOT_KEY", "快照口令无法回读")
            try { exporter.verify(file.readBytes(), reopened) } finally { reopened.fill('\u0000') }
            return file
        } finally {
            chars.fill('\u0000')
        }
    }

    fun readPassphrase(attemptId: String): CharArray? {
        val keyFile = File(dir, "pre-restore-$attemptId.key")
        if (!keyFile.exists()) return null
        val plain = keyCipher.decrypt(keyFile.readText()) ?: return null
        return plain.toCharArray()
    }

    fun exists(attemptId: String): Boolean =
        File(dir, "pre-restore-$attemptId.mengyalife").exists() &&
            File(dir, "pre-restore-$attemptId.key").exists()

    fun readBytes(attemptId: String): ByteArray? {
        val file = File(dir, "pre-restore-$attemptId.mengyalife")
        return if (file.exists()) file.readBytes() else null
    }

    private fun writeAtomic(file: File, bytes: ByteArray) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(bytes); out.flush(); runCatching { out.fd.sync() }
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw RestoreException("SNAPSHOT_WRITE", "无法写入保护快照")
        }
    }
}
