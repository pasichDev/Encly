package com.pasich.encly.editor.ui

/**
 * What the bar over selected blocks does. The host owns the clipboard and the note, so it carries out
 * copy, cut and delete; [canEdit] is false where the note cannot change (locked, in the trash), and cut
 * and delete are then disabled while copy still works.
 */
public class SelectedBlocksActions(
    /** Whether cut and delete are offered. */
    public val canEdit: Boolean,
    /** Copy the selected blocks as plain text. */
    public val onCopy: () -> Unit,
    /** Copy the selected blocks as Markdown. */
    public val onCopyMarkdown: () -> Unit,
    /** Copy the selected blocks, then remove them. */
    public val onCut: () -> Unit,
    /** Remove the selected blocks. */
    public val onDelete: () -> Unit,
)
