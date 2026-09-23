package com.pasich.encly.presentation.components.custombox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pasich.encly.R
import com.pasich.encly.ui.theme.defaultButtonSize

enum class RoundPosition {
    First,
    Medium,
    Last,
    Full,
}

fun getRoundPosition(roundPosition: RoundPosition): RoundedCornerShape = when (roundPosition) {
    RoundPosition.First -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    RoundPosition.Medium -> RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
    RoundPosition.Last -> RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
    RoundPosition.Full -> RoundedCornerShape(20.dp)
}

@Composable
fun SettingBox(
    title: String,
    modifier: Modifier = Modifier,
    subTitle: String = "",
    roundPosition: RoundPosition = RoundPosition.Full,
    icon: Painter = painterResource(id = R.drawable.ic_about),
    active: Boolean = false,
    action: () -> Unit = {},
    endWidget: @Composable (() -> Unit)? = null,
) {
    var shapeRound by remember { mutableStateOf(getRoundPosition(roundPosition)) }

    // One root: the card plus the gap that separates it from the next item of its group.
    Column(modifier = modifier) {
        ElevatedCard(
            shape = shapeRound,
            modifier = Modifier
                .clip(shapeRound)
                .clickable {
                    action()
                },
            colors = if (active) {
                CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primary)
            } else {
                CardDefaults.elevatedCardColors()
            },
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(shapeRound)
                    .fillMaxSize()
                    .padding(
                        24.dp,
                        if (active) 11.dp else 16.dp,
                        14.dp,
                        if (active) 11.dp else 16.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(0.5f)) {
                    RenderBoxTitle(title = title)
                    RenderBoxDescription(subTitle = subTitle, smallSetting = active)
                }
                Row {
                    Spacer(modifier = Modifier.width(20.dp))
                    if (endWidget == null) {
                        RenderBoxIcon(icon, active)
                    } else {
                        endWidget.invoke()
                    }
                }
            }
        }
        Spacer(
            modifier = Modifier.height(
                if (roundPosition == RoundPosition.Last ||
                    roundPosition == RoundPosition.Full
                ) {
                    25.dp
                } else {
                    5.dp
                },
            ),
        )
    }
}

@Composable
private fun RenderBoxTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun RenderBoxDescription(subTitle: String, smallSetting: Boolean) {
    if (subTitle.isNotBlank()) {
        Text(
            color = if (smallSetting) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.primary
            },
            text = subTitle,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun RenderBoxIcon(icon: Painter, reverseColors: Boolean) {
    Box(
        modifier = Modifier
            .background(
                color = if (reverseColors) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.primary
                },
                shape = RoundedCornerShape(20),
            ),
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = if (reverseColors) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            modifier = Modifier
                .scale(if (reverseColors) 0.8f else 1f)
                .padding(if (reverseColors) 9.dp else 9.dp)
                .size(defaultButtonSize),
        )
    }
}
