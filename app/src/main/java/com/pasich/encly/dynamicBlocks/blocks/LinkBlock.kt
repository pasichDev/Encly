package com.pasich.encly.dynamicBlocks.blocks

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.dynamicBlocks.Block
import com.pasich.encly.dynamicBlocks.BlockActions
import com.pasich.encly.dynamicBlocks.BlockRemoveAction
import com.pasich.encly.domain.model.LinkDataBlock
import com.pasich.encly.presentation.screen.editnote.rememberFontStyles


/**
 * Offline-first link block: stores only the user-entered URL and its host as the title.
 * No network requests (no og:image preview) — the app is fully offline.
 */
private fun buildLinkData(rawUrl: String): LinkDataBlock {
    val url = rawUrl.trim()
    val title = Uri.parse(url).host ?: url
    return LinkDataBlock(title = title, imageUrl = "", url = url, isError = false)
}

@Composable
fun LinkBlock(
    block: Block.LinkBlock, blockActions: BlockActions?, onClick: () -> Unit, modifier: Modifier, isLocked: Boolean = false
) {
    val urlModel by block.block.collectAsState()
    val fq = remember { FocusRequester() }
    val fontStyles = rememberFontStyles()

    if (urlModel.url.isBlank()) {
        var inputText by remember { mutableStateOf("") }
        OutlinedTextField(
            value = inputText,
            enabled = !isLocked,
            onValueChange = { inputText = it },
            label = { 
                Text(
                    stringResource(R.string.enter_url),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = fontStyles.families.body,
                        fontSize = fontStyles.sizes.textBlock
                    )
                ) 
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = fontStyles.families.body,
                fontSize = fontStyles.sizes.textBlock
            ),
            singleLine = true,
            maxLines = 1,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.PasswordVisible),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add URL",
                    modifier = Modifier.clickable(enabled = !isLocked) {
                        if (inputText.isNotBlank()) {
                            block.block.value = buildLinkData(inputText)
                        }
                    })
            },
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(fq)
                .onKeyEvent {
                    when (it.key) {
                        Key.Backspace -> {
                            if (urlModel.url.isEmpty()) {
                                blockActions?.onRemoveBlock(BlockRemoveAction.REMOVE_BACKSPACE)
                            }
                            return@onKeyEvent true
                        }

                        else -> false
                    }
                })
        LaunchedEffect(Unit) {
            fq.requestFocus()
        }
    } else {
        OutlinedCard(
            modifier = modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(vertical = 8.dp)
                .clickable { onClick() },
                shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                when {
                    urlModel.isError -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = urlModel.url,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = fontStyles.families.body,
                                    fontSize = fontStyles.sizes.textBlock
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.falied_content),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = fontStyles.families.body,
                                    fontSize = fontStyles.sizes.textBlock
                                ),
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    else -> {
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(15.dp)
                        ) {
                            val domain = Uri.parse(urlModel.url).host ?: urlModel.url

                            Text(
                                text = urlModel.title.takeIf { it.isNotBlank() } ?: domain,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = fontStyles.families.heading,
                                    fontSize = fontStyles.sizes.textBlock
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = urlModel.url,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = fontStyles.families.body,
                                    fontSize = fontStyles.sizes.textBlock * 0.85f
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
