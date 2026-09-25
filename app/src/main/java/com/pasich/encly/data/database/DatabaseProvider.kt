package com.pasich.encly.data.database

/**
 * Supplies the Room instance currently backed by the unlocked encrypted vault.
 *
 * Keeping this contract separate from the SQLCipher lifecycle manager lets repository tests
 * exercise DAO error handling without loading the native SQLCipher library on the JVM.
 */
interface DatabaseProvider {
    fun getDatabase(): AppDatabase
}
