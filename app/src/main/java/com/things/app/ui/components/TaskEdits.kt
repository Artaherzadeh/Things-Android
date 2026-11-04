package com.things.app.ui.components

/** The to-do with [schedule] set (or cleared when null). The Today star and the bell follow it. */
fun TaskRowModel.withSchedule(schedule: ScheduleModel?): TaskRowModel = copy(
    details = details.copy(schedule = schedule),
    isToday = schedule?.kind == ScheduleKind.Today,
    hasReminder = schedule?.reminder != null,
)

/** The to-do with exactly these [tags]. The tag mark in the closed row follows them. */
fun TaskRowModel.withTags(tags: List<String>): TaskRowModel = copy(
    details = details.copy(tags = tags),
    hasTag = tags.isNotEmpty(),
)
