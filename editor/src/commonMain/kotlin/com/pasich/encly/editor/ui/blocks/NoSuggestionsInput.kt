package com.pasich.encly.editor.ui.blocks

import androidx.compose.runtime.Composable

/**
 * Shows [content] (a code field) with the keyboard asked for no suggestions and no autocorrect. A multi-line field
 * autocorrects on keyboards that only read the autocorrect option for single lines (the AOSP keyboard family), and
 * code must stay as typed. A platform without such a keyboard shows [content] as it is.
 */
@Composable
internal expect fun NoSuggestionsInput(content: @Composable () -> Unit)
