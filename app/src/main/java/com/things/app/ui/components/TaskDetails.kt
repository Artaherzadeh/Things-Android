package com.things.app.ui.components

/** One line of a to-do's checklist. */
data class ChecklistItemModel(
    val id: String,
    val title: String,
    val done: Boolean = false,
)

/** Which icon the "When" line of an open to-do shows. */
enum class ScheduleKind { Today, Evening, Someday, Date }

/**
 * The "When" line: for example Today, This Evening, Someday or a date such as Fri, Nov 6.
 * [reminder] is the time of the reminder (for example 9:00 AM). Null means no reminder is set.
 */
data class ScheduleModel(
    val kind: ScheduleKind,
    val label: String,
    val reminder: String? = null,
)

/** The deadline line: the date and, in grey after it, how long is left (for example 12 days left). */
data class DeadlineModel(val dateLabel: String, val remainingLabel: String? = null)

/** The note shown on a to-do that was created by a repeating template. */
data class RepeatInfoModel(val title: String, val summary: String)

/**
 * Everything an open (expanded) to-do shows besides its title. Every part is optional: a line or a
 * button appears only when its value is set (a schedule, a deadline, tags, a checklist...). Pure UI
 * data: the text of the labels is produced by the logic layer later.
 */
data class TaskDetailsModel(
    val notes: String = "",
    val checklist: List<ChecklistItemModel> = emptyList(),
    val schedule: ScheduleModel? = null,
    val deadline: DeadlineModel? = null,
    val repeat: RepeatInfoModel? = null,
    val tags: List<String> = emptyList(),
)
