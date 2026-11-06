package com.things.app.ui.project

import com.things.app.ui.components.ExportHeading
import com.things.app.ui.components.ExportProject
import com.things.app.ui.components.LoggedExport

/** The logged to-do as the exporter takes it. */
fun LoggedTaskModel.toLoggedExport() = LoggedExport(title = title, dateLabel = dateLabel, isRecurring = isRecurring)

/**
 * The project as the shared exporter takes it, exactly as it is stored in this state. A page that
 * holds edits of its own (ProjectScreen) builds the export from its live data instead.
 */
fun ProjectUiState.toExportProject(): ExportProject = ExportProject(
    title = title,
    notes = notes,
    tasks = tasks,
    headings = headings.map { ExportHeading(it.title, it.tasks) },
    later = laterTasks,
    logged = loggedTasks.map { it.toLoggedExport() },
)
