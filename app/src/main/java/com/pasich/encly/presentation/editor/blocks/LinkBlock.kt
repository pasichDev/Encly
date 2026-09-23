package com.pasich.encly.presentation.editor.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.pasich.encly.R
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.presentation.editor.BlockActions
import com.pasich.encly.presentation.editor.state.BlockRemoveAction
import com.pasich.encly.presentation.screen.editnote.rememberFontStyles

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

@Composable
fun LinkBlock(
    block: Block.LinkBlock,
    blockActions: BlockActions?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    isEditing: Boolean = false,
) {
    val urlModel by block.block.collectAsState()
    // Outlives the saved card, so "Edit link" reopens the entry with the current address.
    var inputText by remember { mutableStateOf(urlModel.url) }
    LaunchedEffect(urlModel.url, isEditing) {
        if (urlModel.url.isNotBlank()) inputText = urlModel.url
    }

    // [isEditing]: the saved link stays in the block while its address is edited here, so it
    // is still stored if the user leaves without committing a new one.
    if (urlModel.url.isBlank() || isEditing) {
        LinkUrlField(
            inputText = inputText,
            onInputChange = { inputText = it },
            onCommit = {
                if (inputText.isNotBlank() && !isLocked) {
                    val link = buildLinkData(inputText)
                    if (blockActions != null) blockActions.onLinkChanged(link) else block.block.value = link
                }
            },
            onRemoveBlock = { blockActions?.onRemoveBlock(BlockRemoveAction.REMOVE_BACKSPACE) },
            modifier = modifier,
            isLocked = isLocked,
        )
    } else {
        LinkCard(urlModel = urlModel, onClick = onClick, modifier = modifier)
    }
}

@Composable
private fun LinkUrlField(
    inputText: String,
    onInputChange: (String) -> Unit,
    onCommit: () -> Unit,
    onRemoveBlock: () -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
) {
    val fq = remember { FocusRequester() }
    val fontStyles = rememberFontStyles()
    OutlinedTextField(
        value = inputText,
        enabled = !isLocked,
        onValueChange = onInputChange,
        label = {
            Text(
                stringResource(R.string.enter_url),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = fontStyles.families.body,
                    fontSize = fontStyles.sizes.textBlock,
                ),
            )
        },
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = fontStyles.families.body,
            fontSize = fontStyles.sizes.textBlock,
        ),
        singleLine = true,
        maxLines = 1,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onCommit() }),
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.link_add),
                modifier = Modifier.clickable(enabled = !isLocked) { onCommit() },
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(fq)
            .onKeyEvent {
                // Backspace in an empty field removes the block; otherwise it edits the URL.
                if (it.key == Key.Backspace && inputText.isEmpty()) {
                    onRemoveBlock()
                    true
                } else {
                    false
                }
            },
    )
    LaunchedEffect(Unit) {
        fq.requestFocus()
    }
}

/** A saved link: its title (or host) and address; [onClick] opens the link's sheet. */
@Composable
private fun LinkCard(urlModel: LinkDataBlock, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (urlModel.isError) LinkErrorContent(urlModel) else LinkContent(urlModel)
        }
    }
}

@Composable
private fun LinkErrorContent(urlModel: LinkDataBlock) {
    val fontStyles = rememberFontStyles()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            text = urlModel.url,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = fontStyles.families.body,
                fontSize = fontStyles.sizes.textBlock,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.falied_content),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = fontStyles.families.body,
                fontSize = fontStyles.sizes.textBlock,
            ),
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LinkContent(urlModel: LinkDataBlock) {
    val fontStyles = rememberFontStyles()
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(15.dp),
    ) {
        val domain = urlModel.url.toUri().host ?: urlModel.url

        Text(
            text = urlModel.title.takeIf { it.isNotBlank() } ?: domain,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = fontStyles.families.heading,
                fontSize = fontStyles.sizes.textBlock,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = urlModel.url,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = fontStyles.families.body,
                fontSize = fontStyles.sizes.textBlock * 0.85f,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
