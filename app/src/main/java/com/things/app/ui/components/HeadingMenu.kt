package com.things.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.things.app.ui.home.ArchiveIcon
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsType

/** Actions available in the three-dots menu beside a heading in a project. */
enum class HeadingMenuAction(val label: String) {
    Archive("Archive"),
    Move("Move"),
    Duplicate("Duplicate"),
    ConvertToProject("Convert to a Project"),
    Delete("Delete"),
}

/**
 * The three-dots menu for a heading in a project.
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
    enabledActions: Set<HeadingMenuAction> = setOf(
        HeadingMenuAction.Duplicate,
        HeadingMenuAction.ConvertToProject,
        HeadingMenuAction.Delete,
    ),
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier.widthIn(min = 216.dp),
        offset = DpOffset(0.dp, 8.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = ThingsColors.Background,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
    ) {
        // Section 1: Archive
        DropdownMenuItem(
            text = { Text(HeadingMenuAction.Archive.label, style = ThingsType.RowTitle.copy(color = Color.Unspecified)) },
            onClick = { onAction(HeadingMenuAction.Archive) },
            modifier = Modifier.heightIn(min = 48.dp),
            leadingIcon = {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    ArchiveIcon(Modifier.size(24.dp))
                }
            },
            enabled = HeadingMenuAction.Archive in enabledActions,
            colors = MenuDefaults.itemColors(
                textColor = ThingsColors.TextPrimary,
                leadingIconColor = ThingsColors.Fab,
                disabledTextColor = ThingsColors.TextSecondary.copy(alpha = 0.4f),
                disabledLeadingIconColor = ThingsColors.TextSecondary.copy(alpha = 0.4f),
            ),
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = ThingsColors.Divider,
        )

        // Section 2: Move, Duplicate, Convert to a Project, Delete
        val section2 = listOf(
            HeadingMenuAction.Move to Icons.AutoMirrored.Outlined.ArrowForward,
            HeadingMenuAction.Duplicate to Icons.Outlined.ContentCopy,
            HeadingMenuAction.ConvertToProject to Icons.Outlined.SwapHoriz,
            HeadingMenuAction.Delete to Icons.Outlined.Delete,
        )

        section2.forEach { (action, icon) ->
            val isDelete = action == HeadingMenuAction.Delete
            DropdownMenuItem(
                text = { Text(action.label, style = ThingsType.RowTitle.copy(color = Color.Unspecified)) },
                onClick = { onAction(action) },
                modifier = Modifier.heightIn(min = 48.dp),
                leadingIcon = {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                },
                enabled = action in enabledActions,
                colors = MenuDefaults.itemColors(
                    textColor = if (isDelete) Color(0xFFFF3B30) else ThingsColors.TextPrimary,
                    leadingIconColor = if (isDelete) Color(0xFFFF3B30) else ThingsColors.Fab,
                    disabledTextColor = ThingsColors.TextSecondary.copy(alpha = 0.4f),
                    disabledLeadingIconColor = ThingsColors.TextSecondary.copy(alpha = 0.4f),
                ),
            )
        }
    }
}
