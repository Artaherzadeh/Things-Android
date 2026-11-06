package com.things.app.ui.project

import com.things.app.ui.components.ChecklistItemModel
import com.things.app.ui.components.TaskRowModel

/**
 * The project a to-do turns into: the to-do's title is the project's name ("New Project" when it has
 * none), its notes are the project's notes, and each checklist item becomes a to-do of the project
 * (done stays done). [checklistTaskId] gives the id of the to-do made from a checklist item. The
 * to-do must already hold its current data (edits applied). The tags, dates and deadline of the
 * to-do are not carried: a project has no place for them yet.
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
    )
}

/** The project without the to-dos with these [ids], wherever they are; the ring is worked out again. */
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
