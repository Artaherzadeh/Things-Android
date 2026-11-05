package com.things.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsType

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
private val ProjectMenuGroups: List<List<ProjectMenuAction>> = listOf(
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

/**
 * The grey dots right after a project's title, with the menu they open. The menu has three sections:
 * Complete Project, When, Add Tags, Add Deadline and Repeat; then Move, Duplicate and Delete Project;
 * then Share. [onOpen] is called when the menu opens and [onAction] gets the chosen entry.
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
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.widthIn(min = 216.dp),
            offset = DpOffset(0.dp, 0.dp),
            shape = RoundedCornerShape(24.dp),
            containerColor = ThingsColors.Background,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            ProjectMenuGroups.forEachIndexed { index, group ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = ThingsColors.Divider,
                    )
                }
                group.forEach { action ->
                    DropdownMenuItem(
                        text = {
                            Text(action.label, style = ThingsType.RowTitle.copy(color = Color.Unspecified))
                        },
                        onClick = {
                            open = false
                            onAction(action)
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        leadingIcon = {
                            Icon(action.icon(), contentDescription = null, modifier = Modifier.size(24.dp))
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = ThingsColors.TextPrimary,
                            leadingIconColor = ThingsColors.Fab,
                        ),
                    )
                }
            }
        }
    }
}
