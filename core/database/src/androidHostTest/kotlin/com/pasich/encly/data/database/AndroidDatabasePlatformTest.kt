package com.pasich.encly.data.database

import android.app.Application
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.data.model.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The files of the vault are exactly where they were before the database moved into its own module:
 * `Context.getDatabasePath("database.db")` and `("vault_b.db")`. A vault written by an earlier
 * release is found only there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidDatabasePlatformTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val platform get() = AndroidDatabasePlatform(context)

    @Before
    fun setUp() {
        deleteFiles()
    }

    @After
    fun tearDown() {
        deleteFiles()
    }

    private fun deleteFiles() = VaultFile.entries.forEach {
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            File(context.getDatabasePath(it.fileName).path + suffix).delete()
        }
    }

    @Test
    fun theFilesKeepTheirNamesAndTheAppDatabaseDirectory() {
        assertEquals("database.db", File(platform.databasePath(VaultFile.PRIMARY).toString()).name)
        assertEquals("vault_b.db", File(platform.databasePath(VaultFile.DECOY).toString()).name)
        VaultFile.entries.forEach {
            assertEquals(
                context.getDatabasePath(it.fileName).absolutePath,
                platform.databasePath(it).toString(),
            )
        }
    }

    @Test
    fun theDatabaseDirectoryExistsForTheFirstRun() {
        val directory = File(platform.databasePath(VaultFile.PRIMARY).toString()).parentFile

        assertTrue(checkNotNull(directory).isDirectory)
    }

    @Test
    fun roomWritesTheFileWhereTheBareNameBuilderOfEarlierReleasesLookedForIt() = runBlocking {
        val path = platform.databasePath(VaultFile.PRIMARY)

        val db = VaultSchema.install(platform.databaseBuilder(path)).setDriver(AndroidSQLiteDriver()).build()
        try {
            db.notesDao().insertNote(Note(title = "written through the platform"))
        } finally {
            db.close()
        }
        assertTrue(context.getDatabasePath("database.db").exists())

        // The way it was opened before: Room resolves the bare file name itself.
        val earlier = VaultSchema.install(Room.databaseBuilder(context, AppDatabase::class.java, "database.db"))
            .setDriver(AndroidSQLiteDriver())
            .build()
        try {
            assertEquals(
                listOf("written through the platform"),
                earlier.notesDao().getAllNotes().first().map { it.title },
            )
        } finally {
            earlier.close()
        }
    }
}
