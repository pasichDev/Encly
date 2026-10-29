package com.pasich.encly.core.security.cipher

import android.content.Context
import kotlinx.io.files.Path

/** The state of the app database called [dbName] in this app's database directory. */
fun SQLCipherUtils.getDatabaseState(context: Context, dbName: String?): SQLCipherUtils.State =
    getDatabaseState(Path(context.getDatabasePath(dbName).path))
