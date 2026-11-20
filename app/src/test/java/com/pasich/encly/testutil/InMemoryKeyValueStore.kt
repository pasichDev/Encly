package com.pasich.encly.testutil

import com.pasich.encly.core.platform.KeyValueStore
import com.pasich.encly.core.platform.SharedPreferencesKeyValueStore

/**
 * A [KeyValueStore] for unit tests: the real Android adapter over in-memory SharedPreferences, so
 * the tests of the security logic run through the same `apply`/`commit` code the app does.
 */
internal class InMemoryKeyValueStore : KeyValueStore by SharedPreferencesKeyValueStore(InMemorySharedPreferences())
