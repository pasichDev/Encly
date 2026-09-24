package com.pasich.encly.presentation.editor.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.net.toUri
import com.pasich.encly.R
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.presentation.designsystem.EnclyTextField
import com.pasich.encly.presentation.designsystem.EnclyToolButton
import com.pasich.encly.presentation.designsystem.ToolStyle
import com.pasich.encly.presentation.editor.BlockActions
import com.pasich.encly.presentation.editor.state.BlockRemoveAction
import com.pasich.encly.presentation.screen.editnote.rememberFontStyles
import com.pasich.encly.ui.theme.EnclyTheme

/**
 * Offline-first link block: stores only the user-entered URL and its host as the title.
 * No network requests (no og:image preview) — the app is fully offline.
 */
private fun buildLinkData(rawUrl: String): LinkDataBlock {
    val url = normalizeLinkUrl(rawUrl)
    val title = url.toUri().host ?: url
    return LinkDataBlock(title = title, imageUrl = "", url = url, isError = false)
}

private val URL_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

/**
 * Trims [rawUrl] and gives a bare address ("example.com") an https scheme, so the saved link
 * can be opened. A URL that already names a scheme (https:, mailto:, ...) is kept as typed.
 */
internal fun normalizeLinkUrl(rawUrl: String): String {
    val url = rawUrl.trim()
    return if (url.isEmpty() || URL_SCHEME.containsMatchIn(url)) url else "https://$url"
}

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
    LaunchedEffect(urlModel.url, isEditing) {
        if (urlModel.url.isNotBlank()) inputText = urlModel.url
    }

    // [isEditing]: the saved link stays in the block while its address is edited here, so it
    // is still stored if the user leaves without committing a new one.
    if ((urlModel.url.isBlank() || isEditing) && !isLocked) {
        LinkUrlField(
            inputText = inputText,
            onInputChange = { inputText = it },
            onCommit = {
                if (inputText.isNotBlank()) {
                    val link = buildLinkData(inputText)
                    if (blockActions != null) blockActions.onLinkChanged(link) else block.block.value = link
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

/** A saved link: its address in `primary`, underlined (design spec §4.4), or why it is broken. */
@Composable
private fun LinkText(urlModel: LinkDataBlock, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fontStyles = rememberFontStyles()
    val style = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = fontStyles.families.body,
        fontSize = fontStyles.sizes.textBlock,
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.textGap, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EnclyTheme.spacing.textButtonHeight)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.more_options), onClick = onClick),
    ) {
        Text(
            text = urlModel.url,
            style = style.copy(textDecoration = TextDecoration.Underline),
            color = MaterialTheme.colorScheme.primary,
        )
        if (urlModel.isError) {
            Text(
                text = stringResource(R.string.falied_content),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
