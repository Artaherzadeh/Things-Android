package com.things.app.ui.project

import com.things.app.ui.components.TaskRowModel

/**
 * What turning a heading into a project makes: the new [newProject] and the [source] project without
 * that heading. Both are whole values, so a caller can store them in one step; nothing is changed
 * on the way.
 */
data class HeadingConversion(val newProject: ProjectUiState, val source: ProjectUiState)

/** Share of finished to-dos: the ticked ones and [loggedCount] logged ones over all of them. */
fun progressOf(tasks: List<TaskRowModel>, loggedCount: Int = 0): Float {
    val total = tasks.size + loggedCount
    if (total == 0) return 0f
    return (tasks.count { it.checked } + loggedCount).toFloat() / total
}

/**
 * Turns the heading [headingId] of [source] into a project with the id [newProjectId]. The project
 * gets the heading's title ("New Project" when it has none) and ALL its to-dos, the very same
 * objects in the same order, so ids, ticks, notes, checklists, tags, dates, reminders, deadlines
 * and repeats are kept. The source loses the heading and gets a new ring.
 *
 * Returns null, changing nothing, when [source] has no such heading. [source] must already hold the
 * current data of the page (edits applied).
 */
fun convertHeading(source: ProjectUiState, headingId: String, newProjectId: String): HeadingConversion? {
    val heading = source.headings.firstOrNull { it.id == headingId } ?: return null
    val remaining = source.headings.filter { it.id != headingId }
    val newProject = ProjectUiState(
        id = newProjectId,
        title = heading.title.ifBlank { "New Project" },
        progress = progressOf(heading.tasks),
        tasks = heading.tasks,
    )
    val updatedSource = source.copy(
        headings = remaining,
        progress = progressOf(source.tasks + remaining.flatMap { it.tasks }, source.loggedTasks.size),
    )
    return HeadingConversion(newProject, updatedSource)
}

/**
 * A copy of [heading] with the id [newHeadingId] and a new id from [newTaskId] for each to-do. All
 * the rest of every to-do is the same as in the original.
 */
fun copyHeading(heading: ProjectHeadingModel, newHeadingId: String, newTaskId: () -> String): ProjectHeadingModel =
    heading.copy(id = newHeadingId, tasks = heading.tasks.map { it.copy(id = newTaskId()) })

/**
 * The page after a heading was deleted but its to-dos were kept: [tasks] are the to-dos that are in
 * no heading, [headings] the headings that are left.
 */
data class HeadingRemoval(val tasks: List<TaskRowModel>, val headings: List<ProjectHeadingModel>)

/**
 * Deletes the heading [headingId] and keeps its to-dos: they become to-dos of no heading, at the top
 * of the page (before the to-dos that already were in no heading), in the order they had. They are
 * the very same objects, so ids, ticks, notes, checklists, tags, dates, reminders, deadlines, repeats
 * and the edits held for them are kept. The other headings are not touched.
 *
 * Returns null, changing nothing, when there is no such heading.
 */
fun removeHeadingKeepingTasks(
    tasks: List<TaskRowModel>,
    headings: List<ProjectHeadingModel>,
    headingId: String,
): HeadingRemoval? {
    val heading = headings.firstOrNull { it.id == headingId } ?: return null
    return HeadingRemoval(
        tasks = heading.tasks + tasks,
        headings = headings.filter { it.id != headingId },
    )
}
