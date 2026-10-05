package com.pasich.encly.data.handoff

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** [HandoffStagingSweeper] for the staging directory in the app's cache. */
@Singleton
class CacheHandoffStagingSweeper @Inject constructor(@param:ApplicationContext private val context: Context) :
    HandoffStagingSweeper {
    override fun sweep() = HandoffStaging.sweep(File(context.cacheDir, HandoffStaging.DIR_NAME))
}
