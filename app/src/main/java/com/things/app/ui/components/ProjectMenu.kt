package com.things.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** What the menu next to a project's title offers. */
enum class ProjectMenuAction(val label: String) {
    CompleteProject("Complete Project"),
    When("When"),
    AddTags("Add Tags"),
    AddDeadline("Add Deadline"),
    Repeat("Repeat"),
    Move("Move"),
    Duplicate("Duplicate"),
    DeleteProject("Delete Project"),
    Share("Share"),
}

/** The entries of the project menu: three sections with a thin line between them. */
val ProjectMenuGroups: List<List<ProjectMenuAction>> = listOf(
    listOf(
        ProjectMenuAction.CompleteProject,
        ProjectMenuAction.When,
        ProjectMenuAction.AddTags,
        ProjectMenuAction.AddDeadline,
        ProjectMenuAction.Repeat,
    ),
    listOf(
        ProjectMenuAction.Move,
        ProjectMenuAction.Duplicate,
        ProjectMenuAction.DeleteProject,
    ),
    listOf(ProjectMenuAction.Share),
)

private fun ProjectMenuAction.icon(): ImageVector = when (this) {
    ProjectMenuAction.CompleteProject -> Icons.Outlined.CheckBox
    ProjectMenuAction.When -> Icons.Outlined.CalendarMonth
    ProjectMenuAction.AddTags -> Icons.Outlined.LocalOffer
    ProjectMenuAction.AddDeadline -> Icons.Outlined.Flag
    ProjectMenuAction.Repeat -> Icons.Outlined.Repeat
    ProjectMenuAction.Move -> Icons.AutoMirrored.Outlined.ArrowForward
    ProjectMenuAction.Duplicate -> Icons.Outlined.ContentCopy
    ProjectMenuAction.DeleteProject -> Icons.Outlined.Delete
    ProjectMenuAction.Share -> Icons.Outlined.Share
}

/** The icon of an entry: the same Tag and Share icons as the page menu, outlined icons for the rest. */
@Composable
private fun ProjectMenuIcon(action: ProjectMenuAction, color: Color) {
    val size = Modifier.size(24.dp)
    when (action) {
        ProjectMenuAction.AddTags -> TagIcon(size, color)
        ProjectMenuAction.Share -> ShareIcon(size, color)
        else -> Icon(action.icon(), contentDescription = null, tint = color, modifier = size)
    }
}

/**
 * The grey dots right after a project's title, with the menu they open. The menu has three sections:
 * Complete Project, When, Add Tags, Add Deadline and Repeat; then Move, Duplicate and Delete Project;
 * then Share. It looks like the page menu (see [ThingsMenu]) and dims the page while it is open.
 * [onOpen] is called when the menu opens and [onAction] gets the chosen entry.
 */
@Composable
fun ProjectTitleMenu(onOpen: () -> Unit = {}, onAction: (ProjectMenuAction) -> Unit) {
    var open by remember { mutableStateOf(false) }

    Box {
        TitleMenuButton(
            description = "Project menu",
            onClick = {
                open = true
                onOpen()
            },
        )
        ThingsDropdownMenu(expanded = open, onDismiss = { open = false }) {
            ProjectMenuGroups.forEachIndexed { index, group ->
                if (index > 0) ThingsMenuDivider()
                group.forEach { action ->
                    ThingsMenuItem(
                        label = action.label,
                        onClick = {
                            open = false
                            onAction(action)
                        },
                    ) { color -> ProjectMenuIcon(action, color) }
                }
            }
        }
    }
}
