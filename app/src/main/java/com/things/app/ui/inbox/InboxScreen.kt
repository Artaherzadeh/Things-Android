package com.things.app.ui.inbox

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.things.app.ui.components.ChecklistItemModel
import com.things.app.ui.components.FlatTaskListScreen
import com.things.app.ui.components.TaskDetailsModel
import com.things.app.ui.components.TaskRowModel
import com.things.app.ui.home.InboxIcon
import com.things.app.ui.theme.ThingsTheme

/** Presentation only. Inbox membership will be supplied by the data layer later. */
data class InboxUiState(val tasks: List<TaskRowModel> = emptyList())

private val tripNotes = listOf(
    "Campgrounds within a day's drive:",
    "",
    "1. *Lakeview Campground*",
    "- Boat rentals at the far end of the lake.",
    "- Open fires are allowed.",
    "",
    "2. *Mountain Ridge Retreat*",
    "- Sunrise views and hiking trails.",
    "- No dogs allowed.",
    "",
    "Check ::road and weather conditions:: shortly before the trip.",
).joinToString("\n")

fun sampleInboxState() = InboxUiState(
    tasks = listOf(
        TaskRowModel(
            "trip",
            "Choose a site for summer trip",
            hasNote = true,
            details = TaskDetailsModel(notes = tripNotes),
        ),
        TaskRowModel(
            "party",
            "Buy party decorations",
            hasTag = true,
            hasNote = true,
            hasChecklist = true,
            details = TaskDetailsModel(
                notes = "Robert might be able to bring a helium tank for the balloons.",
                checklist = listOf(
                    ChecklistItemModel("balloons", "Balloons", done = true),
                    ChecklistItemModel("hats", "Party hats"),
                    ChecklistItemModel("streamers", "Streamers"),
                ),
                tags = listOf("Errand"),
            ),
        ),
        TaskRowModel(
            "slides",
            "Update slide layouts",
            hasNote = true,
            hasChecklist = true,
            details = TaskDetailsModel(
                notes = "Our brand guidelines are on our website, but John will send an updated version.",
                checklist = listOf(
                    ChecklistItemModel("images", "Add more images to the introduction"),
                    ChecklistItemModel("idea", "Make the core idea clearer"),
                    ChecklistItemModel("logo", "Add our company logo"),
                ),
            ),
        ),
        TaskRowModel("pull-up", "Pull-up video"),
        TaskRowModel("contacts", "Sync my contacts with my watch"),
        TaskRowModel(
            "practice",
            "Videos for Purple Belt Practice",
            hasChecklist = true,
            details = TaskDetailsModel(
                checklist = listOf(
                    ChecklistItemModel("pick", "Pick the videos"),
                    ChecklistItemModel("download", "Download them"),
                    ChecklistItemModel("watch", "Watch on Sunday"),
                ),
            ),
        ),
        TaskRowModel("maps", "Get new map software for Nissan"),
        TaskRowModel(
            "article",
            "Good article",
            hasNote = true,
            details = TaskDetailsModel(notes = "Read it before the weekend. The **key idea** is near the end."),
        ),
        TaskRowModel("goals", "2018 Goals", hasChecklist = true),
        TaskRowModel("vehicle", "Vehicle issues", hasChecklist = true),
        TaskRowModel("movie", "Nacho Libre movie"),
    ),
)

/** Flat static list. Actions are callbacks only. */
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
    FlatTaskListScreen(
        title = "Inbox",
        icon = { InboxIcon(Modifier.size(28.dp)) },
        tasks = state.tasks,
        titleTag = "inbox_title",
        modifier = modifier,
        onBack = onBack,
        onMoreClick = onMoreClick,
        onAddClick = onAddClick,
        onTaskClick = onTaskClick,
        onToggleTask = onToggleTask,
    )
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
