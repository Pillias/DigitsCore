package com.digitscore.app.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

/**
 * Room/SQLCipher 암호를 Android Keystore로 감싸 보관하고 기존 평문 SQLite DB를
 * 검증 가능한 임시 파일을 거쳐 암호화합니다. 키와 DB는 OS 백업 대상에서 제외합니다.
 */
object DatabaseEncryptionManager {
    private const val KEY_ALIAS = "digitscore_database_wrapping_key_v1"
    private const val KEY_FILE_NAME = "digitscore_database_key_v1.bin"
    private val KEY_FILE_MAGIC = "DCKEY001".toByteArray(Charsets.US_ASCII)
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    @Volatile
    private var nativeLibraryLoaded = false

    @Synchronized
    fun prepare(context: Context, databaseName: String): ByteArray {
        loadNativeLibrary()
        val databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()
        recoverInterruptedMigration(databaseFile)

        val keyFile = File(context.noBackupFilesDir, KEY_FILE_NAME)
        if (databaseFile.exists() && !isPlaintextDatabase(databaseFile) && !keyFile.exists()) {
            throw DatabaseEncryptionException(
                "암호화된 사용 기록의 기기 키를 찾을 수 없습니다. 앱 데이터를 삭제하거나 암호화 백업을 복원해야 합니다."
            )
        }

        val passphrase = if (keyFile.exists()) {
            decryptStoredPassphrase(keyFile)
        } else {
            createAndStorePassphrase(keyFile)
        }

        if (databaseFile.exists() && isPlaintextDatabase(databaseFile)) {
            migratePlaintextDatabase(databaseFile, passphrase)
        }
        return passphrase
    }

    private fun loadNativeLibrary() {
        if (!nativeLibraryLoaded) {
            System.loadLibrary("sqlcipher")
            nativeLibraryLoaded = true
        }
    }

    private fun createAndStorePassphrase(keyFile: File): ByteArray {
        val randomBytes = ByteArray(32).also(SecureRandom()::nextBytes)
        // SQLCipher ATTACH 바인딩과 Room factory가 동일하게 해석할 수 있는 UTF-8 암호를 사용합니다.
        val passphrase = Base64.encode(randomBytes, Base64.NO_WRAP)
        randomBytes.fill(0)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
        val encrypted = cipher.doFinal(passphrase)
        val payload = KEY_FILE_MAGIC + byteArrayOf(1) + cipher.iv + encrypted
        atomicWrite(keyFile, payload)
        return passphrase
    }

    private fun decryptStoredPassphrase(keyFile: File): ByteArray {
        try {
            val payload = keyFile.readBytes()
            require(payload.size > KEY_FILE_MAGIC.size + 1 + GCM_IV_BYTES + 16)
            require(payload.copyOfRange(0, KEY_FILE_MAGIC.size).contentEquals(KEY_FILE_MAGIC))
            require(payload[KEY_FILE_MAGIC.size].toInt() == 1)
            val ivStart = KEY_FILE_MAGIC.size + 1
            val iv = payload.copyOfRange(ivStart, ivStart + GCM_IV_BYTES)
            val encrypted = payload.copyOfRange(ivStart + GCM_IV_BYTES, payload.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getExistingWrappingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            return cipher.doFinal(encrypted)
        } catch (error: Exception) {
            throw DatabaseEncryptionException("기기 보안 키로 사용 기록을 열 수 없습니다.", error)
        }
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        getExistingWrappingKeyOrNull()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun getExistingWrappingKey(): SecretKey =
        getExistingWrappingKeyOrNull()
            ?: throw DatabaseEncryptionException("기기 보안 키가 삭제되었거나 무효화되었습니다.")

    private fun getExistingWrappingKeyOrNull(): SecretKey? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun migratePlaintextDatabase(databaseFile: File, passphrase: ByteArray) {
        val tempFile = File(databaseFile.parentFile, "${databaseFile.name}.encrypted.tmp")
        val backupFile = File(databaseFile.parentFile, "${databaseFile.name}.plaintext.migration-backup")
        deleteDatabaseFiles(tempFile)

        val passphraseText = passphrase.toString(Charsets.UTF_8)
        val source = SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        try {
            source.rawQuery("PRAGMA wal_checkpoint(FULL);", emptyArray()).use { cursor ->
                while (cursor.moveToNext()) Unit
            }
            val sourceVersion = source.version
            source.execSQL(
                "ATTACH DATABASE ? AS encrypted KEY ?;",
                arrayOf(tempFile.absolutePath, passphraseText)
            )
            source.rawExecSQL("SELECT sqlcipher_export('encrypted');")
            source.execSQL("PRAGMA encrypted.user_version = $sourceVersion")
            source.execSQL("DETACH DATABASE encrypted;")
        } catch (error: Exception) {
            deleteDatabaseFiles(tempFile)
            throw DatabaseEncryptionException("기존 사용 기록을 암호화하는 중 오류가 발생했습니다.", error)
        } finally {
            source.close()
        }

        verifyEncryptedDatabase(tempFile, passphraseText)
        deleteDatabaseFiles(backupFile)
        // 평문 WAL/SHM이 새 암호화 DB와 같은 이름으로 재사용되지 않도록 먼저 제거합니다.
        deleteAuxiliaryFiles(databaseFile)
        check(databaseFile.renameTo(backupFile)) { "평문 DB 백업 파일을 만들 수 없습니다." }
        try {
            check(tempFile.renameTo(databaseFile)) { "암호화 DB를 적용할 수 없습니다." }
            verifyEncryptedDatabase(databaseFile, passphraseText)
            deleteDatabaseFiles(backupFile)
        } catch (error: Exception) {
            deleteDatabaseFiles(databaseFile)
            backupFile.renameTo(databaseFile)
            throw DatabaseEncryptionException("암호화 DB 교체에 실패하여 기존 기록을 복구했습니다.", error)
        }
    }

    private fun verifyEncryptedDatabase(file: File, passphrase: String) {
        require(file.exists() && file.length() > 0L) { "암호화 DB 파일이 생성되지 않았습니다." }
        require(!isPlaintextDatabase(file)) { "DB가 평문 상태로 남아 있습니다." }
        val database = SQLiteDatabase.openDatabase(
            file.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READWRITE,
            null
        )
        try {
            database.rawQuery("PRAGMA integrity_check", emptyArray()).use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)) {
                    "암호화 DB 무결성 검증에 실패했습니다."
                }
            }
            database.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='user_settings'",
                emptyArray()
            ).use { cursor ->
                require(cursor.moveToFirst() && cursor.getInt(0) == 1) { "필수 테이블이 누락되었습니다." }
            }
        } finally {
            database.close()
        }
    }

    private fun recoverInterruptedMigration(databaseFile: File) {
        val tempFile = File(databaseFile.parentFile, "${databaseFile.name}.encrypted.tmp")
        val backupFile = File(databaseFile.parentFile, "${databaseFile.name}.plaintext.migration-backup")
        when {
            databaseFile.exists() -> {
                deleteDatabaseFiles(tempFile)
                if (backupFile.exists()) deleteDatabaseFiles(backupFile)
            }
            backupFile.exists() -> {
                deleteDatabaseFiles(tempFile)
                check(backupFile.renameTo(databaseFile)) { "중단된 DB 이전을 복구할 수 없습니다." }
            }
            else -> deleteDatabaseFiles(tempFile)
        }
    }

    private fun isPlaintextDatabase(file: File): Boolean {
        if (!file.exists() || file.length() < SQLITE_HEADER.size) return false
        return file.inputStream().use { input ->
            val header = ByteArray(SQLITE_HEADER.size)
            input.read(header) == header.size && header.contentEquals(SQLITE_HEADER)
        }
    }

    private fun deleteAuxiliaryFiles(databaseFile: File) {
        File("${databaseFile.absolutePath}-wal").delete()
        File("${databaseFile.absolutePath}-shm").delete()
        File("${databaseFile.absolutePath}-journal").delete()
    }

    private fun deleteDatabaseFiles(databaseFile: File) {
        databaseFile.delete()
        deleteAuxiliaryFiles(databaseFile)
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(temp).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        check(temp.renameTo(target)) { "보안 키 파일을 저장할 수 없습니다." }
    }
}

class DatabaseEncryptionException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
