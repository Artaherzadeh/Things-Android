package com.things.app.ui.today

import com.things.app.ui.components.DeadlineModel
import com.things.app.ui.components.RepeatInfoModel
import com.things.app.ui.components.ScheduleKind
import com.things.app.ui.components.ScheduleModel
import com.things.app.ui.components.TaskDetailsModel
import com.things.app.ui.components.TaskGroupModel
import com.things.app.ui.components.TaskRowModel

data class TodayUiState(
    /** To-dos without a project. Shown first, flat, with no header. */
    val looseItems: List<TaskRowModel> = emptyList(),
    val groups: List<TaskGroupModel> = emptyList(),
)

/** Static sample content for previews and the first static version of the page. */
fun sampleTodayState() = TodayUiState(
    looseItems = listOf(
        TaskRowModel("call", "Call the dentist", hasReminder = true),
        TaskRowModel("groceries", "Buy groceries"),
    ),
    groups = listOf(
        TaskGroupModel(
            projectId = "personal",
            projectTitle = "Personal",
            projectProgress = 0.95f,
            items = listOf(
                TaskRowModel("shower", "Shower", isRecurring = true, hasReminder = true),
                TaskRowModel(
                    "cleaning",
                    "Cleaning House",
                    isRecurring = true,
                    details = TaskDetailsModel(
                        schedule = ScheduleModel(ScheduleKind.Today, "Today"),
                        deadline = DeadlineModel("Mon, Nov 16", "12 days left"),
                        repeat = RepeatInfoModel(
                            title = "This is a copy of a repeating to-do",
                            summary = "Repeat every week on Friday",
                        ),
                    ),
                ),
            ),
        ),
        TaskGroupModel(
            projectId = "ielts",
            projectTitle = "IELTS",
            projectProgress = 0.65f,
            items = listOf(
                TaskRowModel("grammar", "Grammar practice"),
            ),
        ),
    ),
)
