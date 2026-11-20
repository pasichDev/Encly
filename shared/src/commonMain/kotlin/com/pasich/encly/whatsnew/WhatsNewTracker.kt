package com.pasich.encly.whatsnew

import com.pasich.encly.core.platform.KeyValueStore
import com.pasich.encly.platform.AppInfo
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Remembers the version the app last started as, so the first unlock after an update can show "What's new" once.
 *
 * A fresh install records its version at once ([recordFreshInstall]) and never shows it. An install that predates
 * this has nothing recorded: it is an update, so it is shown the notes of the version it just got.
 */
@SingleIn(AppScope::class)
@Inject
class WhatsNewTracker(private val flags: KeyValueStore, private val appInfo: AppInfo) {

    /** A new install starts on this version, so there is nothing new to tell. Does nothing if one is recorded. */
    fun recordFreshInstall() {
        if (flags.getString(LAST_SEEN_KEY, null) == null) record()
    }

    /**
     * True once per version: when this version is newer than the one recorded (or none is recorded, an install
     * from before this existed). The version is recorded at once, so the next call is false.
     */
    fun takeUnseen(): Boolean = takeUpdate() != null

    /**
     * Like [takeUnseen], but tells which version the update came from: null when nothing is due (same version, a
     * downgrade or a fresh install), else the [VersionUpdate]. The version is recorded at once, so the next call
     * is null.
     */
    fun takeUpdate(): VersionUpdate? {
        val last = flags.getString(LAST_SEEN_KEY, null)
        val current = appInfo.versionName
        if (last == current) return null
        record()
        return if (last == null || isNewer(current, last)) VersionUpdate(last) else null
    }

    private fun record() = flags.edit { putString(LAST_SEEN_KEY, appInfo.versionName) }

    /** An update to this version from [previous]; null when nothing was recorded (an install from before this). */
    class VersionUpdate(val previous: String?) {
        /**
         * True when the install came from a version below 3.0.0, which did not delete trashed notes after 28 days. A
         * version that is not `MAJOR.MINOR.PATCH` is not counted: its trash is not known to be new to the person.
         */
        val fromBeforeTrashRetention: Boolean
            get() = previous == null || code(previous)?.let { it < TRASH_RETENTION_VERSION_CODE } == true
    }

    private companion object {
        /** 3.0.0, the first version that empties the trash on its own. */
        const val TRASH_RETENTION_VERSION_CODE = 30_000L
        const val LAST_SEEN_KEY = "whats_new_last_seen_version"
        val VERSION = Regex("""^(\d+)\.(\d+)\.(\d+)""")

        /** A name that is not `MAJOR.MINOR.PATCH` counts as different, hence new. */
        fun isNewer(current: String, last: String): Boolean {
            val now = code(current)
            val before = code(last)
            return if (now == null || before == null) true else now > before
        }

        /** The version code (`MAJOR * 10000 + MINOR * 100 + PATCH`) of a name, with a suffix too (`2.3.1-debug`). */
        @Suppress("MagicNumber") // The weights of MAJOR.MINOR.PATCH, as in version.properties.
        fun code(name: String): Long? {
            val parts = VERSION.find(name)?.groupValues?.drop(1)?.map { it.toLong() } ?: return null
            return parts[0] * 10_000 + parts[1] * 100 + parts[2]
        }
    }
}
