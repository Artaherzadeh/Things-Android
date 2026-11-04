package com.things.app.ui.logbook

data class LogbookEntry(
    val id: String,
    val title: String,
    val dateLabel: String,
    val subtitle: String? = null,
    val isRecurring: Boolean = false,
    val hasChecklist: Boolean = false,
)

data class LogbookMonth(
    val id: String,
    val title: String,
    val entries: List<LogbookEntry>,
)

data class LogbookUiState(val months: List<LogbookMonth> = emptyList())

/** Presentation fixtures only; completion dates and grouping will come from the data layer later. */
fun sampleLogbookState() = LogbookUiState(
    months = listOf(
        LogbookMonth(
            id = "2026-11",
            title = "November",
            entries = listOf(
                LogbookEntry("nov-shower", "Shower", "11/2", "Personal", isRecurring = true),
                LogbookEntry("painting", "Get Mohadeseh’s Tablo", "11/2", "Personal", hasChecklist = true),
            ),
        ),
        LogbookMonth(
            id = "2026-10",
            title = "October",
            entries = listOf(
                LogbookEntry("game", "Death Stranding 1", "10/29", "Game"),
                LogbookEntry("oct-shower-29", "Shower", "10/29", "Personal", isRecurring = true),
                LogbookEntry("print", "Print Mohadeseh’s painting", "10/28", "Personal", hasChecklist = true),
                LogbookEntry("phone", "Update A55 to One UI 9", "10/27", "Personal", hasChecklist = true),
                LogbookEntry("ipad", "Update iPad to iPadOS 27.2", "10/27", "Personal"),
                LogbookEntry("oct-shower-27", "Shower", "10/27", "Personal", isRecurring = true),
                LogbookEntry("cleaning", "Cleaning House", "10/25", "Personal", isRecurring = true),
                LogbookEntry("oct-shower-24", "Shower", "10/24", "Personal", isRecurring = true),
                LogbookEntry("cod", "Call of Duty Modern Warfare 4", "10/23", "Game"),
                LogbookEntry("shopping", "Digikala", "10/23", "Finance", hasChecklist = true),
                LogbookEntry("apps", "Update Applications", "10/23", "Personal", hasChecklist = true),
            ),
        ),
    ),
)
