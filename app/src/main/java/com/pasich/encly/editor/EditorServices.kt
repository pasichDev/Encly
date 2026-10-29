package com.pasich.encly.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.pasich.encly.editor.model.Block
import com.pasich.encly.editor.theme.EditorTheme
import com.pasich.encly.editor.theme.LocalEditorTheme
import com.pasich.encly.editor.ui.blocks.LocalSlashLabels
import com.pasich.encly.editor.ui.blocks.rememberSlashLabels

/**
 * Puts text on the system clipboard. The editor never touches a platform clipboard itself, so
 * the host decides how long the text stays there and whether it is hidden from previews.
 */
fun interface EditorClipboard {
    /**
     * Copies [text], labelled [label]. [sensitive] is true for text the user wrote in a note (a
     * link's address): the host should keep it out of clipboard previews and clear it after a while.
     */
    fun copy(label: String, text: String, sensitive: Boolean)

    /** Copies nothing: the default when the host provides no clipboard. */
    companion object None : EditorClipboard {
        override fun copy(label: String, text: String, sensitive: Boolean) = Unit
    }
}

/**
 * Hands a note's link to the system. The editor decides whether a link may be opened at all
 * ([com.pasich.encly.editor.ui.blocks.isOpenableLink]); the host decides how, and tells the user
 * when nothing can take it.
 */
fun interface LinkOpener {
    /** Opens the web or mail link [url] in another app. */
    fun open(url: String)

    /** Opens nothing: the default when the host provides no opener. */
    companion object None : LinkOpener {
        override fun open(url: String) = Unit
    }
}

/**
 * Receives the editor's failure events. They are fixed descriptions, never note content or an
 * exception's message, so a host may forward them anywhere.
 */
fun interface EditorLogger {
    /** A recoverable problem: [event] says what failed in [tag]. */
    fun warn(tag: String, event: String)

    /** Logs nothing: the default when the host provides no logger. */
    companion object None : EditorLogger {
        override fun warn(tag: String, event: String) = Unit
    }
}

/**
 * Reads Markdown text pasted into a note as blocks (Smart Paste). [MarkdownBlockMapper]
 * (`editor.codec`) is the editor's own reader and the default; a host may pass another.
 */
fun interface MarkdownBlockParser {
    /**
     * [text] as blocks, nothing rewritten or dropped. [claim] may take a whole line as a block of
     * its own, before any other rule. With [plainFirstLine] the first line stays plain text whatever
     * it starts with, for a paste that continues a line.
     */
    fun parsePaste(text: String, plainFirstLine: Boolean, claim: (String) -> Block?): List<Block>
}

/** The strings the editor shows; English until the host provides its own. */
val LocalEditorStrings = staticCompositionLocalOf<EditorStrings> { EnglishEditorStrings }

/** Where the editor copies text to; see [EditorClipboard]. */
val LocalEditorClipboard = staticCompositionLocalOf<EditorClipboard> { EditorClipboard.None }

/** How the editor opens a link; see [LinkOpener]. */
val LocalLinkOpener = staticCompositionLocalOf<LinkOpener> { LinkOpener.None }

/** Where the editor reports failures; see [EditorLogger]. */
val LocalEditorLogger = staticCompositionLocalOf<EditorLogger> { EditorLogger.None }

/**
 * Provides everything the editor needs from its host above [content]: [strings], [theme], and the
 * [clipboard], [linkOpener] and [logger] its sheets and focus handling call. Each has an inert
 * default, so [content] may also be shown with only what the host cares about.
 */
@Composable
fun EditorProvider(
    strings: EditorStrings = EnglishEditorStrings,
    theme: EditorTheme = EditorTheme(),
    clipboard: EditorClipboard = EditorClipboard.None,
    linkOpener: LinkOpener = LinkOpener.None,
    logger: EditorLogger = EditorLogger.None,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalEditorStrings provides strings,
        LocalEditorTheme provides theme,
        LocalEditorClipboard provides clipboard,
        LocalLinkOpener provides linkOpener,
        LocalEditorLogger provides logger,
        LocalSlashLabels provides rememberSlashLabels(strings),
        content = content,
    )
}
