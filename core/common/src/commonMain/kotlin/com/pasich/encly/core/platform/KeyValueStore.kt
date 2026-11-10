package com.pasich.encly.core.platform

/**
 * Small non-secret flags that survive restarts (onboarding finished, last export time, auto-lock
 * delay, strict keyboard privacy). The platform decides where they live; on Android it is the
 * `encly_flags_v3` SharedPreferences file ([SharedPreferencesKeyValueStore]). Nothing secret
 * belongs here: it is plain storage.
 *
 * The two write forms keep the platform's two durability levels apart, because callers rely on
 * them: [edit] returns at once and persists in the background, [commit] returns only after the
 * change is persisted and says whether it was.
 */
public interface KeyValueStore {
    public fun getString(key: String, default: String?): String?

    public fun getBoolean(key: String, default: Boolean): Boolean

    public fun getLong(key: String, default: Long): Long

    public fun contains(key: String): Boolean

    /** Applies [block]'s changes in one batch; persisted in the background. */
    public fun edit(block: Editor.() -> Unit)

    /** Applies [block]'s changes in one batch and waits until they are persisted; false if they were not. */
    public fun commit(block: Editor.() -> Unit): Boolean

    /** The changes of one [edit] or [commit]. */
    public interface Editor {
        public fun putString(key: String, value: String)

        public fun putBoolean(key: String, value: Boolean)

        public fun putLong(key: String, value: Long)

        public fun remove(key: String)

        /** Removes every key. */
        public fun clear()
    }
}
