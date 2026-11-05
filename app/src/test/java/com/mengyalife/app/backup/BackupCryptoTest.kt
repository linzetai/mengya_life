package com.mengyalife.app.backup

import com.mengyalife.app.data.backup.BackupCrypto
import com.mengyalife.app.data.backup.BackupCryptoPolicy
import com.mengyalife.app.data.backup.BackupException
import com.mengyalife.app.data.backup.CryptoParams
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val passphrase = "correct horse battery staple".toCharArray()
    private val salt = hex("000102030405060708090a0b0c0d0e0f")
    private val nonce = hex("101112131415161718191a1b")
    private val aad = "{\"backupFormatVersion\":2,\"format\":\"mengyalife-backup\",\"purpose\":\"test\"}"
        .toByteArray(Charsets.UTF_8)
    private val plaintext = "{\"hello\":\"mengya\"}".toByteArray(Charsets.UTF_8)
    private val params = CryptoParams(1000, salt, nonce)

    private fun hex(s: String): ByteArray =
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun bytesToHex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    @Test
    fun `fixed KDF AES GCM vector`() {
        val key = BackupCrypto.deriveKey(passphrase, salt, 1000)
        assertEquals("a69b179e3add3c1e0aaf227a0eb3aa2aa8645ab86fecf6ca00c17512697c719e", bytesToHex(key.encoded))

        val encrypted = BackupCrypto.encrypt(plaintext, passphrase, aad, params, enforceProduction = false)
        assertEquals(plaintext.size + 16, encrypted.ciphertextWithTag.size)
        val body = encrypted.ciphertextWithTag.copyOfRange(0, plaintext.size)
        val tag = encrypted.ciphertextWithTag.copyOfRange(plaintext.size, encrypted.ciphertextWithTag.size)
        assertEquals("67c5d9460baa9286504b5d18b4fa4a929757", bytesToHex(body))
        assertEquals("73b3c4cffa6ca1ee62c38bb3d010418a", bytesToHex(tag))

        assertArrayEquals(
            plaintext,
            BackupCrypto.decrypt(encrypted.ciphertextWithTag, passphrase, aad, params, enforceProduction = false),
        )
    }

    @Test
    fun `wrong password tamper and truncation are rejected`() {
        val encrypted = BackupCrypto.encrypt(plaintext, passphrase, aad, params, enforceProduction = false)
        assertThrows(BackupException::class.java) {
            BackupCrypto.decrypt(encrypted.ciphertextWithTag, "wrong password".toCharArray(), aad, params, false)
        }
        val tampered = encrypted.ciphertextWithTag.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertThrows(BackupException::class.java) {
            BackupCrypto.decrypt(tampered, passphrase, aad, params, false)
        }
        assertThrows(BackupException::class.java) {
            BackupCrypto.decrypt(encrypted.ciphertextWithTag.copyOf(encrypted.ciphertextWithTag.size - 1), passphrase, aad, params, false)
        }
    }

    @Test
    fun `production policy rejects low iterations and bad lengths`() {
        assertThrows(BackupException::class.java) { BackupCryptoPolicy.validateProduction(1000) }
        BackupCryptoPolicy.validateProduction(BackupCryptoPolicy.MIN_ITERATIONS)
        BackupCryptoPolicy.validateProduction(BackupCryptoPolicy.MAX_ITERATIONS)
        assertThrows(BackupException::class.java) {
            BackupCrypto.validateParams(CryptoParams(310_000, ByteArray(15), nonce), true)
        }
        assertThrows(BackupException::class.java) {
            BackupCrypto.validateParams(CryptoParams(310_000, salt, ByteArray(11)), true)
        }
    }

    @Test
    fun `empty passphrase is rejected`() {
        val e = assertThrows(BackupException::class.java) {
            BackupCrypto.encrypt(plaintext, CharArray(0), aad, params, enforceProduction = false)
        }
        assertTrue(e.code == "PASSPHRASE_EMPTY")
    }
}
