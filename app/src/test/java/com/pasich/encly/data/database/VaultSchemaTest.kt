package com.pasich.encly.data.database

import android.content.Context
import androidx.room.RoomDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * The vault database must never fall back to dropping its tables: with no users' notes to
 * recover from anywhere else, a schema change without a migration has to fail, not wipe.
 * Room keeps these switches private, so they are read from the builder directly.
 */
class VaultSchemaTest {

    private val builder = VaultSchema.databaseBuilder(mock(Context::class.java), "vault-test.db")

    @Test
    fun missingMigrationsAreRequiredNotSilentlyDestructive() {
        assertTrue(builderFlag("requireMigration"))
        assertFalse(builderFlag("allowDestructiveMigrationForAllTables"))
        assertFalse(builderFlag("allowDestructiveMigrationOnDowngrade"))
    }

    private fun builderFlag(name: String): Boolean = RoomDatabase.Builder::class.java.getDeclaredField(name)
        .apply { isAccessible = true }
        .getBoolean(builder)
}
