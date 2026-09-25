package com.pasich.encly.presentation.components.tasks

import androidx.annotation.StringRes
import com.pasich.encly.R

/** A task priority: its stored [id] and label. [emphasis] marks the one shown in `error` (High). */
data class PriorityData(val id: Int, @param:StringRes val label: Int, val emphasis: Boolean = false)

object PriorityValues {

    /** Highest first, the order the widget and the editor sheet list them in. */
    val priorities = listOf(
        PriorityData(id = 2, label = R.string.priority_high, emphasis = true),
        PriorityData(id = 1, label = R.string.priority_medium),
        PriorityData(id = 0, label = R.string.priority_low),
    )

    fun getById(id: Int): PriorityData = priorities.firstOrNull { it.id == id } ?: priorities.last()
}
