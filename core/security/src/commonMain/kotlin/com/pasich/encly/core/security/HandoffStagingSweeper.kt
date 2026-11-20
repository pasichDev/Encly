package com.pasich.encly.core.security

/** Deletes what hand-offs of killed processes left in the app's cache (see `HandoffStaging.sweep`). */
public fun interface HandoffStagingSweeper {
    /** Deletes every staged hand-off file. */
    public fun sweep()
}
