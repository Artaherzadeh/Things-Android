package com.things.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the select bar's Convert to Project does with the ticked to-dos: the owner of the data (the
 * navigation host) makes a project of each one and opens the first. Null where nothing can take the
 * to-dos, for example in a preview: Convert to Project then does nothing.
 */
val LocalConvertTasksToProjects = staticCompositionLocalOf<((List<TaskRowModel>) -> Unit)?> { null }
