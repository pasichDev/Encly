package com.pasich.encly.core.platform

import android.content.SharedPreferences

/**
 * [KeyValueStore] on Android's SharedPreferences: [KeyValueStore.edit] is `apply()` and
 * [KeyValueStore.commit] is `commit()`.
 */
class SharedPreferencesKeyValueStore(private val preferences: SharedPreferences) : KeyValueStore {
    override fun getString(key: String, default: String?): String? = preferences.getString(key, default)

    override fun getBoolean(key: String, default: Boolean): Boolean = preferences.getBoolean(key, default)

    override fun getLong(key: String, default: Long): Long = preferences.getLong(key, default)

    override fun contains(key: String): Boolean = preferences.contains(key)

    override fun edit(block: KeyValueStore.Editor.() -> Unit) {
        val editor = preferences.edit()
        PreferencesEditor(editor).block()
        editor.apply()
    }

    override fun commit(block: KeyValueStore.Editor.() -> Unit): Boolean {
        val editor = preferences.edit()
        PreferencesEditor(editor).block()
        return editor.commit()
    }

    private class PreferencesEditor(private val editor: SharedPreferences.Editor) : KeyValueStore.Editor {
        override fun putString(key: String, value: String) {
            editor.putString(key, value)
        }

        override fun putBoolean(key: String, value: Boolean) {
            editor.putBoolean(key, value)
        }

        override fun putLong(key: String, value: Long) {
            editor.putLong(key, value)
        }

        override fun remove(key: String) {
            editor.remove(key)
        }

        override fun clear() {
            editor.clear()
        }
    }
}
