package com.pasich.encly.presentation.screen.editnote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pasich.encly.core.AppLogger
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.block_bulleted_list
import com.pasich.encly.core.resources.block_checklist
import com.pasich.encly.core.resources.block_duplicate
import com.pasich.encly.core.resources.block_heading
import com.pasich.encly.core.resources.block_heading_level
import com.pasich.encly.core.resources.block_link
import com.pasich.encly.core.resources.block_list
import com.pasich.encly.core.resources.block_move_down
import com.pasich.encly.core.resources.block_move_up
import com.pasich.encly.core.resources.block_numbered_list
import com.pasich.encly.core.resources.block_separator
import com.pasich.encly.core.resources.checklist_lock
import com.pasich.encly.core.resources.checklist_unlock
import com.pasich.encly.core.resources.delete_block
import com.pasich.encly.core.resources.enter_url
import com.pasich.encly.core.resources.falied_content
import com.pasich.encly.core.resources.link_add
import com.pasich.encly.core.resources.link_blocked
import com.pasich.encly.core.resources.link_copy
import com.pasich.encly.core.resources.link_edit
import com.pasich.encly.core.resources.link_open
import com.pasich.encly.core.resources.list_item_placeholder
import com.pasich.encly.core.resources.more_options
import com.pasich.encly.core.resources.press_to_edit
import com.pasich.encly.core.resources.quote
import com.pasich.encly.core.resources.slash_menu_title
import com.pasich.encly.core.resources.tap_again_to_delete
import com.pasich.encly.core.resources.template_action_items
import com.pasich.encly.core.resources.template_agenda
import com.pasich.encly.core.resources.template_attendees
import com.pasich.encly.core.resources.template_blank
import com.pasich.encly.core.resources.template_checklist_todo
import com.pasich.encly.core.resources.template_journal
import com.pasich.encly.core.resources.template_journal_gratitude
import com.pasich.encly.core.resources.template_journal_today
import com.pasich.encly.core.resources.template_journal_tomorrow
import com.pasich.encly.core.resources.template_meeting
import com.pasich.encly.core.resources.template_notes
import com.pasich.encly.core.resources.template_project
import com.pasich.encly.core.resources.template_project_decisions
import com.pasich.encly.core.resources.template_project_goal
import com.pasich.encly.core.resources.template_project_questions
import com.pasich.encly.core.resources.template_project_tasks
import com.pasich.encly.core.resources.text
import com.pasich.encly.domain.model.NoteLineSpacing
import com.pasich.encly.editor.EditorClipboard
import com.pasich.encly.editor.EditorLogger
import com.pasich.encly.editor.EditorProvider
import com.pasich.encly.editor.EditorString
import com.pasich.encly.editor.EditorStrings
import com.pasich.encly.editor.LinkOpener
import com.pasich.encly.editor.theme.EditorFontFamilies
import com.pasich.encly.editor.theme.EditorFontSizes
import com.pasich.encly.editor.theme.EditorIcons
import com.pasich.encly.editor.theme.EditorLineSpacing
import com.pasich.encly.editor.theme.EditorSpacing
import com.pasich.encly.editor.theme.EditorTheme
import com.pasich.encly.platform.LocalExternalLinks
import com.pasich.encly.platform.LocalSensitiveClipboard
import com.pasich.encly.platform.LocalToaster
import com.pasich.encly.platform.SensitiveClipboard
import com.pasich.encly.platform.Toaster
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.ui.theme.EnclyTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// What the app binds to the editor's extension points (editor/EditorServices.kt, EditorStrings.kt,
// theme/EditorTheme.kt): its string resources, its design system, its clipboard and link policies
// and its logger. The editor itself knows none of them.

/**
 * Provides the editor with the app's strings, theme (built from [fontStyles] and [lineSpacing]),
 * clipboard, link opener and logger above [content].
 */
@Composable
internal fun AppEditorProvider(fontStyles: FontStyles, lineSpacing: NoteLineSpacing, content: @Composable () -> Unit) {
    val clipboard = LocalSensitiveClipboard.current
    val links = LocalExternalLinks.current
    val toaster = LocalToaster.current
    EditorProvider(
        strings = rememberEditorStrings(),
        theme = rememberEditorTheme(fontStyles, lineSpacing),
        clipboard = remember(clipboard, toaster) { AppEditorClipboard(clipboard, toaster) },
        linkOpener = remember(links, toaster) { LinkOpener { url -> openNoteLink(links, toaster, url) } },
        logger = AppEditorLogger,
        content = content,
    )
}

/** The editor's texts from the app's string resources, resolved in the language they have now. */
internal class ResourceEditorStrings(private val texts: Map<EditorString, String>) : EditorStrings {
    override fun text(key: EditorString): String = texts.getValue(key)

    override fun heading(level: Int): String =
        texts.getValue(EditorString.BLOCK_HEADING_LEVEL).replace(HEADING_LEVEL_ARGUMENT, level.toString())

    private companion object {
        /** The placeholder of the level in `block_heading_level`. */
        const val HEADING_LEVEL_ARGUMENT = "%1\$d"
    }
}

/**
 * The strings in the current language. The editor asks for text outside composition, so every
 * string it knows is read here; the strings follow the locale, so the instance changes with it.
 */
@Composable
internal fun rememberEditorStrings(): EditorStrings {
    val texts = EditorString.entries.associateWith { stringResource(it.resource()) }
    return remember(texts) { ResourceEditorStrings(texts) }
}

/** The string resource of [this] key. */
@Suppress("CyclomaticComplexMethod") // One branch per string the editor shows.
internal fun EditorString.resource(): StringResource = when (this) {
    EditorString.TEXT -> Res.string.text
    EditorString.PRESS_TO_EDIT -> Res.string.press_to_edit
    EditorString.BLOCK_HEADING -> Res.string.block_heading
    EditorString.BLOCK_HEADING_LEVEL -> Res.string.block_heading_level
    EditorString.BLOCK_CHECKLIST -> Res.string.block_checklist
    EditorString.BLOCK_BULLETED_LIST -> Res.string.block_bulleted_list
    EditorString.BLOCK_NUMBERED_LIST -> Res.string.block_numbered_list
    EditorString.BLOCK_LIST -> Res.string.block_list
    EditorString.QUOTE -> Res.string.quote
    EditorString.BLOCK_LINK -> Res.string.block_link
    EditorString.BLOCK_SEPARATOR -> Res.string.block_separator
    EditorString.MORE_OPTIONS -> Res.string.more_options
    EditorString.LIST_ITEM_PLACEHOLDER -> Res.string.list_item_placeholder
    EditorString.SLASH_MENU_TITLE -> Res.string.slash_menu_title
    EditorString.ENTER_URL -> Res.string.enter_url
    EditorString.LINK_BLOCKED -> Res.string.link_blocked
    EditorString.LINK_CONTENT_FAILED -> Res.string.falied_content
    EditorString.LINK_ADD -> Res.string.link_add
    EditorString.LINK_OPEN -> Res.string.link_open
    EditorString.LINK_COPY -> Res.string.link_copy
    EditorString.LINK_EDIT -> Res.string.link_edit
    EditorString.BLOCK_MOVE_UP -> Res.string.block_move_up
    EditorString.BLOCK_MOVE_DOWN -> Res.string.block_move_down
    EditorString.BLOCK_DUPLICATE -> Res.string.block_duplicate
    EditorString.BLOCK_DELETE -> Res.string.delete_block
    EditorString.TAP_AGAIN_TO_DELETE -> Res.string.tap_again_to_delete
    EditorString.CHECKLIST_LOCK -> Res.string.checklist_lock
    EditorString.CHECKLIST_UNLOCK -> Res.string.checklist_unlock
    EditorString.TEMPLATE_BLANK -> Res.string.template_blank
    EditorString.TEMPLATE_MEETING -> Res.string.template_meeting
    EditorString.TEMPLATE_ATTENDEES -> Res.string.template_attendees
    EditorString.TEMPLATE_AGENDA -> Res.string.template_agenda
    EditorString.TEMPLATE_NOTES -> Res.string.template_notes
    EditorString.TEMPLATE_ACTION_ITEMS -> Res.string.template_action_items
    EditorString.TEMPLATE_JOURNAL -> Res.string.template_journal
    EditorString.TEMPLATE_JOURNAL_TODAY -> Res.string.template_journal_today
    EditorString.TEMPLATE_JOURNAL_GRATITUDE -> Res.string.template_journal_gratitude
    EditorString.TEMPLATE_JOURNAL_TOMORROW -> Res.string.template_journal_tomorrow
    EditorString.TEMPLATE_PROJECT -> Res.string.template_project
    EditorString.TEMPLATE_PROJECT_GOAL -> Res.string.template_project_goal
    EditorString.TEMPLATE_PROJECT_TASKS -> Res.string.template_project_tasks
    EditorString.TEMPLATE_PROJECT_DECISIONS -> Res.string.template_project_decisions
    EditorString.TEMPLATE_PROJECT_QUESTIONS -> Res.string.template_project_questions
    EditorString.TEMPLATE_CHECKLIST_TODO -> Res.string.template_checklist_todo
}

/**
 * The app's design system as the editor's theme: spacing from [EnclyTheme.spacing], the quote and
 * figure styles from its type scale, the fonts and sizes of [fontStyles], the icons of
 * [EnclyIcons].
 */
@Composable
internal fun rememberEditorTheme(fontStyles: FontStyles, lineSpacing: NoteLineSpacing): EditorTheme {
    val spacing = EnclyTheme.spacing
    val quote = EnclyTheme.typography.quote
    val data = EnclyTheme.typography.dataSmall
    return remember(fontStyles, lineSpacing, spacing, quote, data) {
        EditorTheme(
            spacing = EditorSpacing(
                xxs = spacing.xxs,
                xs = spacing.xs,
                s = spacing.s,
                m = spacing.m,
                l = spacing.l,
                textGap = spacing.textGap,
                labelGap = spacing.labelGap,
                rowGap = spacing.rowGap,
                fieldGap = spacing.fieldGap,
                hairline = spacing.hairline,
                stroke = spacing.stroke,
                minTouchTarget = spacing.minTouchTarget,
                buttonHeight = spacing.buttonHeight,
                rowHeight = spacing.rowHeight,
                icon = spacing.icon,
                iconMedium = spacing.iconMedium,
                iconSmall = spacing.iconSmall,
                tileSmall = spacing.tileSmall,
                gutter = spacing.gutter,
                tapArea = spacing.editorTapArea,
            ),
            fontSizes = EditorFontSizes(
                textBlock = fontStyles.sizes.textBlock,
                h1 = fontStyles.sizes.h1,
                h2 = fontStyles.sizes.h2,
                h3 = fontStyles.sizes.h3,
                h4 = fontStyles.sizes.h4,
                quote = fontStyles.sizes.quote,
                list = fontStyles.sizes.list,
            ),
            fontFamilies = EditorFontFamilies(heading = fontStyles.families.heading, body = fontStyles.families.body),
            lineSpacing = lineSpacing.toEditor(),
            quoteStyle = quote,
            dataStyle = data,
            icons = AppEditorIcons,
        )
    }
}

/** The app's reading density as the editor's. */
internal fun NoteLineSpacing.toEditor(): EditorLineSpacing = when (this) {
    NoteLineSpacing.COMPACT -> EditorLineSpacing.COMPACT
    NoteLineSpacing.DEFAULT -> EditorLineSpacing.DEFAULT
    NoteLineSpacing.COMFORTABLE -> EditorLineSpacing.COMFORTABLE
}

private val AppEditorIcons by lazy {
    EditorIcons(
        text = EnclyIcons.Type,
        heading1 = EnclyIcons.Heading1,
        heading2 = EnclyIcons.Heading2,
        heading3 = EnclyIcons.Heading3,
        heading4 = EnclyIcons.Heading4,
        checklist = EnclyIcons.Checklist,
        bulletList = EnclyIcons.ListBullet,
        numberedList = EnclyIcons.ListNumbered,
        quote = EnclyIcons.Quote,
        link = EnclyIcons.Link,
        separator = EnclyIcons.Separator,
        globe = EnclyIcons.Globe,
        plus = EnclyIcons.Plus,
        check = EnclyIcons.Check,
        external = EnclyIcons.External,
        copy = EnclyIcons.Copy,
        edit = EnclyIcons.Edit,
        arrowUp = EnclyIcons.ArrowUp,
        arrowDown = EnclyIcons.ArrowDown,
        duplicate = EnclyIcons.Duplicate,
        trash = EnclyIcons.Trash,
    )
}

/**
 * The editor copies through the app's clipboard policy ([com.pasich.encly.platform.SensitiveClipboard]):
 * every clip is marked sensitive and cleared after a minute, so [sensitive] changes nothing here.
 */
internal class AppEditorClipboard(private val clipboard: SensitiveClipboard, private val toaster: Toaster) :
    EditorClipboard {
    override fun copy(label: String, text: String, sensitive: Boolean) = copyNoteLink(clipboard, toaster, label, text)
}

/** Failure events go to the app's logger, which writes nothing in any build. */
internal val AppEditorLogger = EditorLogger { tag, event -> AppLogger.w(tag, event) }
