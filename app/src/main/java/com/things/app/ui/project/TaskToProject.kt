package com.things.app.ui.project

import com.things.app.ui.components.ChecklistItemModel
import com.things.app.ui.components.TaskRowModel

/**
 * Converts current to-do data into a project. Title and notes are preserved;
 * checklist items become project tasks in the same order and completion state.
 * The project's own schedule (including its reminder), deadline, tags and repeat
 * are copied directly from the to-do's details without reformatting or inference.
 */
fun TaskRowModel.toProjectState(
    projectId: String,
    checklistTaskId: (ChecklistItemModel) -> String = { "$projectId-${it.id}" },
): ProjectUiState {
    val tasks = details.checklist.map { item ->
        TaskRowModel(id = checklistTaskId(item), title = item.title, checked = item.done)
    }
    return ProjectUiState(
        id = projectId,
        title = title.ifBlank { "New Project" },
        progress = progressOf(tasks),
        notes = details.notes,
        tasks = tasks,
        tags = details.tags.toList(),
        schedule = details.schedule,
        deadline = details.deadline,
        repeat = details.repeat,
    )
}

/** Compatibility hook: all four formerly unsupported detail fields are now carried. */
@Suppress("UNUSED_PARAMETER")
fun TaskRowModel.unsupportedProjectFields(): List<String> = emptyList()

/** The project without these task IDs; progress is recalculated. */
fun ProjectUiState.withoutTasks(ids: Set<String>): ProjectUiState {
    val remaining = tasks.filter { it.id !in ids }
    val remainingHeadings = headings.map { heading -> heading.copy(tasks = heading.tasks.filter { it.id !in ids }) }
    return copy(
        tasks = remaining,
        headings = remainingHeadings,
        laterTasks = laterTasks.filter { it.id !in ids },
        progress = progressOf(remaining + remainingHeadings.flatMap { it.tasks }, loggedTasks.size),
    )
}
