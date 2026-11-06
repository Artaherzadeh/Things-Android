package com.things.app.ui.components

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A list page that shows to-dos as one flat list: no project headers and no grouping.
 * Used by Inbox and Someday. Every to-do opens in place when tapped. Every action is a callback
 * that defaults to a no-op.
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
    val expansion = rememberTaskExpansion()

    ListPageScaffold(
        title = title,
        icon = icon,
        modifier = modifier,
        titleTag = titleTag,
        onBack = onBack,
        onMoreClick = onMoreClick,
        expansion = expansion,
        onAddClick = onAddClick,
        shareableTasks = { tasks },
    ) {
        items(tasks, key = { "task-${it.id}" }) { task ->
            ExpandableTaskRow(
                task = task,
                expansion = expansion,
                onOpen = { onTaskClick(task.id) },
                onToggle = { onToggleTask(task.id) },
            )
        }
    }
}
