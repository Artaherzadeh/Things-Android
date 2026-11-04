package com.things.app.ui.upcoming

import com.things.app.ui.components.TaskRowModel

/**
 * One day section on the Upcoming page. [label] is what is written under the divider
 * ("Tomorrow", "Friday", ...). Deciding which days exist and what they are called belongs to the
 * logic layer later; this model only describes what to draw.
 */
data class UpcomingDay(
    val id: String,
    val dayNumber: Int,
    val label: String,
    val tasks: List<TaskRowModel> = emptyList(),
)

data class UpcomingUiState(
    val days: List<UpcomingDay> = emptyList(),
)

/** Static sample content for previews and the first static version of the page. */
fun sampleUpcomingState() = UpcomingUiState(
    days = listOf(
        UpcomingDay("2026-11-05", 5, "Tomorrow"),
        UpcomingDay(
            id = "2026-11-06",
            dayNumber = 6,
            label = "Friday",
            tasks = listOf(
                TaskRowModel("cleaning", "Cleaning House", subtitle = "Personal", isRecurring = true),
                TaskRowModel("invoice", "Send invoice"),
            ),
        ),
        UpcomingDay("2026-11-07", 7, "Saturday"),
        UpcomingDay(
            id = "2026-11-08",
            dayNumber = 8,
            label = "Sunday",
            tasks = listOf(
                TaskRowModel(
                    "report",
                    "Submit weekly report",
                    subtitle = "Work",
                    hasReminder = true,
                    hasNote = true,
                ),
            ),
        ),
        UpcomingDay("2026-11-09", 9, "Monday"),
        UpcomingDay("2026-11-10", 10, "Tuesday"),
        UpcomingDay("2026-11-11", 11, "Wednesday"),
    ),
)
