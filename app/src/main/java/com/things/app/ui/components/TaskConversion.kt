package com.things.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/** The outcome of Convert to Project. Source to-dos may only be removed after [Success]. */
sealed interface ConvertResult {
    /** Every project was created and registered. [warnings] are shown to the user and undo nothing. */
    data class Success(
        val projectIds: List<String>,
        val warnings: List<String> = emptyList(),
    ) : ConvertResult

    /** Nothing was changed. */
    data class Failure(val message: String) : ConvertResult
}

/**
 * What the select bar's Convert to Project does with the ticked to-dos: the owner of the data (the
 * navigation host) makes a project of each one, opens the first, and reports a [ConvertResult].
 * Null where nothing can take the to-dos, for example in a preview: Convert to Project then does
 * nothing.
 */
val LocalConvertTasksToProjects =
    staticCompositionLocalOf<((List<TaskRowModel>) -> ConvertResult)?> { null }
