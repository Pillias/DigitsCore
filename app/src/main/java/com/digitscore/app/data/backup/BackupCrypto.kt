package com.digitscore.app.data.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** 기기 간 복원이 가능한 사용자 비밀번호 기반 AES-256-GCM 백업 형식입니다. */
object BackupCrypto {
    private val MAGIC = "DCOREBK1".toByteArray(Charsets.US_ASCII)
    private const val FORMAT_VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 310_000
    private const val HEADER_BYTES = 8 + 1 + 4 + SALT_BYTES + IV_BYTES

    fun isEncryptedBackup(bytes: ByteArray): Boolean =
        bytes.size >= HEADER_BYTES + 16 && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun encrypt(plainBytes: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 8) { "백업 비밀번호는 8자 이상이어야 합니다." }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC)
            .put(FORMAT_VERSION)
            .putInt(PBKDF2_ITERATIONS)
            .put(salt)
            .put(iv)
            .array()
        val key = deriveKey(password, salt, PBKDF2_ITERATIONS)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(header)
            header + cipher.doFinal(plainBytes)
        } finally {
            key.encoded?.fill(0)
        }
    }

    fun decrypt(encryptedBytes: ByteArray, password: CharArray): ByteArray {
        require(isEncryptedBackup(encryptedBytes)) { "DigitsCore 암호화 백업 파일이 아닙니다." }
        require(password.isNotEmpty()) { "백업 비밀번호를 입력하세요." }
        val buffer = ByteBuffer.wrap(encryptedBytes)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        require(magic.contentEquals(MAGIC))
        require(buffer.get() == FORMAT_VERSION) { "지원하지 않는 백업 암호화 버전입니다." }
        val iterations = buffer.int
        require(iterations in 100_000..2_000_000) { "올바르지 않은 키 파생 설정입니다." }
        val salt = ByteArray(SALT_BYTES).also(buffer::get)
        val iv = ByteArray(IV_BYTES).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val header = encryptedBytes.copyOfRange(0, HEADER_BYTES)
        val key = deriveKey(password, salt, iterations)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(header)
            cipher.doFinal(ciphertext)
        } catch (error: Exception) {
            throw IllegalArgumentException("비밀번호가 다르거나 백업 파일이 손상되었습니다.", error)
        } finally {
            key.encoded?.fill(0)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
            SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }
}
