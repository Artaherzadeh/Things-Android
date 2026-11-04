package com.things.app.ui.today

/** One to-do row on the Today page. [checked] is visual only for now. */
data class TodayItem(
    val id: String,
    val title: String,
    val isRecurring: Boolean = false,
    val hasReminder: Boolean = false,
    val checked: Boolean = false,
)

/** To-dos that belong to one project, shown under a header with the project's progress ring. */
data class TodayGroup(
    val projectId: String,
    val projectTitle: String,
    /** 0f..1f, drives the ring icon. */
    val projectProgress: Float,
    val items: List<TodayItem>,
)

data class TodayUiState(
    /** To-dos without a project. Shown first, flat, with no header. */
    val looseItems: List<TodayItem> = emptyList(),
    val groups: List<TodayGroup> = emptyList(),
)

/** Static sample content for previews and the first static version of the page. */
fun sampleTodayState() = TodayUiState(
    looseItems = listOf(
        TodayItem("call", "Call the dentist", hasReminder = true),
        TodayItem("groceries", "Buy groceries"),
    ),
    groups = listOf(
        TodayGroup(
            projectId = "personal",
            projectTitle = "Personal",
            projectProgress = 0.95f,
            items = listOf(
                TodayItem("shower", "Shower", isRecurring = true, hasReminder = true),
                TodayItem("cleaning", "Cleaning House", isRecurring = true),
            ),
        ),
        TodayGroup(
            projectId = "ielts",
            projectTitle = "IELTS",
            projectProgress = 0.65f,
            items = listOf(
                TodayItem("grammar", "Grammar practice"),
            ),
        ),
    ),
)
