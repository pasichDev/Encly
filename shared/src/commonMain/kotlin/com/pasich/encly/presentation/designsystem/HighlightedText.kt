package com.pasich.encly.presentation.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

/** [text] with every case-insensitive occurrence of [searchQuery] highlighted. */
@Composable
fun HighlightedText(
    text: String,
    searchQuery: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    if (searchQuery.isBlank()) {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    val annotatedString = buildAnnotatedString {
        val normalStyle = SpanStyle(color = color)
        // Matches read as marked text: `primaryContainer` behind `onPrimaryContainer`.
        val highlightStyle = SpanStyle(
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
            background = MaterialTheme.colorScheme.primaryContainer,
        )

        var startIndex = 0
        val lowerCaseText = text.lowercase()
        val lowerCaseQuery = searchQuery.lowercase()

        while (startIndex < text.length) {
            val index = lowerCaseText.indexOf(lowerCaseQuery, startIndex)
            if (index == -1) {
                withStyle(normalStyle) {
                    append(text.substring(startIndex))
                }
                break
            }

            // Append the text before the found word
            if (index > startIndex) {
                withStyle(normalStyle) {
                    append(text.substring(startIndex, index))
                }
            }

            // Append the highlighted word
            withStyle(highlightStyle) {
                append(text.substring(index, index + searchQuery.length))
            }

            startIndex = index + searchQuery.length
        }
    }

    Text(
        text = annotatedString,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
