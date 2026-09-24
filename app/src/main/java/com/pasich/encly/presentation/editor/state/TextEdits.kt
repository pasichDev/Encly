package com.pasich.encly.presentation.editor.state

/** One edit of a text as the range it replaced: at [start], [removed] became [inserted]. */
data class TextDiff(val start: Int, val removed: String, val inserted: String) {
    /** Where the inserted text ends in the new text. */
    val end: Int get() = start + inserted.length
}

/**
 * The edit that turned [old] into [new]. [cursor], the cursor after the edit, places a pure
 * insertion exactly: typing a line break next to an existing one is otherwise ambiguous. Without
 * it (or when it does not fit), the smallest differing range is taken.
 */
fun textDiff(old: String, new: String, cursor: Int = -1): TextDiff {
    if (isInsertionEndingAt(old, new, cursor)) {
        val start = cursor - (new.length - old.length)
        return TextDiff(start, removed = "", inserted = new.substring(start, cursor))
    }
    val max = minOf(old.length, new.length)
    var prefix = 0
    while (prefix < max && old[prefix] == new[prefix]) prefix++
    var suffix = 0
    while (suffix < max - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
    return TextDiff(prefix, old.substring(prefix, old.length - suffix), new.substring(prefix, new.length - suffix))
}

/** Whether [new] is [old] with text inserted just before [cursor]. */
private fun isInsertionEndingAt(old: String, new: String, cursor: Int): Boolean {
    val start = cursor - (new.length - old.length)
    val fits = new.length > old.length && start >= 0 && cursor <= new.length
    return fits && new.regionMatches(0, old, 0, start) && new.regionMatches(cursor, old, start, old.length - start)
}

/** What a typing edit did, for undo: typed, deleted, or replaced (autocorrect, a paste over a selection). */
enum class TypingKind { INSERT, DELETE, REPLACE }

/**
 * How one typing edit joins the undo history. Typing in one [field] (a block, or one list item)
 * stays one step while it keeps doing the same [kind] of edit, and a new word ([startsWord])
 * starts a new step: undo takes back the last word, not one letter or the whole session.
 */
data class TypingStep(val field: String, val kind: TypingKind, val startsWord: Boolean) {
    /** Whether this edit may join the step [previous] started. */
    fun continues(previous: TypingStep): Boolean =
        field == previous.field && kind == previous.kind && kind != TypingKind.REPLACE && !startsWord
}

/** The [TypingStep] of the edit from [old] to [new] in [field]. */
fun typingStep(field: String, old: String, new: String): TypingStep {
    val diff = textDiff(old, new)
    val kind = when {
        diff.removed.isEmpty() -> TypingKind.INSERT
        diff.inserted.isEmpty() -> TypingKind.DELETE
        else -> TypingKind.REPLACE
    }
    val startsWord = kind == TypingKind.INSERT &&
        !diff.inserted.first().isWhitespace() &&
        diff.start > 0 && old[diff.start - 1].isWhitespace()
    return TypingStep(field, kind, startsWord)
}

/**
 * A line break typed or pasted into a field that read [head] + the removed text + [tail]: the
 * field now reads [head], then [lines] joined by line breaks, then [tail]. A typed Enter is two
 * empty [lines].
 */
data class LineBreak(val head: String, val lines: List<String>, val tail: String) {
    init {
        require(lines.size >= 2) { "A line break makes at least two lines" }
    }

    /** Enter, as opposed to pasted text. */
    val isEnter: Boolean get() = lines.size == 2 && lines.all { it.isEmpty() }

    companion object {
        /** The line break [diff] made in a field that read [old]; null when it inserted none. */
        fun of(old: String, diff: TextDiff): LineBreak? {
            val inserted = normalizeLineBreaks(diff.inserted)
            if ('\n' !in inserted) return null
            return LineBreak(
                head = old.substring(0, diff.start),
                lines = inserted.split('\n'),
                tail = old.substring(diff.start + diff.removed.length),
            )
        }
    }
}
