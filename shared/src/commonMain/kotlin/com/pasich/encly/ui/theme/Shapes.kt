package com.pasich.encly.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii (design spec §3.1): 6 checkbox · 12 input, word cell · 16 card, sheet · 18 FAB.
 * Buttons, chips, the search field and keypad keys are fully round (CircleShape).
 */
val EnclyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Top corners only: bottom sheets. */
val SheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
