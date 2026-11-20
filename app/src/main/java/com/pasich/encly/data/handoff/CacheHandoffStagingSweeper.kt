package com.pasich.encly.data.handoff

import android.content.Context
import com.pasich.encly.core.di.ApplicationContext
import com.pasich.encly.core.security.HandoffStagingSweeper
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.io.files.Path

/** [HandoffStagingSweeper] for the staging directory in the app's cache. */
@SingleIn(AppScope::class)
@Inject
class CacheHandoffStagingSweeper(@param:ApplicationContext private val context: Context) : HandoffStagingSweeper {
    override fun sweep() = HandoffStaging.sweep(Path(context.cacheDir.path, HandoffStaging.DIR_NAME))
}
