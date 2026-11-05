package com.things.app.ui.components

/** The to-do with [schedule] set (or cleared when null). The Today star and the bell follow it. */
fun TaskRowModel.withSchedule(schedule: ScheduleModel?): TaskRowModel = copy(
    details = details.copy(schedule = schedule),
    isToday = schedule?.kind == ScheduleKind.Today,
    hasReminder = schedule?.reminder != null,
)

/** The to-do with [deadline] set (or cleared when null). */
fun TaskRowModel.withDeadline(deadline: DeadlineModel?): TaskRowModel = copy(
    details = details.copy(deadline = deadline),
)

/** The to-do with exactly these [tags]. The tag mark in the closed row follows them. */
fun TaskRowModel.withTags(tags: List<String>): TaskRowModel = copy(
    details = details.copy(tags = tags),
    hasTag = tags.isNotEmpty(),
)

/**
 * The closed-row marks of a to-do that was just closed: the note, checklist and tag marks show only
 * when there is a note, a checklist item or a tag in the card. Nothing else is kept.
 */
fun TaskRowModel.settled(): TaskRowModel = copy(
    hasNote = details.notes.isNotBlank(),
    hasChecklist = details.checklist.isNotEmpty(),
    hasTag = details.tags.isNotEmpty(),
)

/**
 * The list with [task] added next to the to-do [anchorId]: under it when [after], above it
 * otherwise. With no anchor the task goes first; with an unknown anchor, last.
 */
fun List<TaskRowModel>.insertedAt(task: TaskRowModel, anchorId: String?, after: Boolean): List<TaskRowModel> {
    val index = if (anchorId == null) -1 else indexOfFirst { it.id == anchorId }
    val at = when {
        anchorId == null -> 0
        index < 0 -> size
        after -> index + 1
        else -> index
    }
    return toMutableList().also { it.add(at, task) }
}
