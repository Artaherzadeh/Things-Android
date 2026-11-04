package com.things.app.ui.inbox

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.things.app.ui.components.ListPageScaffold
import com.things.app.ui.components.TaskRow
import com.things.app.ui.components.TaskRowModel
import com.things.app.ui.home.InboxIcon
import com.things.app.ui.theme.ThingsTheme

/** Presentation only. Inbox membership will be supplied by the data layer later. */
data class InboxUiState(val tasks: List<TaskRowModel> = emptyList())

fun sampleInboxState() = InboxUiState(
    tasks = listOf(
        TaskRowModel("pull-up", "Pull-up video"),
        TaskRowModel("contacts", "Sync my contacts with my watch"),
        TaskRowModel("practice", "Videos for Purple Belt Practice", hasChecklist = true),
        TaskRowModel("maps", "Get new map software for Nissan"),
        TaskRowModel("article", "Good article", hasNote = true),
        TaskRowModel("goals", "2018 Goals", hasChecklist = true),
        TaskRowModel("vehicle", "Vehicle issues", hasChecklist = true),
        TaskRowModel("movie", "Nacho Libre movie"),
    ),
)

/** Flat static list: no project headers or grouping. Actions are callbacks only. */
@Composable
fun InboxScreen(
    state: InboxUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    onTaskClick: (String) -> Unit = {},
    onToggleTask: (String) -> Unit = {},
) {
    ListPageScaffold(
        title = "Inbox",
        icon = { InboxIcon(Modifier.size(28.dp)) },
        modifier = modifier,
        titleTag = "inbox_title",
        onBack = onBack,
        onMoreClick = onMoreClick,
        onAddClick = onAddClick,
    ) {
        items(state.tasks, key = { "inbox-task-${it.id}" }) { task ->
            TaskRow(
                task = task,
                onClick = { onTaskClick(task.id) },
                onToggle = { onToggleTask(task.id) },
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Inbox")
@Composable
private fun InboxPreview() {
    ThingsTheme { InboxScreen(sampleInboxState()) }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Inbox - empty")
@Composable
private fun EmptyInboxPreview() {
    ThingsTheme { InboxScreen(InboxUiState()) }
}
