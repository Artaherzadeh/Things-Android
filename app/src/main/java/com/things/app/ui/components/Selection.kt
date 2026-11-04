package com.things.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsType

/** What the bar at the bottom of the select mode can do with the selected to-dos. */
enum class SelectionAction(val label: String) {
    When("When"),
    Move("Move"),
    Delete("Delete"),
    MarkCompleted("Mark as Completed"),
    SetTags("Set Tags"),
    SetDeadline("Set Deadline"),
    Duplicate("Duplicate"),
    ConvertToProject("Convert to Project"),
    Share("Share"),
}

/**
 * The select mode of a list page: whether it is on and which to-dos are ticked. The page menu
 * entry Select turns it on; the Done button turns it off.
 */
@Stable
class SelectionState {
    var active: Boolean by mutableStateOf(false)
        private set

    var selected: Set<String> by mutableStateOf<Set<String>>(emptySet())
        private set

    fun start() {
        active = true
        selected = emptySet()
    }

    fun finish() {
        active = false
        selected = emptySet()
    }

    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun isSelected(id: String): Boolean = id in selected
}

@Composable
fun rememberSelectionState(): SelectionState = remember { SelectionState() }

/**
 * The select mode of the page the row is on. ListPageScaffold provides it, so every row that is an
 * ExpandableTaskRow can be ticked without the page passing anything.
 */
val LocalSelection = compositionLocalOf<SelectionState?> { null }

private val BarShape = RoundedCornerShape(28.dp)
private val MoreShape = RoundedCornerShape(14.dp)
private val DeleteTint = Color(0xFFFF453A)

/**
 * The dark bar at the bottom of the select mode: When, Move and Delete (red), and a rounded square
 * button that opens the rest of the actions. Give it the width of the page minus its side margins:
 * the three buttons share the room that is left and the bar grows in height with the text size, so
 * larger text never pushes it past the margins. The icon of each button sits 4dp from its label.
 * Everything is dimmed while [enabled] is false (nothing ticked). [completeLabel] is the name of the
 * status entry of the menu (Mark as Completed, Undo or Switch Status). [onMenuOpenChange] tells the
 * page when the menu opens or closes so it can dim itself.
 */
@Composable
fun SelectionBottomBar(
    enabled: Boolean,
    completeLabel: String,
    onAction: (SelectionAction) -> Unit,
    onMenuOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var moreOpen by remember { mutableStateOf(false) }
    val iconSize = Modifier.size(24.dp)

    LaunchedEffect(moreOpen) { onMenuOpenChange(moreOpen) }

    Row(
        modifier
            .shadow(
                elevation = 6.dp,
                shape = BarShape,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.16f),
            )
            .clip(BarShape)
            .background(ThingsColors.MenuSurface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton("When", enabled, ThingsColors.MenuTitle, Modifier.weight(1f), { onAction(SelectionAction.When) }) { tint ->
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = tint, modifier = iconSize)
        }
        BarButton("Move", enabled, ThingsColors.MenuTitle, Modifier.weight(1f), { onAction(SelectionAction.Move) }) { tint ->
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = tint, modifier = iconSize)
        }
        BarButton("Delete", enabled, DeleteTint, Modifier.weight(1f), { onAction(SelectionAction.Delete) }) { tint ->
            Icon(Icons.Outlined.Delete, contentDescription = null, tint = tint, modifier = iconSize)
        }
        Box {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(MoreShape)
                    .background(ThingsColors.MenuTitle.copy(alpha = 0.12f))
                    .clickable(enabled = enabled, onClickLabel = "More actions", role = Role.Button) {
                        moreOpen = true
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.MoreHoriz,
                    contentDescription = "More actions",
                    tint = if (enabled) ThingsColors.MenuTitle else ThingsColors.MenuTitle.copy(alpha = 0.4f),
                    modifier = iconSize,
                )
            }
            MoreMenu(
                expanded = moreOpen,
                completeLabel = completeLabel,
                onDismiss = { moreOpen = false },
                onAction = {
                    moreOpen = false
                    onAction(it)
                },
            )
        }
    }
}

@Composable
private fun BarButton(
    label: String,
    enabled: Boolean,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val tint = if (enabled) color else color.copy(alpha = 0.4f)
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(tint)
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = ThingsType.RowTitle.copy(color = tint, fontSize = 16.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MoreMenu(
    expanded: Boolean,
    completeLabel: String,
    onDismiss: () -> Unit,
    onAction: (SelectionAction) -> Unit,
) {
    val groups: List<List<Pair<SelectionAction, ImageVector>>> = listOf(
        listOf(
            SelectionAction.MarkCompleted to Icons.Outlined.CheckBox,
            SelectionAction.SetTags to Icons.Outlined.LocalOffer,
            SelectionAction.SetDeadline to Icons.Outlined.Flag,
        ),
        listOf(
            SelectionAction.Duplicate to Icons.Outlined.ContentCopy,
            SelectionAction.ConvertToProject to Icons.Outlined.SwapHoriz,
        ),
        listOf(SelectionAction.Share to Icons.Outlined.Share),
    )
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = ThingsColors.MenuSurface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        groups.forEachIndexed { index, items ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = ThingsColors.MenuTitle.copy(alpha = 0.15f),
                )
            }
            items.forEach { (action, icon) ->
                val label = if (action == SelectionAction.MarkCompleted) completeLabel else action.label
                DropdownMenuItem(
                    text = { Text(label, style = ThingsType.RowTitle.copy(color = Color.Unspecified)) },
                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                    onClick = { onAction(action) },
                    colors = MenuDefaults.itemColors(
                        textColor = ThingsColors.MenuTitle,
                        leadingIconColor = ThingsColors.Fab,
                    ),
                )
            }
        }
    }
}
