package com.digitscore.app.data.security

import android.database.sqlite.SQLiteDatabase as FrameworkSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherSQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseEncryptionMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val databaseName = "digitscore_encryption_migration_test"

    @Before
    fun createVersion3PlaintextDatabase() {
        context.deleteDatabase(databaseName)
        val database = FrameworkSQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(databaseName),
            null
        )
        database.execSQL(
            "CREATE TABLE user_settings (id INTEGER NOT NULL PRIMARY KEY, selectedPresetModeId TEXT NOT NULL)"
        )
        database.execSQL(
            "INSERT INTO user_settings (id, selectedPresetModeId) VALUES (1, 'balanced')"
        )
        database.version = 3
        database.close()
    }

    @After
    fun cleanup() {
        context.deleteDatabase(databaseName)
        context.getDatabasePath("$databaseName.plaintext.migration-backup").delete()
        context.getDatabasePath("$databaseName.encrypted.tmp").delete()
    }

    @Test
    fun plaintextUpgradeCanBeVerifiedRolledBackAndFinalized() {
        val passphrase = DatabaseEncryptionManager.prepare(context, databaseName)
        assertEncryptedDatabaseContainsSettings(passphrase)

        assertTrue(
            DatabaseEncryptionManager.restorePendingPlaintextBackup(
                context,
                databaseName,
                IllegalStateException("Room schema verification test failure")
            )
        )
        val restored = FrameworkSQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            FrameworkSQLiteDatabase.OPEN_READONLY
        )
        restored.rawQuery("SELECT selectedPresetModeId FROM user_settings WHERE id = 1", null).use {
            assertTrue(it.moveToFirst())
            assertEquals("balanced", it.getString(0))
        }
        restored.close()

        val retryPassphrase = DatabaseEncryptionManager.prepare(context, databaseName)
        assertEncryptedDatabaseContainsSettings(retryPassphrase)
        DatabaseEncryptionManager.finalizeSuccessfulOpen(context, databaseName)
        assertFalse(
            context.getDatabasePath("$databaseName.plaintext.migration-backup").exists()
        )
    }

    private fun assertEncryptedDatabaseContainsSettings(passphrase: ByteArray) {
        val database = CipherSQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            passphrase.toString(Charsets.UTF_8),
            null,
            CipherSQLiteDatabase.OPEN_READONLY,
            null
        )
        database.rawQuery("SELECT selectedPresetModeId FROM user_settings WHERE id = 1", emptyArray()).use {
            assertTrue(it.moveToFirst())
            assertEquals("balanced", it.getString(0))
        }
        database.close()
    }
}
