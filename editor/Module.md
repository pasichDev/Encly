# Module Encly editor

The block editor of Encly notes, as a Kotlin Multiplatform library for Android, the desktop JVM and iOS.
A note is a list of [Block][com.pasich.encly.editor.model.Block]s (paragraphs, headings, quotes, lists, link
cards, separators). The library keeps the blocks in a
[BlockEditorState][com.pasich.encly.editor.state.BlockEditorState] with undo, find and every edit
operation, shows them with Compose Multiplatform, and stores them as a stable JSON format
([BlockConverter][com.pasich.encly.editor.codec.BlockConverter]).

It depends on nothing of Encly. The host supplies texts, theme, clipboard, link opening and logging through
[EditorProvider][com.pasich.encly.editor.EditorProvider].

## Quick start

```kotlin
val state = remember { BlockEditorState() }

EditorProvider(strings = myStrings, theme = EditorTheme(), clipboard = myClipboard) {
    val host = EditorBlocksHost(
        controller = myController,          // an EditorController over `state`
        focusRegistry = rememberBlockFocusRegistry(state.blocks),
        highlights = FindHighlights(state.find.state.collectAsState()),
        onOpenSheet = { kind, block -> /* show the sheet of this block */ },
    )
    LazyColumn {
        editorBlocks(host, blocks = state.blocks, isLocked = false, editingLinkIds = emptySet())
    }
}
```

# Package com.pasich.encly.editor

The extension points a host implements: strings, clipboard, link opener, logger, Markdown reader, and
[EditorProvider][com.pasich.encly.editor.EditorProvider] that provides them.

# Package com.pasich.encly.editor.model

The block model: [Block][com.pasich.encly.editor.model.Block] and its payloads.

# Package com.pasich.encly.editor.codec

Reading and writing blocks: the stored JSON, Markdown, and the inline HTML of imported notes.

# Package com.pasich.encly.editor.state

[BlockEditorState][com.pasich.encly.editor.state.BlockEditorState] and the operations on it: structural edits,
list and text editing, selection, templates, slash commands, Smart Paste and find.

# Package com.pasich.encly.editor.focus

Focus and caret handling across the blocks of a note.

# Package com.pasich.encly.editor.theme

[EditorTheme][com.pasich.encly.editor.theme.EditorTheme]: the spacing, type and icon tokens the editor draws with.

# Package com.pasich.encly.editor.ui

The editor's Compose UI: the blocks host and its controller.

# Package com.pasich.encly.editor.ui.blocks

Composables and helpers of the individual blocks, and the note link rules.

# Package com.pasich.encly.editor.ui.sheets

The bottom sheets of a block (link actions, other actions).
