package com.pasich.encly.data.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.ChevronsUp
import com.composables.icons.lucide.Lucide

data class PriorityData(
    val id: Int,
    val backgroundColor: Color,
    val contentColor: Color,
    val icon: ImageVector,
    val label: String
)

object PriorityValues {

    val priorities = listOf(
        PriorityData(
            id = 2,
            backgroundColor = Color.Red.copy(alpha = 0.1f),
            contentColor = Color.Red,
            icon = Lucide.ChevronsUp,
            label = "Високий"
        ),
        PriorityData(
            id = 1,
            backgroundColor = Color.Yellow.copy(alpha = 0.1f),
            contentColor = Color.Yellow,
            icon = Lucide.ChevronUp,
            label = "Середній"
        ),
        PriorityData(
            id = 0,
            backgroundColor = Color.LightGray.copy(alpha = 0.1f),
            contentColor = Color.LightGray,
            icon = Lucide.ChevronDown,
            label = "Низький"
        )
    )

    fun getById(id: Int): PriorityData =
        priorities.firstOrNull { it.id == id } ?: priorities.last()
}
