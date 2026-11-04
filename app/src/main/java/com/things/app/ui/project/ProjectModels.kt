package com.things.app.ui.project

import com.things.app.ui.components.TaskRowModel

/** A completed to-do shown at the bottom of a project once logged items are revealed. */
data class LoggedTaskModel(
    val id: String,
    val title: String,
    /** Completion date as written next to the checkbox, for example 11/2. */
    val dateLabel: String,
    val isRecurring: Boolean = false,
    val hasChecklist: Boolean = false,
)

/**
 * Everything the project page draws. Which to-dos are active, later or logged is decided by the
 * logic layer; this model only describes the three groups.
 */
data class ProjectUiState(
    val id: String,
    val title: String,
    /** 0f..1f, drives the ring next to the title. */
    val progress: Float,
    val notes: String = "",
    val tasks: List<TaskRowModel> = emptyList(),
    val laterTasks: List<TaskRowModel> = emptyList(),
    val loggedTasks: List<LoggedTaskModel> = emptyList(),
)

/** Static sample content for previews and the first static version of the page. */
fun sampleProjectState(
    id: String = "personal",
    title: String = "Personal",
    progress: Float = 0.8f,
) = ProjectUiState(
    id = id,
    title = title,
    progress = progress,
    tasks = listOf(
        TaskRowModel("drive", "Fix movie hard drive bad sectors", hasTag = true),
        TaskRowModel("usb", "Get USB drives from Mohdeseh"),
        TaskRowModel("shower", "Shower", isToday = true, isRecurring = true, hasReminder = true),
        TaskRowModel("cleaning", "Cleaning House", isToday = true, isRecurring = true),
    ),
    laterTasks = listOf(
        TaskRowModel("cleaning-template", "Cleaning House", chip = "Fri", isRepeatingTemplate = true),
        TaskRowModel(
            "shower-template",
            "Shower",
            chip = "Waiting",
            isRepeatingTemplate = true,
            hasReminder = true,
        ),
    ),
    loggedTasks = listOf(
        LoggedTaskModel("l1", "Shower", "11/2", isRecurring = true),
        LoggedTaskModel("l2", "Get Mohadeseh's Tablo", "11/2", hasChecklist = true),
        LoggedTaskModel("l3", "Shower", "10/29", isRecurring = true),
        LoggedTaskModel("l4", "Print Mohadeseh's painting", "10/28", hasChecklist = true),
        LoggedTaskModel("l5", "Update A55 to One UI 9", "10/27", hasChecklist = true),
        LoggedTaskModel("l6", "Update iPad to iPadOS 27.2", "10/27"),
    ),
)
