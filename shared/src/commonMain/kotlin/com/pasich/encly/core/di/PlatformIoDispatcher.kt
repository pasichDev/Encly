package com.pasich.encly.core.di

import kotlinx.coroutines.CoroutineDispatcher

/** The dispatcher for blocking work (disk, crypto, the database): `Dispatchers.IO`, which common code cannot name. */
internal expect val platformIoDispatcher: CoroutineDispatcher
