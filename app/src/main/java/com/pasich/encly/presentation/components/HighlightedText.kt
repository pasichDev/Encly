package com.pasich.encly.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle


@Composable
fun HighlightedText(
    text: String,
    searchQuery: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    maxLines: Int = Int.MAX_VALUE
) {
    if (searchQuery.isBlank()) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
        return
    }

    val annotatedString = buildAnnotatedString {
        val normalStyle = SpanStyle(color = color)
        val highlightStyle = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            background = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
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
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}
