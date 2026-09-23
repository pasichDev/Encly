package com.pasich.encly.testutil

import android.content.SharedPreferences

@Suppress("TooManyFunctions") // Android's SharedPreferences requires this complete test-only interface implementation.
internal class InMemorySharedPreferences : SharedPreferences {

    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = values.toMap()

    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        @Suppress("UNCHECKED_CAST")
        ((values[key] as? Set<String>)?.toSet() ?: defValues)

    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor(values)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit

    private class Editor(private val values: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val updates = mutableMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = update(key, value)

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor =
            update(key, values?.toSet())

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = update(key, value)

        override fun putLong(key: String, value: Long): SharedPreferences.Editor = update(key, value)

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = update(key, value)

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = update(key, value)

        override fun remove(key: String): SharedPreferences.Editor = update(key, null)

        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }

        override fun commit(): Boolean {
            if (clearRequested) values.clear()
            updates.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
            return true
        }

        override fun apply() {
            commit()
        }

        private fun update(key: String, value: Any?): SharedPreferences.Editor {
            updates[key] = value
            return this
        }
    }
}
