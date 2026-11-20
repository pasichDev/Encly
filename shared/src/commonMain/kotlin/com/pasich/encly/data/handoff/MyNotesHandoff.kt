package com.pasich.encly.data.handoff

import kotlinx.serialization.Serializable

/**
 * `handoff.json` from My Notes, contract v1 (pasichDev/Encly#46, pasichDev/MyNotes#167). Raw My
 * Notes data: Encly does all the mapping ([MyNotesHandoffMapper]), so its block format stays
 * private to Encly.
 *
 * Unknown keys are ignored inside schema 1. A nullable field may also be left out (a Java
 * serializer drops nulls by default); every other field is required.
 */
@Serializable
data class MyNotesHandoff(
    val format: String,
    val schema: Int,
    val exportedAt: Long,
    val tags: List<HandoffTag>,
    val notes: List<HandoffNote>,
    val taskCategories: List<HandoffTaskCategory>,
    val tasks: List<HandoffTask>,
) {
    companion object {
        const val FORMAT = "mynotes-handoff"
        const val SCHEMA = 1
    }
}

/** A user tag. Notes refer to it by [name]. */
@Serializable
data class HandoffTag(val id: String, val name: String, val position: Int)

/**
 * [value] is the plain text; [valueJson], when present, the Editor.js block array of an
 * extended note and the richer source. [tag] is a tag *name*. [attachments] counts the files
 * that were not sent and stay in My Notes.
 */
@Serializable
data class HandoffNote(
    val id: String,
    val title: String,
    val value: String,
    val valueJson: String? = null,
    val date: Long,
    val tag: String? = null,
    val isTrash: Boolean,
    val isPinned: Boolean,
    val attachments: Int,
)

/** A task category; it becomes an Encly tag. */
@Serializable
data class HandoffTaskCategory(val id: String, val name: String, val position: Int)

@Serializable
data class HandoffTask(
    val id: String,
    val description: String,
    val isDone: Boolean,
    val createdAt: Long,
    val categoryId: String? = null,
    val position: Int,
)

/** Why a hand-off was refused. [reason] is the contract's `reason` result extra. */
enum class HandoffError(val reason: String) {
    UNTRUSTED_CALLER("untrusted_caller"),
    CANCELLED("cancelled"),

    /** A newer `schema`: intact, but this Encly cannot read it ("update Encly"). */
    UNSUPPORTED_SCHEMA("unsupported_schema"),
    INVALID_PAYLOAD("invalid_payload"),
    TOO_LARGE("too_large"),
    FAILED("failed"),
}

class HandoffException(val error: HandoffError, cause: Throwable? = null) : Exception(error.reason, cause)
