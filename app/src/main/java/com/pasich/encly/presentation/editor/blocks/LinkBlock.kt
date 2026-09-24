package com.pasich.encly.presentation.editor.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.pasich.encly.R
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyTextField
import com.pasich.encly.presentation.designsystem.EnclyToolButton
import com.pasich.encly.presentation.designsystem.FieldState
import com.pasich.encly.presentation.designsystem.ToolStyle
import com.pasich.encly.presentation.editor.BlockActions
import com.pasich.encly.presentation.editor.state.BlockRemoveAction
import com.pasich.encly.ui.theme.EnclyTheme

/**
 * A link: its address entry until one is saved (or while [isEditing] it), then the link as
 * underlined `primary` text; [onClick] opens the link's sheet. The editor focuses a new link's
 * entry through [modifier]; the entry focuses itself only when "Edit link" reopened it.
 */
@Composable
fun LinkBlock(
    block: Block.LinkBlock,
    blockActions: BlockActions?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    isLocked: Boolean = false,
    isEditing: Boolean = false,
) {
    val urlModel by block.block.collectAsState()
    // Outlives the saved link, so "Edit link" reopens the entry with the current address.
    var inputText by remember { mutableStateOf(urlModel.url) }
    // Set when the entered address may not be saved (not a web or mail link).
    var rejected by remember { mutableStateOf(false) }
    LaunchedEffect(urlModel.url, isEditing) {
        if (urlModel.url.isNotBlank()) inputText = urlModel.url
    }

    // [isEditing]: the saved link stays in the block while its address is edited here, so it
    // is still stored if the user leaves without committing a new one.
    if ((urlModel.url.isBlank() || isEditing) && !isLocked) {
        LinkUrlField(
            inputText = inputText,
            rejected = rejected,
            onInputChange = {
                inputText = it
                rejected = false
            },
            onCommit = {
                if (inputText.isNotBlank()) {
                    val link = buildLinkData(inputText)
                    rejected = link == null
                    if (link != null) {
                        if (blockActions != null) blockActions.onLinkChanged(link) else block.block.value = link
                    }
                }
            },
            onRemoveBlock = { blockActions?.onRemoveBlock(BlockRemoveAction.REMOVE_BACKSPACE) },
            modifier = modifier,
            fieldModifier = fieldModifier,
            autoFocus = isEditing,
        )
    } else if (urlModel.url.isNotBlank()) {
        LinkText(urlModel = urlModel, onClick = onClick, modifier = modifier)
    }
}

@Composable
private fun LinkUrlField(
    inputText: String,
    rejected: Boolean,
    onInputChange: (String) -> Unit,
    onCommit: () -> Unit,
    onRemoveBlock: () -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    autoFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xs),
        modifier = modifier.fillMaxWidth(),
    ) {
        EnclyTextField(
            value = inputText,
            onValueChange = onInputChange,
            label = stringResource(R.string.enter_url),
            state = if (rejected) FieldState.Error(stringResource(R.string.link_blocked)) else FieldState.Default,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit() }),
            modifier = Modifier.weight(1f),
            fieldModifier = fieldModifier
                .focusRequester(focusRequester)
                .onKeyEvent {
                    // Backspace in an empty field removes the block; otherwise it edits the URL.
                    if (it.key == Key.Backspace && it.type == KeyEventType.KeyDown && inputText.isEmpty()) {
                        onRemoveBlock()
                        true
                    } else {
                        false
                    }
                },
        )
        EnclyToolButton(
            icon = EnclyIcons.Plus,
            contentDescription = stringResource(R.string.link_add),
            onClick = if (inputText.isNotBlank()) onCommit else null,
            style = ToolStyle.FILLED,
        )
    }
    if (autoFocus) {
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    }
}

/**
 * A saved link as a card: a globe tile, the site's host as its title and the rest of the address
 * under it (the app is offline, so there is no fetched preview). A tap opens the link's sheet.
 * A link Encly will not open ([NoteLink.Blocked]) shows its whole address and says so in `error`.
 */
@Composable
private fun LinkText(urlModel: LinkDataBlock, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val link = remember(urlModel.url) { parseNoteLink(urlModel.url) }
    val (title, detail) = remember(link) { link.cardLines() }
    val error = when {
        link is NoteLink.Blocked -> stringResource(R.string.link_blocked)
        urlModel.isError -> stringResource(R.string.falied_content)
        else -> null
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .padding(vertical = EnclyTheme.spacing.xxs)
            .fillMaxWidth(),
    ) {
        // Clickable inside the Surface, so the ripple keeps to the card's corners.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s),
            modifier = Modifier
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.more_options), onClick = onClick)
                .padding(EnclyTheme.spacing.s),
        ) {
            LinkTile()
            Column(
                verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.textGap),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = if (link is NoteLink.Blocked) TextOverflow.MiddleEllipsis else TextOverflow.Ellipsis,
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (detail.isNotEmpty()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
            }
        }
    }
}

/** The globe tile at the start of a link card. */
@Composable
private fun LinkTile(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(EnclyTheme.spacing.tileSmall)) {
            Icon(
                EnclyIcons.Globe,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(EnclyTheme.spacing.iconSmall),
            )
        }
    }
}
