package com.pasich.encly.data.importer

internal actual fun Throwable.isOutOfMemory(): Boolean = this is OutOfMemoryError
