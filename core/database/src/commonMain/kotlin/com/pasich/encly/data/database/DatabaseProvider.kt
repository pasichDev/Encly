package com.pasich.encly.data.database

/**
 * One opening of the encrypted database. Identity, not its contents, grants DAO access: creating
 * one grants nothing, as [DatabaseProvider.getDatabase] accepts only the lease of the current opening.
 */
public class DatabaseLease

/** Supplies Room only to callers still bound to the opening in which they were created. */
public interface DatabaseProvider {
    /** Null while locked. The returned lease is revoked before Room closes. */
    public fun captureLease(): DatabaseLease?

    /**
     * Refuses a lease from an earlier opening, even when another database (the other vault's,
     * real or decoy) is now open.
     */
    public fun getDatabase(lease: DatabaseLease): AppDatabase
}
