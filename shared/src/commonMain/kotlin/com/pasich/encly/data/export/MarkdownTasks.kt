package com.pasich.encly.data.export

import com.pasich.encly.data.model.Subtask
import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.model.Task

/**
 * The tasks as one Markdown file: a GFM checkbox per task and per sub-task, open tasks first
 * (highest priority first), then the completed ones. A task's priority and tag follow its title in
 * brackets; its description is a quote under it.
 *
 * ```
 * - [ ] Buy milk (High, Home)
 *   > Two litres.
 *   - [ ] Oat
 *   - [x] Soy
 * ```
 */
object MarkdownTasks {
    fun render(tasks: List<Task>, subtasks: List<Subtask>, tags: List<Tag>, labels: MarkdownLabels): String {
        val tagNames = tags.associate { it.id to it.nameTag }
        val subtasksByTask = subtasks.groupBy { it.taskId }
        val ordered = tasks.sortedWith(
            compareBy<Task> { it.isCompleted }.thenByDescending { if (it.isCompleted) 0 else it.priority }
                .thenBy { it.position }.thenBy { it.id },
        )
        return buildString {
            append("# ").append(MarkdownText.line(labels.tasks)).append("\n\n")
            ordered.forEach { task ->
                append(taskLine(task, tagNames[task.categoryId], labels)).append('\n')
                task.description?.takeIf { it.isNotBlank() }?.let { description ->
                    MarkdownText.hardBreakLines(description).forEach { line ->
                        append("  >").append(if (line.isEmpty()) "" else " $line").append('\n')
                    }
                }
                subtasksByTask[task.id].orEmpty().sortedWith(compareBy({ it.position }, { it.id })).forEach { sub ->
                    append("  - ").append(box(sub.isCompleted)).append(MarkdownText.line(sub.title)).append('\n')
                }
            }
        }
    }

    private fun taskLine(task: Task, tagName: String?, labels: MarkdownLabels): String {
        val details = listOfNotNull(labels.priorities[task.priority], tagName?.takeIf { it.isNotBlank() })
            .joinToString(", ") { MarkdownText.escapeInline(it.trim()) }
        val title = MarkdownText.line(task.title)
        return "- " + box(task.isCompleted) + title + if (details.isEmpty()) "" else " ($details)"
    }

    private fun box(done: Boolean) = if (done) "[x] " else "[ ] "
}
