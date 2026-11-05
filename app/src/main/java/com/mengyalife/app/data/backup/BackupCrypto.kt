package com.mengyalife.app.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** 生产参数边界；测试向量可显式使用低迭代，但导出器必须走 production 校验。 */
object BackupCryptoPolicy {
    const val DEFAULT_ITERATIONS = 310_000
    const val MIN_ITERATIONS = 210_000
    const val MAX_ITERATIONS = 600_000
    const val SALT_BYTES = 16
    const val NONCE_BYTES = 12
    const val TAG_BITS = 128
    const val KEY_BITS = 256

    fun validateProduction(iterations: Int) {
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw BackupException(
                "KDF_PARAMS",
                "备份加密参数超出允许范围（迭代次数必须在 $MIN_ITERATIONS–$MAX_ITERATIONS）",
            )
        }
    }
}

/**
 * PBKDF2-HMAC-SHA256 + AES-256-GCM。
 *
 * 口令只存在于调用栈/CharArray；本类不缓存、不写日志。
 */
object BackupCrypto {
    private val random = SecureRandom()

    fun newParams(iterations: Int = BackupCryptoPolicy.DEFAULT_ITERATIONS): CryptoParams {
        val salt = ByteArray(BackupCryptoPolicy.SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(BackupCryptoPolicy.NONCE_BYTES).also(random::nextBytes)
        return CryptoParams(iterations, salt, nonce)
    }

    fun validateParams(params: CryptoParams, enforceProduction: Boolean) {
        if (params.salt.size != BackupCryptoPolicy.SALT_BYTES) {
            throw BackupException("CRYPTO_SALT", "salt 长度非法")
        }
        if (params.nonce.size != BackupCryptoPolicy.NONCE_BYTES) {
            throw BackupException("CRYPTO_NONCE", "nonce 长度非法")
        }
        if (params.iterations <= 0) {
            throw BackupException("KDF_PARAMS", "迭代次数必须为正")
        }
        if (enforceProduction) BackupCryptoPolicy.validateProduction(params.iterations)
    }

    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        if (passphrase.isEmpty()) throw BackupException("PASSPHRASE_EMPTY", "备份口令不能为空")
        val spec = PBEKeySpec(passphrase, salt, iterations, BackupCryptoPolicy.KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val encoded = factory.generateSecret(spec).encoded
            SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(
        plaintext: ByteArray,
        passphrase: CharArray,
        aad: ByteArray,
        params: CryptoParams,
        enforceProduction: Boolean = true,
    ): EncryptedPayload {
        validateParams(params, enforceProduction)
        val key = deriveKey(passphrase, params.salt, params.iterations)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(BackupCryptoPolicy.TAG_BITS, params.nonce))
            cipher.updateAAD(aad)
            val body = cipher.doFinal(plaintext)
            EncryptedPayload(body)
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("CRYPTO_ENCRYPT", "无法加密备份", e)
        }
    }

    fun decrypt(
        ciphertextWithTag: ByteArray,
        passphrase: CharArray,
        aad: ByteArray,
        params: CryptoParams,
        enforceProduction: Boolean = false,
    ): ByteArray {
        validateParams(params, enforceProduction)
        if (ciphertextWithTag.size < BackupCryptoPolicy.TAG_BITS / 8) {
            throw BackupException("CRYPTO_TRUNCATED", "备份文件被截断或损坏")
        }
        val key = deriveKey(passphrase, params.salt, params.iterations)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(BackupCryptoPolicy.TAG_BITS, params.nonce))
            cipher.updateAAD(aad)
            cipher.doFinal(ciphertextWithTag)
        } catch (e: Exception) {
            // 不区分“口令错/被篡改/被截断”，避免成为 oracle；上层可统一提示。
            throw BackupException("DECRYPT_FAILED", "口令错误或备份文件损坏", e)
        }
    }
}
