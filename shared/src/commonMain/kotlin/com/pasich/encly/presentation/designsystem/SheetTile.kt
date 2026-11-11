package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pasich.encly.ui.theme.EnclyTheme

/** One quick action of [EnclySheetTiles]: its [label] under its [icon]. */
class SheetTile(val label: String, val icon: ImageVector, val onClick: () -> Unit)

private const val SHEET_TILE_COLUMNS = 4
private const val SHEET_TILE_LABEL_LINES = 2
private val SheetTileMinHeight = 64.dp

/**
 * Quick actions as a grid of tiles, four to a line (`surfaceContainer`, radius 16, a 22 dp icon in
 * `primary` over a small label of at most two lines). The tiles of a line are as high as its highest; a
 * last line that is not full keeps its tiles at their width.
 */
@Composable
fun EnclySheetTiles(tiles: List<SheetTile>, modifier: Modifier = Modifier) {
    val gap = EnclyTheme.spacing.xs
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        tiles.chunked(SHEET_TILE_COLUMNS).forEach { line ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                line.forEach { tile -> SheetTileButton(tile, Modifier.weight(1f).fillMaxHeight()) }
                repeat(SHEET_TILE_COLUMNS - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SheetTileButton(tile: SheetTile, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = tile.onClick,
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainer,
        contentColor = colors.onSurface,
        modifier = modifier
            .heightIn(min = SheetTileMinHeight)
            .enclyInteractive(MaterialTheme.shapes.medium)
            .semantics { role = Role.Button },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs, Alignment.CenterVertically),
            modifier = Modifier.padding(horizontal = EnclyTheme.spacing.xxs, vertical = EnclyTheme.spacing.xs),
        ) {
            Icon(
                tile.icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(EnclyTheme.spacing.iconMedium),
            )
            Text(
                text = tile.label,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = SHEET_TILE_LABEL_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
