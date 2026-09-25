package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.core.security.RecoveryWords
import com.pasich.encly.core.security.SensitiveDataCleaner
import com.pasich.encly.presentation.components.SensitiveClip
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.delay

/** How long the keyboard takes to slide in; the focused cell is brought into view again after it. */
private const val IME_SETTLE_MS = 350L

/** A typical long recovery word: the grid keeps two columns only if this fits a cell. */
private const val SAMPLE_LONG_WORD = "together"

private val WHITESPACE = Regex("\\s+")

/** The longest BIP39 word; a cell takes no more, so no typing can widen the grid. */
private const val MAX_WORD_LENGTH = 8

/**
 * The words typed into a [RecoveryPhraseInput]. Held with `remember`, never `rememberSaveable`:
 * the phrase must not reach saved state. Words are kept lower case, letters only.
 */
@Stable
class RecoveryPhraseState(val count: Int = RecoveryWords.COUNT) {
    private val cells: SnapshotStateList<String> = MutableList(count) { "" }.toMutableStateList()

    /** Cells the user has moved on from: an unknown word there is flagged. */
    private val left: SnapshotStateList<Boolean> = MutableList(count) { false }.toMutableStateList()

    val words: List<String> get() = cells

    /** Every cell holds a word from the wordlist. */
    val isComplete: Boolean get() = cells.all(RecoveryWords::isWord)

    private val checksumValid by derivedStateOf {
        isComplete && toCharArray().let { phrase ->
            try {
                RecoveryWords.isValidPhrase(phrase)
            } finally {
                SensitiveDataCleaner.clear(phrase)
            }
        }
    }

    /** All words are known but they do not form a valid phrase (a wrong word or order). */
    val checksumFailed: Boolean get() = isComplete && !checksumValid

    /** The phrase can be submitted: every word is known and the checksum holds. */
    val canSubmit: Boolean get() = checksumValid

    /** The first cell (0-based) flagged as not a recovery word, if any. */
    val firstInvalid: Int? get() = cells.indices.firstOrNull(::isInvalid)

    /**
     * A cell is flagged at once when no word starts with what it holds, and when it was left
     * with something that is not a whole word.
     */
    fun isInvalid(index: Int): Boolean {
        val word = cells[index]
        return word.isNotEmpty() && (!RecoveryWords.isPrefix(word) || (left[index] && !RecoveryWords.isWord(word)))
    }

    /**
     * Applies an edit of cell [index] and returns the cell focus should move to, or null to stay.
     * A space commits the word and moves on; several words (a paste) fill the cells from this one
     * on, and a whole phrase fills them from the first.
     */
    fun onValueChange(index: Int, value: String): Int? {
        val tokens = value.split(WHITESPACE).map(::clean).filter(String::isNotEmpty)
        val typing = value.none(Char::isWhitespace)
        if (typing || tokens.isEmpty()) {
            cells[index] = tokens.firstOrNull().orEmpty()
            left[index] = false
            return null
        }
        val start = if (tokens.size >= count) 0 else index
        tokens.take(count - start).forEachIndexed { offset, word ->
            cells[start + offset] = word
            left[start + offset] = true
        }
        return (start + tokens.size).coerceAtMost(count - 1)
    }

    /** Next on cell [index]: the word is committed; returns the cell to move to. */
    fun next(index: Int): Int {
        leave(index)
        return (index + 1).coerceAtMost(count - 1)
    }

    /** Backspace in an empty cell goes back one; returns that cell, or null to delete as usual. */
    fun backspaceTarget(index: Int): Int? = (index - 1).takeIf { cells[index].isEmpty() && index > 0 }

    /** Focus left cell [index]. */
    fun leave(index: Int) {
        left[index] = true
    }

    /** The words joined by single spaces. The caller wipes the array. */
    fun toCharArray(): CharArray {
        val length = cells.sumOf { it.length } + (count - 1)
        val phrase = CharArray(length)
        var at = 0
        cells.forEachIndexed { index, word ->
            if (index > 0) phrase[at++] = ' '
            word.toCharArray(phrase, at)
            at += word.length
        }
        return phrase
    }

    fun clear() {
        for (index in cells.indices) {
            cells[index] = ""
            left[index] = false
        }
    }

    private fun clean(value: String): String = value.filter(Char::isLetter).lowercase().take(MAX_WORD_LENGTH)
}

/**
 * The recovery phrase entry: 12 numbered cells, two columns filled row by row (one column when a
 * word would not fit), styled like [WordGrid]. Rest: 1 dp `outline`; focused: 2 dp `onSurface`;
 * not a recovery word: 2 dp `error`. Space or Next moves on, backspace in an empty cell goes back,
 * a pasted phrase fills the cells. One message under the grid: [error] (the caller's, e.g. a
 * wrong phrase), else an unknown word, else a failed checksum. [onEdit] runs on every change,
 * so the caller can clear its [error]; [onDone] runs on the last cell's Done.
 */
@Composable
fun RecoveryPhraseInput(
    state: RecoveryPhraseState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
    onEdit: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    val requesters = remember(state.count) { List(state.count) { FocusRequester() } }
    val currentOnEdit by rememberUpdatedState(onEdit)
    val currentOnDone by rememberUpdatedState(onDone)
    val actions = remember(requesters) {
        CellActions(
            onEdit = { currentOnEdit() },
            onMoveTo = { target -> requesters[target].requestFocus() },
            onDone = { currentOnDone() },
        )
    }
    val style = EnclyTheme.typography.dataLarge
    // No layout cache: the measured strings are recovery words.
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val longest = remember(state.words.toList(), style, density) {
        // Only real words count: a typo must not flip the grid to one column mid-typing.
        (state.words.filter(RecoveryWords::isWord) + SAMPLE_LONG_WORD).maxOf {
            measurer.measure(it, style, softWrap = false).size.width
        }
    }
    val message = error ?: state.firstInvalid?.let { stringResource(R.string.recovery_word_unknown, it + 1) }
        ?: stringResource(R.string.recovery_phrase_checksum).takeIf { state.checksumFailed }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs)) {
        WordGridLayout(longestWordPx = longest) {
            repeat(state.count) { index ->
                PhraseCell(
                    state = state,
                    index = index,
                    requester = requesters[index],
                    enabled = enabled,
                    actions = actions,
                )
            }
        }
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** What a cell reports: any edit, a move to another cell, and Done on the last one. */
private class CellActions(val onEdit: () -> Unit, val onMoveTo: (Int) -> Unit, val onDone: () -> Unit)

@Composable
private fun PhraseCell(
    state: RecoveryPhraseState,
    index: Int,
    requester: FocusRequester,
    enabled: Boolean,
    actions: CellActions,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val bringIntoView = remember { BringIntoViewRequester() }
    val invalid = state.isInvalid(index)
    val border = when {
        invalid -> BorderStroke(2.dp, colors.error)
        focused -> BorderStroke(2.dp, colors.onSurface)
        else -> BorderStroke(1.dp, colors.outline)
    }
    LaunchedEffect(focused) {
        if (focused) {
            bringIntoView.bringIntoView()
            delay(IME_SETTLE_MS)
            bringIntoView.bringIntoView()
        }
    }
    WordCellFrame(
        number = index + 1,
        border = border,
        numberColor = if (invalid) colors.error else colors.onSurfaceVariant,
        modifier = Modifier.bringIntoViewRequester(bringIntoView),
    ) {
        PhraseField(
            state = state,
            index = index,
            enabled = enabled,
            interactionSource = interactionSource,
            actions = actions,
            modifier = Modifier
                .weight(1f)
                .focusRequester(requester),
        )
    }
}

@Composable
private fun PhraseField(
    state: RecoveryPhraseState,
    index: Int,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    actions: CellActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboard.current
    val focused by interactionSource.collectIsFocusedAsState()
    val word = state.words[index]
    val description = stringResource(R.string.recovery_word_cell, index + 1, state.count)
    // Holds the cursor; the word itself lives in [state], which pastes into other cells also change.
    var field by remember { mutableStateOf(TextFieldValue(word, TextRange(word.length))) }
    // Moved here by Next, space or backspace: type on at the end of the word.
    LaunchedEffect(focused) { if (focused) field = TextFieldValue(word, TextRange(word.length)) }
    BasicTextField(
        value = if (field.text == word) field else TextFieldValue(word, TextRange(word.length)),
        onValueChange = { value ->
            // A pasted phrase does not stay on the clipboard once it is in the cells.
            if (value.text.trim().contains(WHITESPACE)) SensitiveClip.clear(clipboard.nativeClipboard)
            val target = state.onValueChange(index, value.text)
            val cleaned = state.words[index]
            field = if (cleaned == value.text) value else TextFieldValue(cleaned, TextRange(cleaned.length))
            if (target != null && target != index) actions.onMoveTo(target)
            actions.onEdit()
        },
        enabled = enabled,
        singleLine = true,
        textStyle = EnclyTheme.typography.dataLarge.copy(
            color = if (enabled) colors.onSurface else colors.onSurface.disabled(),
        ),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = phraseKeyboard(last = index == state.count - 1),
        keyboardActions = KeyboardActions(
            onNext = { actions.onMoveTo(state.next(index)) },
            onDone = {
                state.leave(index)
                focusManager.clearFocus()
                actions.onDone()
            },
        ),
        interactionSource = interactionSource,
        modifier = modifier
            .onFocusChanged { if (!it.isFocused && word.isNotEmpty()) state.leave(index) }
            .onPreviewKeyEvent { event ->
                val target = state.backspaceTarget(index)
                val back = event.type == KeyEventType.KeyDown && event.key == Key.Backspace && target != null
                if (back && target != null) actions.onMoveTo(target)
                back
            }
            .semantics { contentDescription = description },
    )
}

/** A password keyboard keeps the words out of suggestions and IME learning. */
private fun phraseKeyboard(last: Boolean) = KeyboardOptions(
    keyboardType = KeyboardType.Password,
    autoCorrectEnabled = false,
    capitalization = KeyboardCapitalization.None,
    imeAction = if (last) ImeAction.Done else ImeAction.Next,
)
