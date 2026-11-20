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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.tap_again_to_delete
import com.pasich.encly.ui.theme.EnclyTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * One quick action of [EnclySheetTiles]: its [label] under its [icon]. A [destructive] tile is drawn in
 * `error`; with [confirmFirst] the first tap only arms it ("Tap again to delete") for a few seconds. An
 * [active] tile (a pinned note's Pin, say) is filled with `primaryContainer` and reported as selected.
 */
class SheetTile(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val confirmFirst: Boolean = false,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

private const val SHEET_TILE_COLUMNS = 4
private const val SHEET_TILE_LABEL_LINES = 2

/** "Tap again to delete" runs to three lines in the longer languages. */
private const val SHEET_TILE_ARMED_LABEL_LINES = 3
private val SheetTileMinHeight = 64.dp

/**
 * Quick actions as a grid of tiles, four to a line (`surfaceContainer`, radius 16, a 22 dp icon in
 * `primary` over a small label of at most two lines). The tiles of a line are as high as its highest; a
 * last line that is not full keeps its tiles at their width. On a sheet whose own colour is the tile
 * colour, pass another [containerColor] so the tiles stand out.
 */
@Composable
fun EnclySheetTiles(
    tiles: List<SheetTile>,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val gap = EnclyTheme.spacing.xs
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        tiles.chunked(SHEET_TILE_COLUMNS).forEach { line ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                // Keyed by its label: an armed Delete must not hand its state to the tile that takes its place.
                line.forEach { tile ->
                    key(tile.label) { SheetTileButton(tile, containerColor, Modifier.weight(1f).fillMaxHeight()) }
                }
                repeat(SHEET_TILE_COLUMNS - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A tile's colours: its fill, its label and its icon. */
private class TileLook(val container: Color, val content: Color, val icon: Color)

/**
 * Armed, the tile itself turns to the error colours: the warning must not hang on a label that a long
 * translation cuts short. Otherwise an active tile is filled, a destructive one is drawn in `error`.
 */
@Composable
private fun tileLook(tile: SheetTile, armed: Boolean, containerColor: Color): TileLook {
    val colors = MaterialTheme.colorScheme
    return when {
        armed -> TileLook(colors.errorContainer, colors.onErrorContainer, colors.onErrorContainer)
        tile.active -> TileLook(colors.primaryContainer, colors.onPrimaryContainer, colors.onPrimaryContainer)
        tile.destructive -> TileLook(containerColor, colors.error, colors.error)
        else -> TileLook(containerColor, colors.onSurface, colors.primary)
    }
}

@Composable
private fun SheetTileButton(tile: SheetTile, containerColor: Color, modifier: Modifier = Modifier) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(CONFIRM_WINDOW_MS)
            armed = false
        }
    }
    val look = tileLook(tile, armed, containerColor)
    Surface(
        onClick = {
            if (tile.confirmFirst && !armed) {
                armed = true
            } else {
                armed = false
                tile.onClick()
            }
        },
        shape = MaterialTheme.shapes.medium,
        color = look.container,
        contentColor = look.content,
        modifier = modifier
            .heightIn(min = SheetTileMinHeight)
            .enclyInteractive(MaterialTheme.shapes.medium)
            .semantics {
                role = Role.Button
                if (tile.active) selected = true
                // Arming only changes the label: a live region makes TalkBack speak it ("Tap again to delete").
                if (tile.confirmFirst) liveRegion = LiveRegionMode.Polite
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EnclyTheme.spacing.xxs, Alignment.CenterVertically),
            modifier = Modifier.padding(horizontal = EnclyTheme.spacing.xxs, vertical = EnclyTheme.spacing.xs),
        ) {
            Icon(
                tile.icon,
                contentDescription = null,
                tint = look.icon,
                modifier = Modifier.size(EnclyTheme.spacing.iconMedium),
            )
            Text(
                text = if (armed) stringResource(Res.string.tap_again_to_delete) else tile.label,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = if (armed) SHEET_TILE_ARMED_LABEL_LINES else SHEET_TILE_LABEL_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
