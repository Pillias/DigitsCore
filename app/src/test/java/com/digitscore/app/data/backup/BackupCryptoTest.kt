package com.digitscore.app.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCryptoTest {
    @Test
    fun `encrypted backup round trips with correct password`() {
        val original = "{\"private\":\"usage history\"}".toByteArray()
        val password = "correct horse battery staple".toCharArray()
        val encrypted = BackupCrypto.encrypt(original, password)

        assertTrue(BackupCrypto.isEncryptedBackup(encrypted))
        assertFalse(encrypted.toString(Charsets.UTF_8).contains("usage history"))
        assertArrayEquals(original, BackupCrypto.decrypt(encrypted, password))
    }

    @Test
    fun `wrong password cannot decrypt backup`() {
        val encrypted = BackupCrypto.encrypt("private".toByteArray(), "password-one".toCharArray())
        try {
            BackupCrypto.decrypt(encrypted, "password-two".toCharArray())
            fail("Wrong password must not decrypt")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `tampered backup fails authentication`() {
        val password = "strong-password".toCharArray()
        val encrypted = BackupCrypto.encrypt("private".toByteArray(), password)
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        try {
            BackupCrypto.decrypt(encrypted, password)
            fail("Tampered backup must not decrypt")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `four character password works`() {
        val original = "{\"test\":123}".toByteArray()
        val password = "1234".toCharArray()
        val encrypted = BackupCrypto.encrypt(original, password)
        assertTrue(BackupCrypto.isEncryptedBackup(encrypted))
        assertArrayEquals(original, BackupCrypto.decrypt(encrypted, password))
    }

    @Test
    fun `less than 4 character password fails`() {
        try {
            BackupCrypto.encrypt("test".toByteArray(), "123".toCharArray())
            fail("Password shorter than 4 must fail")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
