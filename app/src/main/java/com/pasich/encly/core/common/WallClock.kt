package com.pasich.encly.core.common

import kotlin.time.Clock

/** Milliseconds since the Unix epoch, from the system clock (it can be set by the user; for dates, not intervals). */
fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
