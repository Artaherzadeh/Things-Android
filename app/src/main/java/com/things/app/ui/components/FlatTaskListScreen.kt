package com.things.app.ui.components

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A list page that shows to-dos as one flat list: no project headers and no grouping.
 * Used by Inbox and Someday. Every action is a callback that defaults to a no-op.
 */
@Composable
fun FlatTaskListScreen(
    title: String,
    icon: @Composable () -> Unit,
    tasks: List<TaskRowModel>,
    titleTag: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    onTaskClick: (String) -> Unit = {},
    onToggleTask: (String) -> Unit = {},
) {
    ListPageScaffold(
        title = title,
        icon = icon,
        modifier = modifier,
        titleTag = titleTag,
        onBack = onBack,
        onMoreClick = onMoreClick,
        onAddClick = onAddClick,
    ) {
        items(tasks, key = { "task-${it.id}" }) { task ->
            TaskRow(
                task = task,
                onClick = { onTaskClick(task.id) },
                onToggle = { onToggleTask(task.id) },
            )
        }
    }
}
