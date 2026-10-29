package com.pasich.encly.presentation.screen.editnote

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.pasich.encly.R
import com.pasich.encly.core.AppLogger
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
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.ui.theme.EnclyTheme

// What the app binds to the editor's extension points (editor/EditorServices.kt, EditorStrings.kt,
// theme/EditorTheme.kt): its string resources, its design system, its clipboard and link policies
// and its logger. The editor itself knows none of them.

/**
 * Provides the editor with the app's strings, theme (built from [fontStyles] and [lineSpacing]),
 * clipboard, link opener and logger above [content].
 */
@Composable
internal fun AppEditorProvider(fontStyles: FontStyles, lineSpacing: NoteLineSpacing, content: @Composable () -> Unit) {
    val context = LocalContext.current
    EditorProvider(
        strings = rememberEditorStrings(),
        theme = rememberEditorTheme(fontStyles, lineSpacing),
        clipboard = remember(context) { AppEditorClipboard(context) },
        linkOpener = remember(context) { LinkOpener { url -> openNoteLink(context, url) } },
        logger = AppEditorLogger,
        content = content,
    )
}

/** The editor's texts from the app's string resources, in the language they resolve to now. */
internal class ResourceEditorStrings(private val resources: Resources) : EditorStrings {
    override fun text(key: EditorString): String = resources.getString(key.resourceId())

    override fun heading(level: Int): String = resources.getString(R.string.block_heading_level, level)
}

/** The strings in the current language: a new instance whenever the resources or configuration change. */
@Composable
internal fun rememberEditorStrings(): EditorStrings {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    return remember(resources, configuration) { ResourceEditorStrings(resources) }
}

/** The string resource of [this] key. */
@Suppress("CyclomaticComplexMethod") // One branch per string the editor shows.
internal fun EditorString.resourceId(): Int = when (this) {
    EditorString.TEXT -> R.string.text
    EditorString.PRESS_TO_EDIT -> R.string.press_to_edit
    EditorString.BLOCK_HEADING -> R.string.block_heading
    EditorString.BLOCK_HEADING_LEVEL -> R.string.block_heading_level
    EditorString.BLOCK_CHECKLIST -> R.string.block_checklist
    EditorString.BLOCK_BULLETED_LIST -> R.string.block_bulleted_list
    EditorString.BLOCK_NUMBERED_LIST -> R.string.block_numbered_list
    EditorString.BLOCK_LIST -> R.string.block_list
    EditorString.QUOTE -> R.string.quote
    EditorString.BLOCK_LINK -> R.string.block_link
    EditorString.BLOCK_SEPARATOR -> R.string.block_separator
    EditorString.MORE_OPTIONS -> R.string.more_options
    EditorString.LIST_ITEM_PLACEHOLDER -> R.string.list_item_placeholder
    EditorString.SLASH_MENU_TITLE -> R.string.slash_menu_title
    EditorString.ENTER_URL -> R.string.enter_url
    EditorString.LINK_BLOCKED -> R.string.link_blocked
    EditorString.LINK_CONTENT_FAILED -> R.string.falied_content
    EditorString.LINK_ADD -> R.string.link_add
    EditorString.LINK_OPEN -> R.string.link_open
    EditorString.LINK_COPY -> R.string.link_copy
    EditorString.LINK_EDIT -> R.string.link_edit
    EditorString.BLOCK_MOVE_UP -> R.string.block_move_up
    EditorString.BLOCK_MOVE_DOWN -> R.string.block_move_down
    EditorString.BLOCK_DUPLICATE -> R.string.block_duplicate
    EditorString.BLOCK_DELETE -> R.string.delete_block
    EditorString.TAP_AGAIN_TO_DELETE -> R.string.tap_again_to_delete
    EditorString.CHECKLIST_LOCK -> R.string.checklist_lock
    EditorString.CHECKLIST_UNLOCK -> R.string.checklist_unlock
    EditorString.TEMPLATE_BLANK -> R.string.template_blank
    EditorString.TEMPLATE_MEETING -> R.string.template_meeting
    EditorString.TEMPLATE_ATTENDEES -> R.string.template_attendees
    EditorString.TEMPLATE_AGENDA -> R.string.template_agenda
    EditorString.TEMPLATE_NOTES -> R.string.template_notes
    EditorString.TEMPLATE_ACTION_ITEMS -> R.string.template_action_items
    EditorString.TEMPLATE_JOURNAL -> R.string.template_journal
    EditorString.TEMPLATE_JOURNAL_TODAY -> R.string.template_journal_today
    EditorString.TEMPLATE_JOURNAL_GRATITUDE -> R.string.template_journal_gratitude
    EditorString.TEMPLATE_JOURNAL_TOMORROW -> R.string.template_journal_tomorrow
    EditorString.TEMPLATE_PROJECT -> R.string.template_project
    EditorString.TEMPLATE_PROJECT_GOAL -> R.string.template_project_goal
    EditorString.TEMPLATE_PROJECT_TASKS -> R.string.template_project_tasks
    EditorString.TEMPLATE_PROJECT_DECISIONS -> R.string.template_project_decisions
    EditorString.TEMPLATE_PROJECT_QUESTIONS -> R.string.template_project_questions
    EditorString.TEMPLATE_CHECKLIST_TODO -> R.string.template_checklist_todo
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
 * The editor copies through the app's clipboard policy ([com.pasich.encly.presentation.components.SensitiveClip]):
 * every clip is marked sensitive and cleared after a minute, so [sensitive] changes nothing here.
 */
internal class AppEditorClipboard(private val context: Context) : EditorClipboard {
    override fun copy(label: String, text: String, sensitive: Boolean) = copyNoteLink(context, label, text)
}

/** Failure events go to the app's logger, which writes nothing in any build. */
internal val AppEditorLogger = EditorLogger { tag, event -> AppLogger.w(tag, event) }
