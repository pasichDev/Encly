package com.pasich.encly.utils

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Plaintext store of scheduled task reminders, kept OUTSIDE the encrypted database.
 *
 * With the auth gate the database is locked until the user authenticates, but alarms
 * can fire (and must be rescheduled after a reboot) while it is still locked. This
 * store therefore holds only the minimal reminder metadata needed to show/reschedule
 * a notification — id, title, optional description, trigger time — and never note
 * content. It also queues "completed from notification" actions to apply once the app
 * is unlocked.
 */
object ReminderStore {
    private const val PREFS = "reminders_store"
    private const val KEY_REMINDERS = "reminders"
    private const val KEY_PENDING_COMPLETE = "pending_complete"

    @Serializable
    data class ReminderRecord(
        val id: Long,
        val title: String,
        val description: String? = null,
        val triggerAt: Long
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
}
