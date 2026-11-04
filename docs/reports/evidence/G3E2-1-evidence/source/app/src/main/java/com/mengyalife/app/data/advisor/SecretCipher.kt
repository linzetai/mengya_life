package com.mengyalife.app.data.advisor

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 对称加密抽象。
 *
 * 生产实现使用 Android Keystore 里的 AES/GCM 密钥；单元测试使用可预测的假实现。
 * 接口故意只暴露「字符串进、字符串出」，避免上层误把明文密钥持久化。
 */
interface SecretCipher {
    /** @return Base64(iv):Base64(ciphertext) */
    fun encrypt(plaintext: String): String

    /** 解密失败（密钥被删除/失效/数据损坏）返回 null，不抛给 UI。 */
    fun decrypt(blob: String): String?

    /** 删除 Keystore 中的密钥，使旧密文永久不可解。 */
    fun deleteKey()
}

/**
 * Android Keystore 支撑的 AES/GCM 实现。
 *
 * - 密钥永远不出 Keystore（不可导出）；
 * - 每次加密使用随机 IV；
 * - 删除密钥时同时删除 Keystore 条目，旧密文无法再解。
 */
class AndroidKeystoreCipher(
    private val alias: String,
) : SecretCipher {

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        val store = keyStore()
        (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$iv:$body"
    }

    override fun decrypt(blob: String): String? = runCatching {
        val parts = blob.split(":", limit = 2)
        if (parts.size != 2) return null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val body = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(body), Charsets.UTF_8)
    }.getOrNull()

    override fun deleteKey() {
        runCatching {
            val store = keyStore()
            if (store.containsAlias(alias)) store.deleteEntry(alias)
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
