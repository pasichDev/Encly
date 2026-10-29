# Encly editor

The block editor of Encly notes as a Kotlin Multiplatform library: a note is a list of blocks (paragraph,
headings, quote, bulleted / numbered / check lists, link card, separator), edited with Compose Multiplatform.

It knows nothing about Encly's vault, database, dependency injection or security policies. Everything it
needs from its host (texts, theme, clipboard, link opening, logging, Markdown reading) comes in through a
few small interfaces, so the same editor runs on Android, the desktop JVM and iOS.

| Target | Source sets | Checked in |
|---|---|---|
| Android (min SDK 26) | `commonMain`, `androidMain` | unit tests on the JVM under Robolectric (`androidHostTest`) |
| Desktop JVM | `commonMain`, `desktopMain` | `desktopTest` |
| iOS (`iosArm64`, `iosSimulatorArm64`) | `commonMain`, `iosMain` | compiled in CI on macOS; logic is shared, so it is tested on the JVM |

The module depends on Compose Multiplatform (runtime, foundation, ui, material3), `kotlinx-coroutines` and
`kotlinx-serialization-json`. It has no dependency on `:app` (a test fails if one is added) and no `java.`,
`android.` or `R` in `commonMain` (`EditorBoundaryTest`).

## Layout

`src/commonMain/kotlin/com/pasich/encly/editor/`

| Package | What is in it |
|---|---|
| `editor` | The extension points: `EditorProvider`, `EditorStrings` / `EditorString`, `EditorClipboard`, `LinkOpener`, `EditorLogger`, `MarkdownBlockParser` |
| `model` | `Block` (sealed: text, heading, quote, list, link, separator), `BlockType`, `BlockFactory`, the list and link payloads |
| `codec` | `BlockConverter` (blocks to / from the stored JSON; the format is frozen by golden tests), `MarkdownBlockMapper`, `InlineHtml`, `ImportBlocks` |
| `state` | `BlockEditorState`: the blocks, undo / redo, selection, find, slash picker and every edit operation, with no UI in it |
| `focus` | Focus and caret handling across blocks (`BlockFocusRegistry`) |
| `theme` | `EditorTheme` (spacing, font sizes and families, line spacing, icons) and `LocalEditorTheme` |
| `ui` | `EditorBlocksHost`, `editorBlocks` (the `LazyColumn` content), `EditorController`, the block composables and sheets |

## Public API

The public surface is small on purpose and is tracked in `api/` (Kotlin's built-in ABI validation): a change
that touches it fails `:editor:checkKotlinAbi` until `:editor:updateKotlinAbi` rewrites the dump and the
diff is reviewed. Explicit API mode is on, and detekt requires KDoc on every public declaration.

- **Content**: `Block` and its subclasses, `BlockConverter.blocksToJson` / `jsonToBlocksOrNull` for storage,
  `MarkdownBlockMapper`, `InlineHtml` and `ImportBlocks` for importers.
- **Editing**: `BlockEditorState` (`load`, `blocks`, `undo`, `redo`, `contentChanges`, `selection`, `find`,
  `slash`, ...) and the operations that take it (`applyTool`, `applyTemplate`, `applyShortcut`, `breakLine`, ...).
- **Showing it**: `EditorProvider` above, then `editorBlocks(host, blocks, ...)` inside the host's own
  `LazyColumn`. The `EditorBlocksHost` it takes bundles an `EditorController` (the host implements it on top of
  `BlockEditorState`), the focus registry, the find highlights and the callback that opens a block's sheet.

## Extension points

| Interface | What the host decides |
|---|---|
| `EditorStrings` | Every text the editor shows, by `EditorString` key. English is the default. |
| `EditorTheme` | Spacing, font sizes and families, line spacing, icons. The editor draws with Material 3 but reads its own tokens. |
| `EditorClipboard` | How text is copied. `sensitive = true` marks text the user wrote: keep it out of previews and clear it later. |
| `LinkOpener` | Opening a web or mail link. The editor only offers links that `isOpenableLink` accepts. |
| `EditorLogger` | Where recoverable failures go. Events are fixed descriptions, never note content. |
| `MarkdownBlockParser` | How pasted Markdown becomes blocks (Smart Paste). `MarkdownBlockMapper` is the default. |

Each has an inert default, so the editor can be shown with only what the host cares about.

## How Encly binds it

The app module (`app/.../presentation/screen/editnote/`) owns everything security- and platform-specific:

- `EditorBindings.kt`: `AppEditorProvider` maps the editor's string keys onto Android resources, builds the
  `EditorTheme` from the user's font and spacing settings, and passes the app's clipboard (sensitive text is
  flagged and wiped), link opener and logger.
- `EditNoteViewModelController`: implements `EditorController` on the note's `BlockEditorState`, and
  saves through `NotePersistence`.
- The keyboard privacy policy (no learning, secure text input), `FLAG_SECURE` and the clipboard wipe stay in
  the app; the editor never touches them.

## Platform code

| Declaration | android / desktop | iOS |
|---|---|---|
| `toAsciiHost` (`ui/blocks/AsciiHost.kt`): a link card shows an internationalized host in its ASCII (punycode) form, so it cannot imitate another site | `java.net.IDN` | the pure-Kotlin `Punycode` encoder (RFC 3492) |

Everything else is in `commonMain`. The two implementations are checked against the same reference names
(`PunycodeTest`).

## Build and test

```
./gradlew :editor:desktopTest :editor:testAndroidHostTest   # unit and Compose UI tests
./gradlew :editor:koverVerify :editor:koverVerifyEditorLogic # coverage gates
./gradlew :editor:detekt :editor:checkKotlinAbi             # static analysis, public API dump
./gradlew :editor:dokkaGeneratePublicationHtml              # API docs (build/dokka)
./gradlew :editor:compileKotlinIosSimulatorArm64            # macOS only
```

Coverage is gated at 100 % of lines for the whole module and 100 % of branches for the logic packages
(`model`, `codec`, `state`, `focus`, `theme`). The branches of `@Composable` functions are not gated: the Compose
compiler adds `$changed` / `$default` checks to every one of them that no test can take both ways.
Tests of pure logic are in `commonTest` (`kotlin.test`); Compose UI tests run under Robolectric in
`androidHostTest`.
