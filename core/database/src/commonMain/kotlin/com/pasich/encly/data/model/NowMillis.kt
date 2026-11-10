package com.pasich.encly.data.model

import kotlin.time.Clock

/**
 * Milliseconds since the Unix epoch from the system clock, the default dates of new notes and tasks.
 * Same value as `core.common.currentTimeMillis`; the module keeps its own copy so it depends on no
 * other Encly module.
 */
internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
