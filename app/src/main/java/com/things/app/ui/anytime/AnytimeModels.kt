package com.things.app.ui.anytime

import com.things.app.ui.components.TaskGroupModel
import com.things.app.ui.components.TaskRowModel

data class AnytimeUiState(
    /** To-dos with no project. Shown first, flat, with no header. */
    val looseTasks: List<TaskRowModel> = emptyList(),
    val groups: List<TaskGroupModel> = emptyList(),
)

/** Static sample content for previews and the first static version of the page. */
fun sampleAnytimeState() = AnytimeUiState(
    looseTasks = listOf(
        TaskRowModel("notebook", "Buy a new notebook"),
    ),
    groups = listOf(
        TaskGroupModel(
            projectId = "personal",
            projectTitle = "Personal",
            projectProgress = 0.95f,
            items = listOf(
                TaskRowModel("drive", "Fix movie hard drive", hasTag = true),
                TaskRowModel("usb", "Get USB drives"),
                TaskRowModel("shower", "Shower", isToday = true, isRecurring = true, hasReminder = true),
                TaskRowModel("plants", "Water the plants"),
            ),
        ),
        TaskGroupModel(
            projectId = "work",
            projectTitle = "Work",
            projectProgress = 0.5f,
            items = listOf(
                TaskRowModel("case-study", "Create case study", hasNote = true),
                TaskRowModel("portfolio", "Create PDF portfolio", hasChecklist = true),
            ),
        ),
        TaskGroupModel(
            projectId = "finance",
            projectTitle = "Finance",
            projectProgress = 0.8f,
            items = listOf(
                TaskRowModel("rent", "Pay rent", hasChecklist = true),
                TaskRowModel("insurance", "Renew insurance", hasChecklist = true),
                TaskRowModel("band", "Order wristband", hasNote = true, hasChecklist = true),
            ),
        ),
        TaskGroupModel(
            projectId = "education",
            projectTitle = "Education",
            projectProgress = 0.7f,
            items = listOf(
                TaskRowModel("chapter", "Read chapter 3"),
            ),
        ),
    ),
)
