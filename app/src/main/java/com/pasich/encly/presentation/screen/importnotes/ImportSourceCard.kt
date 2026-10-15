package com.pasich.encly.presentation.screen.importnotes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import com.pasich.encly.R
import com.pasich.encly.data.importer.ImportSource
import com.pasich.encly.presentation.designsystem.EnclyCard
import com.pasich.encly.presentation.designsystem.EnclyCardStyle
import com.pasich.encly.presentation.designsystem.EnclyIcons
import com.pasich.encly.ui.theme.EnclyTheme

/**
 * One supported app on the Import notes page: its mark, its name and what to pick. A tap anywhere
 * on the card unfolds how to get the export out of the app and what will not come over, so the
 * list stays short enough to scan.
 */
@Composable
internal fun SourceCard(source: ImportSource, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (expanded) CHEVRON_TURN else 0f, label = "chevron")
    EnclyCard(modifier = modifier, style = EnclyCardStyle.OUTLINED, onClick = { expanded = !expanded }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.rowGap),
        ) {
            SourceMark(source)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.textGap),
            ) {
                Text(text = stringResource(source.title), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = stringResource(source.formats),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                EnclyIcons.ChevronDown,
                contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(EnclyTheme.spacing.iconSmall)
                    .rotate(turn),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(
                    start = EnclyTheme.spacing.tileSmall + EnclyTheme.spacing.rowGap,
                    top = EnclyTheme.spacing.s,
                ),
                verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.s),
            ) {
                Labelled(stringResource(R.string.import_label_how_to), stringResource(source.howTo))
                Labelled(stringResource(R.string.import_label_losses), stringResource(source.losses))
            }
        }
    }
}

/** The app's initials on a small tile: a plain mark, never the app's own logo or colours. */
@Composable
private fun SourceMark(source: ImportSource) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(EnclyTheme.spacing.tileSmall)) {
            Text(
                text = source.initials(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private fun ImportSource.initials(): String = when (this) {
    ImportSource.GOOGLE_KEEP -> "GK"
    ImportSource.STANDARD_NOTES -> "SN"
    ImportSource.JOPLIN -> "J"
    ImportSource.NOTESNOOK -> "NN"
    ImportSource.MARKDOWN -> "MD"
}

private const val CHEVRON_TURN = 180f
