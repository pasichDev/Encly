package com.pasich.encly.data.database

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.pasich.encly.core.security.cipher.SQLCipherUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import net.sqlcipher.database.SupportFactory
import java.io.File
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the SQLCipher-encrypted Room database and its unlocked lifecycle.
 * The database is opened with a seed-derived passphrase; until unlocked, callers
 * must not persist real data through it.
 */
@Singleton
class SecureDatabaseManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private var database: AppDatabase? = null
    private var isUnlocked = false

    fun getDatabase(): AppDatabase {
        return if (isUnlocked && database != null) {
            Log.d(TAG, "Повертаємо реальну розблоковану базу")
            database!!
        } else {
            Log.w(TAG, "База ще не розблокована — повертаємо тимчасову in-memory базу")
            Room.inMemoryDatabaseBuilder(
                context.applicationContext, AppDatabase::class.java
            ).build()
        }
    }


    @Synchronized
    fun unlockDatabase(secretKey: SecretKey): Boolean {
        if (isUnlocked) {
            Log.d(TAG, "База вже розблокована")
            return true
        }


        return try {
            val rawPassphrase = secretKey.encoded
            val passphraseForCheck = rawPassphrase.copyOf()
            val passphraseForRoom = rawPassphrase.copyOf()
            Log.d(TAG, "Ключ отримано. Довжина: ${rawPassphrase.size} байт")

            val dbFile = context.getDatabasePath(DB_NAME)
            Log.d(TAG, "Шлях до бази: ${dbFile.absolutePath}")

            val state = SQLCipherUtils.getDatabaseState(context, DB_NAME)
            Log.d(TAG, "Стан бази перед розблокуванням: $state")



            if (state == SQLCipherUtils.State.ENCRYPTED) {
                Log.d(TAG, "База існує і зашифрована. Перевіряємо чи відкривається")
                if (!canOpenDatabase(passphraseForCheck)) {
                    Log.e(TAG, "Неможливо відкрити зашифровану базу з цим ключем")
                    return false
                }
            }

            if (state == SQLCipherUtils.State.DOES_NOT_EXIST) {
                Log.d(TAG, "База не існує. Створюємо нову зашифровану базу")
            }

            Log.d(TAG, "Ініціалізуємо Room з шифруванням")
            database = Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, DB_NAME
            ).openHelperFactory(SupportFactory(passphraseForRoom))
                .fallbackToDestructiveMigration(false).build()

            isUnlocked = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Не вдалося розблокувати базу", e)
            false
        }
    }

    private fun canOpenDatabase(passphrase: ByteArray): Boolean {
        return try {
            Log.d(TAG, "Перевірка відкриття бази через SupportFactory")

            val factory = SupportFactory(passphrase)
            val db = Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, DB_NAME
            ).openHelperFactory(factory).build()

            // Тригерим ініціалізацію
            db.openHelper.readableDatabase
            db.close()

            Log.d(TAG, "Базу відкрито успішно")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Помилка відкриття бази", e)
            false
        }
    }

    private fun deleteDatabaseFiles() {
        try {
            Log.d(TAG, "Видаляємо файли бази даних")
            val dbFile = context.getDatabasePath(DB_NAME)
            val wal = File(dbFile.absolutePath + "-wal")
            val shm = File(dbFile.absolutePath + "-shm")
            dbFile.delete()
            wal.delete()
            shm.delete()
            Log.d(TAG, "Файли бази даних успішно видалено")
        } catch (e: Exception) {
            Log.e(TAG, "Не вдалося видалити базу", e)
        }
    }

    fun reset() {
        Log.d(TAG, "Скидаємо базу")
        database?.close()
        database = null
        isUnlocked = false
        Log.d(TAG, "Базу скинуто")
    }

    companion object {
        private const val TAG = "SecureDatabaseManager"
        private const val DB_NAME = "database.db"
    }
}
