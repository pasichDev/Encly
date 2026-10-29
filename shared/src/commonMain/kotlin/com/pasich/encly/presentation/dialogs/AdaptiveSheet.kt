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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pasich.encly.presentation.designsystem.EnclyBottomSheet
import com.pasich.encly.ui.theme.EnclyTheme
import com.pasich.encly.ui.theme.LocalWidthClass
import kotlinx.coroutines.launch

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
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalWidthClass.current.isWide) {
        SheetDialog(onDismissRequest = onDismissRequest, modifier = modifier, title = title, content = content)
    } else {
        EnclyBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            title = title,
            showHandle = showHandle,
            scrollable = scrollable,
            content = content,
        )
    }
}

@Composable
private fun SheetDialog(
    onDismissRequest: () -> Unit,
    title: String?,
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
                color = MaterialTheme.colorScheme.surfaceContainerLow,
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

/**
 * Closes what [AdaptiveSheet] shows, then runs [onDismiss]: a bottom sheet slides away first, a dialog has
 * nothing to slide, so it goes at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSheetDismisser(sheetState: SheetState, onDismiss: () -> Unit): () -> Unit {
    val wide = LocalWidthClass.current.isWide
    val scope = rememberCoroutineScope()
    val currentOnDismissed by rememberUpdatedState(onDismiss)
    return remember(sheetState, wide, scope) {
        {
            if (wide) {
                currentOnDismissed()
            } else {
                scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnDismissed() }
            }
        }
    }
}
