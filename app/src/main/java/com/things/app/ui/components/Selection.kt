package com.things.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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
    Repeat("Repeat"),
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

// The inner corners of a box that sits in another one are the outer radius minus the space between
// them, so both curves have the same centre: 22dp outside and 6dp of padding give 16dp inside.
private val BarRadius = 22.dp
private val BarPadding = 6.dp
private val BarShape = RoundedCornerShape(BarRadius)
private val InnerShape = RoundedCornerShape(BarRadius - BarPadding)

private val MenuShape = RoundedCornerShape(24.dp)

/** Space between the bar and the menu above it. */
private val MenuGap = 8.dp

/** Empty space around the menu inside its window, so its shadow is not cut off. */
private val MenuShadowRoom = 12.dp

private val DeleteTint = Color(0xFFFF453A)

/**
 * The dark bar at the bottom of the select mode: When, Move and Delete (red), and a rounded square
 * button that opens the rest of the actions. Give it the width of the page minus its side margins:
 * the bar itself is only as wide as its content, centered in that room, and grows in height and
 * width with the text size. The icon of each button sits 4dp from its label and the buttons are 2dp
 * apart. Everything is dimmed while [enabled] is false (nothing ticked). [completeLabel] is the name
 * of the status entry of the menu (Mark as Completed, Undo or Switch Status). [onMenuOpenChange]
 * tells the page when the menu opens or closes so it can dim itself.
 *
 * The menu opens above the bar, 8dp over it, with its right edge in line with the right edge of the
 * bar.
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

    Box(modifier, contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .shadow(
                    elevation = 6.dp,
                    shape = BarShape,
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.16f),
                )
                .clip(BarShape)
                .background(ThingsColors.MenuSurface)
                .padding(BarPadding),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton("When", enabled, ThingsColors.MenuTitle, { onAction(SelectionAction.When) }) { tint ->
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = tint, modifier = iconSize)
            }
            BarButton("Move", enabled, ThingsColors.MenuTitle, { onAction(SelectionAction.Move) }) { tint ->
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = tint, modifier = iconSize)
            }
            BarButton("Delete", enabled, DeleteTint, { onAction(SelectionAction.Delete) }) { tint ->
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = tint, modifier = iconSize)
            }
            Box {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(InnerShape)
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
}

@Composable
private fun BarButton(
    label: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val tint = if (enabled) color else color.copy(alpha = 0.4f)
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(InnerShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
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

/**
 * Puts a popup above its anchor with its right edge [endInset] past the anchor's right edge and
 * [gap] between its bottom and the anchor's top. The popup window has [room] of empty space on every
 * side (for the shadow), which is taken into account here.
 */
private class AboveEndPositioner(
    private val endInset: Int,
    private val gap: Int,
    private val room: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val right = anchorBounds.right + endInset + room
        val x = (right - popupContentSize.width)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.top - gap - popupContentSize.height + room).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

@Composable
private fun MoreMenu(
    expanded: Boolean,
    completeLabel: String,
    onDismiss: () -> Unit,
    onAction: (SelectionAction) -> Unit,
) {
    if (!expanded) return

    val density = LocalDensity.current
    val positioner = remember(density) {
        with(density) {
            AboveEndPositioner(
                endInset = BarPadding.roundToPx(),
                gap = (BarPadding + MenuGap).roundToPx(),
                room = MenuShadowRoom.roundToPx(),
            )
        }
    }
    val groups: List<List<Pair<SelectionAction, ImageVector>>> = listOf(
        listOf(
            SelectionAction.MarkCompleted to Icons.Outlined.CheckBox,
            SelectionAction.SetTags to Icons.Outlined.LocalOffer,
            SelectionAction.SetDeadline to Icons.Outlined.Flag,
            SelectionAction.Repeat to Icons.Outlined.Repeat,
        ),
        listOf(
            SelectionAction.Duplicate to Icons.Outlined.ContentCopy,
            SelectionAction.ConvertToProject to Icons.Outlined.SwapHoriz,
        ),
        listOf(SelectionAction.Share to Icons.Outlined.Share),
    )

    Popup(
        popupPositionProvider = positioner,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .padding(MenuShadowRoom)
                .shadow(8.dp, MenuShape)
                .clip(MenuShape)
                .background(ThingsColors.MenuSurface)
                .widthIn(min = 216.dp)
                .width(IntrinsicSize.Max)
                .padding(vertical = 8.dp),
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
}
