package com.pasich.encly.data.handoff

import android.content.Context
import com.pasich.encly.core.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File

/** [HandoffStagingSweeper] for the staging directory in the app's cache. */
@SingleIn(AppScope::class)
@Inject
class CacheHandoffStagingSweeper(@param:ApplicationContext private val context: Context) : HandoffStagingSweeper {
    override fun sweep() = HandoffStaging.sweep(File(context.cacheDir, HandoffStaging.DIR_NAME))
}
