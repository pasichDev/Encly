package com.pasich.encly.presentation.components.tasks

import com.pasich.encly.core.resources.Res
import com.pasich.encly.core.resources.priority_high
import com.pasich.encly.core.resources.priority_low
import com.pasich.encly.core.resources.priority_medium
import org.jetbrains.compose.resources.StringResource

/** A task priority: its stored [id] and label. [emphasis] marks the one shown in `error` (High). */
data class PriorityData(val id: Int, val label: StringResource, val emphasis: Boolean = false)

object PriorityValues {

    /** Highest first, the order the widget and the editor sheet list them in. */
    val priorities = listOf(
        PriorityData(id = 2, label = Res.string.priority_high, emphasis = true),
        PriorityData(id = 1, label = Res.string.priority_medium),
        PriorityData(id = 0, label = Res.string.priority_low),
    )

    fun getById(id: Int): PriorityData = priorities.firstOrNull { it.id == id } ?: priorities.last()
}
