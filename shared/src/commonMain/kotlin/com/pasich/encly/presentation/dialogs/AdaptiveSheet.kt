package com.pasich.encly.presentation.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pasich.encly.presentation.designsystem.EnclyBottomSheet
import com.pasich.encly.presentation.navigation.LocalVaultLocked
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.ui.theme.LocalWidthClass
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The test tag of the dialog that stands in for a bottom sheet in a wide window. */
internal const val SHEET_DIALOG_TAG = "adaptive-sheet-dialog"

/** The share of the window's height a dialog may take before its content scrolls. */
private const val DIALOG_MAX_HEIGHT_FRACTION = 0.85f

/**
 * What the design system calls a bottom sheet, placed the way Material guides each window size: a bottom
 * sheet in a compact window (exactly [EnclyBottomSheet]), a centred dialog of the same content in a medium or
 * expanded one, where a sheet would stretch across the whole width. [sheetState], [showHandle] and
 * [scrollable] only matter to the sheet; the dialog always scrolls what does not fit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    title: String? = null,
    showHandle: Boolean = true,
    scrollable: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalWidthClass.current.isWide) {
        SheetDialog(onDismissRequest, title, containerColor, modifier, content)
    } else {
        EnclyBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            title = title,
            showHandle = showHandle,
            scrollable = scrollable,
            containerColor = containerColor,
            content = content,
        )
    }
}

@Composable
private fun SheetDialog(
    onDismissRequest: () -> Unit,
    title: String?,
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = modifier.fillMaxWidth().padding(EnclyTheme.spacing.m),
            contentAlignment = Alignment.Center,
        ) {
            val maxContentHeight = maxHeight * DIALOG_MAX_HEIGHT_FRACTION
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = containerColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .testTag(SHEET_DIALOG_TAG)
                    .widthIn(max = EnclyTheme.spacing.dialogWidth)
                    .fillMaxWidth()
                    .heightIn(max = maxContentHeight),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(EnclyTheme.spacing.gutter),
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = EnclyTheme.spacing.xs),
                        )
                    }
                    content()
                }
            }
        }
    }
}

/** The longest a field waits for its sheet's window before it takes focus anyway. */
private const val WINDOW_FOCUS_TIMEOUT_MS = 1_000L

/**
 * Focuses [requester] (which brings the keyboard up) as its sheet opens; call it in the sheet's content.
 * It waits only for the sheet's window to be the one that takes input, since a keyboard asked for before
 * that is not shown. The keyboard then rises under the sheet's slide and the sheet rides up on it.
 */
@Composable
fun FocusWithSheet(requester: FocusRequester) {
    val window = LocalWindowInfo.current
    LaunchedEffect(requester, window) {
        withTimeoutOrNull(WINDOW_FOCUS_TIMEOUT_MS) { snapshotFlow { window.isWindowFocused }.first { it } }
        runCatching { requester.requestFocus() }
    }
}

/**
 * Shows [content] (an [AdaptiveSheet] on [sheetState]) while [visible], and keeps it composed after that
 * until its sheet has slid away. For a sheet that something else closes (a ViewModel, once a save went
 * through): taken out of the composition at once it would be gone in one frame. A sheet opened again
 * meanwhile starts afresh, and nothing is kept on display once the vault locks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetUntilHidden(visible: Boolean, sheetState: SheetState, content: @Composable () -> Unit) {
    val locked = LocalVaultLocked.current
    val openings = remember { SheetOpenings() }
    val opening = openings.count(visible)
    var hidden by remember(opening) { mutableStateOf(false) }
    LaunchedEffect(visible, opening) {
        if (!visible) {
            try {
                sheetState.hide()
            } finally {
                hidden = true
            }
        }
    }
    // Only a sheet this call has shown: a restored state alone never brings one back.
    val slidingAway = opening > 0 && !hidden && !locked && sheetState.isVisible
    if (visible || slidingAway) {
        key(opening) { content() }
    }
}

/** How many times a sheet was opened, counted as its `visible` turns true. */
private class SheetOpenings {
    private var opened = 0
    private var wasVisible = false

    fun count(visible: Boolean): Int {
        if (visible && !wasVisible) opened++
        wasVisible = visible
        return opened
    }
}

/**
 * Closes what [AdaptiveSheet] shows, then runs [onDismiss]: a bottom sheet slides away first, a dialog has
 * nothing to slide, so it goes at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSheetDismisser(sheetState: SheetState, onDismiss: () -> Unit): () -> Unit {
    val close = rememberSheetCloser(sheetState, onDismiss = onDismiss)
    return remember(close) { { close {} } }
}

/**
 * Closes what [AdaptiveSheet] shows and only then runs what a row of it asked for: the returned function
 * takes that action. A sheet taken out of the composition is gone in one frame, with no slide, so a row that
 * closes the sheet lets it slide away first; the action (a navigation, a dialog, a change to the list under
 * it) then plays by itself instead of under a vanishing sheet.
 *
 * [onDismiss] always runs, as for [rememberSheetDismisser]; the action is dropped when the screen left
 * meanwhile. Calls made while the sheet is closing do nothing, so a double tap runs the action once.
 * [alwaysSheet] is for a caller that shows a bottom sheet in every window size.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSheetCloser(
    sheetState: SheetState,
    alwaysSheet: Boolean = false,
    onDismiss: () -> Unit,
): (then: () -> Unit) -> Unit {
    val slides = alwaysSheet || !LocalWidthClass.current.isWide
    val scope = rememberCoroutineScope()
    val currentOnDismissed by rememberUpdatedState(onDismiss)
    return remember(sheetState, slides, scope) {
        var closing = false
        val close: (() -> Unit) -> Unit = { then ->
            if (!slides) {
                currentOnDismissed()
                then()
            } else if (!closing) {
                closing = true
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    closing = false
                    currentOnDismissed()
                    if (scope.isActive) then()
                }
            }
        }
        close
    }
}
