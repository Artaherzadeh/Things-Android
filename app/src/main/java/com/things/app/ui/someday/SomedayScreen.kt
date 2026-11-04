package com.things.app.ui.someday

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.things.app.ui.components.FlatTaskListScreen
import com.things.app.ui.components.TaskRowModel
import com.things.app.ui.home.ArchiveIcon
import com.things.app.ui.theme.ThingsTheme

/** Presentation only. Which to-dos belong to Someday will be decided by the data layer later. */
data class SomedayUiState(val tasks: List<TaskRowModel> = emptyList())

fun sampleSomedayState() = SomedayUiState(
    tasks = listOf(
        TaskRowModel("piano", "Learn to play the piano"),
        TaskRowModel("trip", "Plan a trip to Japan", hasNote = true),
        TaskRowModel("novel", "Read War and Peace"),
        TaskRowModel("shelf", "Build a bookshelf", hasChecklist = true),
        TaskRowModel("pottery", "Try a pottery class"),
    ),
)

/** Flat static list. Actions are callbacks only. */
@Composable
fun SomedayScreen(
    state: SomedayUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    onTaskClick: (String) -> Unit = {},
    onToggleTask: (String) -> Unit = {},
) {
    FlatTaskListScreen(
        title = "Someday",
        icon = { ArchiveIcon(Modifier.size(28.dp)) },
        tasks = state.tasks,
        titleTag = "someday_title",
        modifier = modifier,
        onBack = onBack,
        onMoreClick = onMoreClick,
        onAddClick = onAddClick,
        onTaskClick = onTaskClick,
        onToggleTask = onToggleTask,
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Someday")
@Composable
private fun SomedayPreview() {
    ThingsTheme { SomedayScreen(sampleSomedayState()) }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Someday - empty")
@Composable
private fun EmptySomedayPreview() {
    ThingsTheme { SomedayScreen(SomedayUiState()) }
}
