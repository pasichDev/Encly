package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.ui.theme.EnclyTheme

/** Size of the wordmark tile. */
private val WordmarkTile = 34.dp

/** The lock and empty-state tile: 64 dp, radius 20. */
private val TileShape = RoundedCornerShape(20.dp)

/**
 * The Encly wordmark: the 34 dp logo mark, then the name in the wordmark style
 * (always Playfair Display). [style] lets the About header set it larger.
 */
@Composable
fun EnclyWordmark(appName: String, modifier: Modifier = Modifier, style: TextStyle = EnclyTheme.typography.wordmark) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        EnclyLogoMark(size = WordmarkTile, shape = RoundedCornerShape(10.dp))
        Text(text = appName, style = style, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Welcome header: the wordmark on the left, the language pill on the right. */
@Composable
fun WelcomeHeader(
    appName: String,
    language: String,
    languageDescription: String,
    onLanguage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EnclyTheme.spacing.topBarHeight)
            .padding(start = EnclyTheme.spacing.l, top = EnclyTheme.spacing.m, end = EnclyTheme.spacing.m),
    ) {
        EnclyWordmark(appName = appName, modifier = Modifier.weight(1f))
        LanguagePill(label = language, description = languageDescription, onClick = onLanguage)
    }
}

/** The language pill: globe and the current language, 1 dp `outlineVariant` border. */
@Composable
fun LanguagePill(label: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(EnclyTheme.spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.heightIn(min = EnclyTheme.spacing.minTouchTarget),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.labelGap),
            modifier = Modifier.padding(horizontal = EnclyTheme.spacing.s, vertical = EnclyTheme.spacing.xs),
        ) {
            Icon(
                EnclyIcons.Globe,
                contentDescription = description,
                modifier = Modifier.size(EnclyTheme.spacing.iconXSmall),
            )
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** The Restore header: back, then a label instead of the progress bar. */
@Composable
fun LabelHeader(label: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EnclyTheme.spacing.topBarHeight)
            .padding(start = EnclyTheme.spacing.xs, top = EnclyTheme.spacing.xs, end = EnclyTheme.spacing.m),
    ) {
        EnclyBackButton(onClick = onBack)
        Text(
            text = label.uppercase(),
            style = EnclyTheme.typography.stepLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The logo: the launcher icon's E on a `primary` tile. It draws the launcher foreground itself, so
 * the in-app mark and the home-screen icon can never drift apart.
 */
@Composable
fun EnclyLogoMark(modifier: Modifier = Modifier, size: Dp = EnclyTheme.spacing.tile, shape: Shape = TileShape) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.primary, modifier = modifier) {
        Icon(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(size),
        )
    }
}

/** A 64 dp tile with radius 20 in `primaryContainer` holding [icon] (the lock screen, empty states). */
@Composable
fun EnclyIconTile(icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(shape = TileShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(EnclyTheme.spacing.tile)) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

/**
 * An empty (or failed) list: the tile, a titleLarge line and a muted bodyMedium line, centred in
 * the 24 dp gutter. [error] tints the title `error` for a failed read. [actionLabel] adds a text
 * action below ("Show all", "New task") that runs [onAction].
 */
@Composable
fun EnclyEmptyState(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    title: String? = null,
    body: String? = null,
    error: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = EnclyTheme.spacing.gutter, vertical = EnclyTheme.spacing.xxl),
    ) {
        EnclyIconTile(icon = icon)
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null) EnclyTextButton(text = actionLabel, onClick = onAction)
    }
}
