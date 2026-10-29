package com.pasich.encly.editor

/**
 * Every piece of text the editor shows, by key. The host maps each key to its own localized
 * string (the app maps them onto its string resources), so the editor ships no resource files
 * and stays free of the host's localization system. [english] is the default text.
 */
enum class EditorString(val english: String) {
    TEXT("Text"),
    PRESS_TO_EDIT("Tap to start editing…"),
    BLOCK_HEADING("Heading"),

    /** Takes the heading level (1 to 4): see [EditorStrings.heading]. */
    BLOCK_HEADING_LEVEL("Heading %d"),
    BLOCK_CHECKLIST("Checklist"),
    BLOCK_BULLETED_LIST("Bulleted list"),
    BLOCK_NUMBERED_LIST("Numbered list"),
    BLOCK_LIST("List"),
    QUOTE("Quote"),
    BLOCK_LINK("Link"),
    BLOCK_SEPARATOR("Separator"),
    MORE_OPTIONS("More options"),
    LIST_ITEM_PLACEHOLDER("List item"),
    SLASH_MENU_TITLE("Insert block"),
    ENTER_URL("Enter a link"),
    LINK_BLOCKED("Only web (http, https) and email links can be saved or opened."),
    LINK_CONTENT_FAILED("Couldn't load the content"),
    LINK_ADD("Add link"),
    LINK_OPEN("Open link"),
    LINK_COPY("Copy link"),
    LINK_EDIT("Edit link"),
    BLOCK_MOVE_UP("Move block up"),
    BLOCK_MOVE_DOWN("Move block down"),
    BLOCK_DUPLICATE("Duplicate block"),
    BLOCK_DELETE("Delete block"),
    TAP_AGAIN_TO_DELETE("Tap again to delete"),
    CHECKLIST_LOCK("Lock checklist"),
    CHECKLIST_UNLOCK("Unlock checklist"),
    TEMPLATE_BLANK("Blank"),
    TEMPLATE_MEETING("Meeting"),
    TEMPLATE_ATTENDEES("Attendees"),
    TEMPLATE_AGENDA("Agenda"),
    TEMPLATE_NOTES("Notes"),
    TEMPLATE_ACTION_ITEMS("Action items"),
    TEMPLATE_JOURNAL("Journal"),
    TEMPLATE_JOURNAL_TODAY("Today"),
    TEMPLATE_JOURNAL_GRATITUDE("Gratitude"),
    TEMPLATE_JOURNAL_TOMORROW("Tomorrow"),
    TEMPLATE_PROJECT("Project notes"),
    TEMPLATE_PROJECT_GOAL("Goal"),
    TEMPLATE_PROJECT_TASKS("Tasks"),
    TEMPLATE_PROJECT_DECISIONS("Decisions"),
    TEMPLATE_PROJECT_QUESTIONS("Open questions"),
    TEMPLATE_CHECKLIST_TODO("To do"),
}

/**
 * Looks up the text of an [EditorString] in the host's current language. The editor reads the
 * instance through [LocalEditorStrings] and shows what it returns at that moment, so a host that
 * changes language provides a new instance.
 */
interface EditorStrings {
    /** The text for [key]. */
    fun text(key: EditorString): String

    /** The "Heading N" label for [level] (1 to 4): [EditorString.BLOCK_HEADING_LEVEL] with the level filled in. */
    fun heading(level: Int): String
}

/** English texts: the default when the host provides none, and what the editor's tests read. */
object EnglishEditorStrings : EditorStrings {
    override fun text(key: EditorString): String = key.english

    override fun heading(level: Int): String = EditorString.BLOCK_HEADING_LEVEL.english.replace("%d", level.toString())
}
