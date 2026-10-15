package com.pasich.encly.data.database

/** One opening of the encrypted database. Identity, not its contents, grants DAO access. */
class DatabaseLease internal constructor()

/** Supplies Room only to callers still bound to the opening in which they were created. */
interface DatabaseProvider {
    /** Null while locked. The returned lease is revoked before Room closes. */
    fun captureLease(): DatabaseLease?

    /**
     * Refuses a lease from an earlier opening, even when another database (the other vault's,
     * real or decoy) is now open.
     */
    fun getDatabase(lease: DatabaseLease): AppDatabase
}
