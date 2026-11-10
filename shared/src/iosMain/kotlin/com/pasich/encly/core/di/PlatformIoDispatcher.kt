package com.pasich.encly.core.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal actual val platformIoDispatcher: CoroutineDispatcher = Dispatchers.IO
