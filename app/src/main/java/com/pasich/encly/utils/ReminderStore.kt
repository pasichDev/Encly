package com.pasich.encly.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Store of scheduled task reminders, kept OUTSIDE the SQLCipher database so alarms can
 * fire (and be rescheduled after a reboot) while the seed-locked database is unavailable.
 *
 * It holds only the minimal reminder metadata (id, title, optional description, trigger
 * time) plus a queue of "completed from notification" actions. Storage is
 * [EncryptedSharedPreferences] (Keystore-backed, independent of the seed) so task
 * titles/descriptions are encrypted at rest rather than sitting in plaintext prefs.
 */
object ReminderStore {
    private const val PREFS = "reminders_store_secure"
    private const val KEY_REMINDERS = "reminders"
    private const val KEY_PENDING_COMPLETE = "pending_complete"

    @Serializable
    data class ReminderRecord(
        val id: Long,
        val title: String,
        val description: String? = null,
        val triggerAt: Long
    )

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        return synchronized(this) {
            cachedPrefs ?: run {
                val appContext = context.applicationContext
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appContext,
                    PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also { cachedPrefs = it }
            }
        }
    }

    fun put(context: Context, record: ReminderRecord) {
        val byId = all(context).associateBy { it.id }.toMutableMap()
        byId[record.id] = record
        save(context, byId.values.toList())
    }

    fun remove(context: Context, id: Long) {
        save(context, all(context).filterNot { it.id == id })
    }

    fun all(context: Context): List<ReminderRecord> {
        val json = prefs(context).getString(KEY_REMINDERS, null) ?: return emptyList()
        return try {
            Json.decodeFromString(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Replaces all stored reminders with [records] in a single write. */
    fun replaceAll(context: Context, records: List<ReminderRecord>) {
        save(context, records)
    }

    private fun save(context: Context, records: List<ReminderRecord>) {
        prefs(context).edit { putString(KEY_REMINDERS, Json.encodeToString(records)) }
    }

    /** Queues a task id completed from a notification, to be applied once unlocked. */
    fun enqueueComplete(context: Context, id: Long) {
        val set = prefs(context).getStringSet(KEY_PENDING_COMPLETE, emptySet())
            .orEmpty().toMutableSet()
        set.add(id.toString())
        prefs(context).edit { putStringSet(KEY_PENDING_COMPLETE, set) }
    }

    /** Returns and clears the queued completions (call after unlock to reconcile the DB). */
    fun drainPendingComplete(context: Context): List<Long> {
        val set = prefs(context).getStringSet(KEY_PENDING_COMPLETE, emptySet()).orEmpty()
        prefs(context).edit { remove(KEY_PENDING_COMPLETE) }
        return set.mapNotNull { it.toLongOrNull() }
    }

    /** Removes all stored reminders and queued completions (used on a full data wipe). */
    fun clear(context: Context) {
        prefs(context).edit { clear() }
    }
}
