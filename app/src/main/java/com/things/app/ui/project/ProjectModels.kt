package com.things.app.ui.project

import com.things.app.ui.components.DeadlineModel
import com.things.app.ui.components.RepeatInfoModel
import com.things.app.ui.components.ScheduleModel
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

/** A heading splits a project into parts. Headings exist only inside projects. */
data class ProjectHeadingModel(
    val id: String,
    val title: String,
    val tasks: List<TaskRowModel>,
)

/**
 * Everything the project page draws. Which to-dos are active, later or logged is decided by the
 * logic layer; this model only describes the groups. [tasks] are the to-dos that are not under a
 * heading; they come first, then each heading with its own to-dos.
 *
 * [tags], [schedule], [deadline] and [repeat] are what the project itself holds (the When, Add Tags,
 * Add Deadline and Repeat entries of its menu); each is optional and shows as a line under the title
 * only while it is set.
 */
data class ProjectUiState(
    val id: String,
    val title: String,
    /** 0f..1f, drives the ring next to the title. */
    val progress: Float,
    val notes: String = "",
    val tasks: List<TaskRowModel> = emptyList(),
    val headings: List<ProjectHeadingModel> = emptyList(),
    val laterTasks: List<TaskRowModel> = emptyList(),
    val loggedTasks: List<LoggedTaskModel> = emptyList(),
    val tags: List<String> = emptyList(),
    val schedule: ScheduleModel? = null,
    val deadline: DeadlineModel? = null,
    val repeat: RepeatInfoModel? = null,
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

/** Static sample of a project split into headings. */
fun sampleVacationProjectState(
    id: String = "rome",
    title: String = "Vacation in Rome",
    progress: Float = 0.55f,
) = ProjectUiState(
    id = id,
    title = title,
    progress = progress,
    notes = "We'll go from June 14-22 and visit Antonia and Paolo. Maybe do a night out in Trastevere.",
    headings = listOf(
        ProjectHeadingModel(
            id = "planning",
            title = "Planning",
            tasks = listOf(
                TaskRowModel("rome-guide", "Borrow Emma's travel guide", isToday = true),
                TaskRowModel("rome-flights", "Book flights", hasNote = true),
                TaskRowModel("rome-hotel", "Book a hotel room", hasNote = true),
                TaskRowModel("rome-dates", "Add trip dates to calendar"),
            ),
        ),
        ProjectHeadingModel(
            id = "buy",
            title = "Things to buy",
            tasks = listOf(
                TaskRowModel("rome-battery", "Extra camera battery", hasTag = true),
                TaskRowModel("rome-adapter", "Power adapter", hasTag = true),
            ),
        ),
        ProjectHeadingModel(
            id = "do",
            title = "Things to do",
            tasks = listOf(
                TaskRowModel("rome-violetta", "Meet up with Violetta"),
                TaskRowModel("rome-cooking", "Take a cooking class"),
                TaskRowModel("rome-colosseum", "Visit the Colosseum", hasNote = true),
                TaskRowModel("rome-night", "Photograph the Colosseum at night"),
            ),
        ),
    ),
)
