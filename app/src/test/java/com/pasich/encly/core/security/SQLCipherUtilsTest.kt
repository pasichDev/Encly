package com.pasich.encly.core.security

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.pasich.encly.core.security.cipher.SQLCipherUtils
import com.pasich.encly.core.security.cipher.SQLCipherUtils.State
import com.pasich.encly.core.security.cipher.getDatabaseState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The database file is classified by its header alone: missing, plaintext SQLite, or anything else. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SQLCipherUtilsTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()

    private fun dbFile(name: String): File = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }

    @Test
    fun aMissingFileDoesNotExist() {
        assertEquals(State.DOES_NOT_EXIST, SQLCipherUtils.getDatabaseState(context, "no-such.db"))
    }

    @Test
    fun aFileWithTheRealSqliteHeaderIsPlaintext() {
        dbFile("plain.db").writeBytes("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(100))

        assertEquals(State.UNENCRYPTED, SQLCipherUtils.getDatabaseState(context, "plain.db"))
    }

    @Test
    fun aFileWithAnyOtherHeaderIsTreatedAsEncrypted() {
        dbFile("cipher.db").writeBytes(ByteArray(4096) { (it * 31).toByte() })

        assertEquals(State.ENCRYPTED, SQLCipherUtils.getDatabaseState(context, "cipher.db"))
    }

    @Test
    fun aFileShorterThanTheMagicIsNotPlaintext() {
        dbFile("short.db").writeBytes("SQLite".toByteArray())

        assertEquals(State.ENCRYPTED, SQLCipherUtils.getDatabaseState(context, "short.db"))
    }

    @Test
    fun anUnreadablePathIsTreatedAsEncryptedNotAsPlaintext() {
        // A directory exists but cannot be read as a file: failing closed means "encrypted".
        dbFile("dir.db").also { it.mkdirs() }

        assertEquals(State.ENCRYPTED, SQLCipherUtils.getDatabaseState(context, "dir.db"))
    }
}
