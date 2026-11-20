package com.pasich.encly.testutil

import com.pasich.encly.core.platform.KeyValueStore

/**
 * A [KeyValueStore] for unit tests that keeps its values in a map. [edit] and [commit] apply a batch
 * at once, the way the Android adapter does, and [commit] always reports success.
 */
internal class InMemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, Any>()

    override fun getString(key: String, default: String?): String? = values[key] as? String ?: default

    override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default

    override fun getLong(key: String, default: Long): Long = values[key] as? Long ?: default

    override fun contains(key: String): Boolean = key in values

    override fun edit(block: KeyValueStore.Editor.() -> Unit) {
        MapEditor().apply(block).applyTo(values)
    }

    override fun commit(block: KeyValueStore.Editor.() -> Unit): Boolean {
        edit(block)
        return true
    }

    /** The changes of one batch, in the order they were made. */
    private class MapEditor : KeyValueStore.Editor {
        private val steps = mutableListOf<(MutableMap<String, Any>) -> Unit>()

        override fun putString(key: String, value: String) {
            steps += { it[key] = value }
        }

        override fun putBoolean(key: String, value: Boolean) {
            steps += { it[key] = value }
        }

        override fun putLong(key: String, value: Long) {
            steps += { it[key] = value }
        }

        override fun remove(key: String) {
            steps += { it.remove(key) }
        }

        override fun clear() {
            steps += { it.clear() }
        }

        fun applyTo(target: MutableMap<String, Any>) = steps.forEach { it(target) }
    }
}
