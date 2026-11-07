package com.things.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.things.app.ui.home.ArchiveIcon

/** Actions available in the three-dots menu beside a heading in a project. */
enum class HeadingMenuAction(val label: String) {
    Archive("Archive"),
    Move("Move"),
    Duplicate("Duplicate"),
    ConvertToProject("Convert to a Project"),
    Delete("Delete"),
}

/** The entries of the heading menu that work today. Archive and Move have no flow yet, so they are greyed out. */
val DefaultEnabledHeadingActions: Set<HeadingMenuAction> = setOf(
    HeadingMenuAction.Duplicate,
    HeadingMenuAction.ConvertToProject,
    HeadingMenuAction.Delete,
)

private fun HeadingMenuAction.icon(): ImageVector = when (this) {
    HeadingMenuAction.Move -> Icons.AutoMirrored.Outlined.ArrowForward
    HeadingMenuAction.Duplicate -> Icons.Outlined.ContentCopy
    HeadingMenuAction.ConvertToProject -> Icons.Outlined.SwapHoriz
    HeadingMenuAction.Delete -> Icons.Outlined.Delete
    HeadingMenuAction.Archive -> Icons.Outlined.Delete
}

/**
 * The three-dots menu for a heading in a project. It looks like the page menu (see [ThingsMenu]) and
 * dims the page while it is open.
 * Section 1:
 *   Archive
 * Divider
 * Section 2:
 *   Move
 *   Duplicate
 *   Convert to a Project
 *   Delete
 */
@Composable
fun HeadingMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (HeadingMenuAction) -> Unit,
    modifier: Modifier = Modifier,
    enabledActions: Set<HeadingMenuAction> = DefaultEnabledHeadingActions,
) {
    ThingsDropdownMenu(expanded = expanded, onDismiss = onDismiss, modifier = modifier) {
        val archiveEnabled = HeadingMenuAction.Archive in enabledActions
        ThingsMenuItem(
            label = HeadingMenuAction.Archive.label,
            onClick = { onAction(HeadingMenuAction.Archive) },
            enabled = archiveEnabled,
        ) {
            // The Archive icon has its own colors (the same one the Someday list uses); a disabled
            // entry fades it like the other disabled icons of the menu.
            ArchiveIcon(Modifier.size(24.dp).alpha(if (archiveEnabled) 1f else ThingsMenu.DisabledAlpha))
        }

        ThingsMenuDivider()

        listOf(
            HeadingMenuAction.Move,
            HeadingMenuAction.Duplicate,
            HeadingMenuAction.ConvertToProject,
            HeadingMenuAction.Delete,
        ).forEach { action ->
            ThingsMenuItem(
                label = action.label,
                onClick = { onAction(action) },
                enabled = action in enabledActions,
            ) { color -> Icon(action.icon(), contentDescription = null, tint = color, modifier = Modifier.size(24.dp)) }
        }
    }
}
