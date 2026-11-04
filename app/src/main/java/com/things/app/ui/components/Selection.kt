package com.things.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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

// The bar has 6dp of padding. A shape that sits inside another one looks right when its corner
// radius is the outer radius minus that padding: 20dp outside, 14dp inside.
private val BarPadding = 6.dp
private val BarRadius = 20.dp
private val InnerRadius = BarRadius - BarPadding
private val BarShape = RoundedCornerShape(BarRadius)
private val InnerShape = RoundedCornerShape(InnerRadius)

/** Space between the bottom of the menu and the top of the bar. */
private val MenuGap = 8.dp
private val DeleteTint = Color(0xFFFF453A)

/**
 * The dark bar at the bottom of the select mode: When, Move and Delete (red), and a rounded square
 * button that opens the rest of the actions. The bar is as wide as its buttons need and no wider;
 * when the text is large the buttons shrink to fit the room [modifier] gives it (the page margins),
 * and the bar grows in height instead of passing them. The icon of each button sits 4dp from its
 * label. Everything is dimmed while [enabled] is false (nothing ticked). [completeLabel] is the name
 * of the status entry of the menu (Mark as Completed, Undo or Switch Status). [onMenuOpenChange]
 * tells the page when the menu opens or closes so it can dim itself.
 *
 * The menu opens above the bar, 8dp over it, with its right edge on the right edge of the bar.
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
        // The menu is anchored to this box, which is exactly as big as the bar.
        Box {
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
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarButton("When", enabled, ThingsColors.MenuTitle, Modifier.weight(1f, fill = false), { onAction(SelectionAction.When) }) { tint ->
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = tint, modifier = iconSize)
                }
                BarButton("Move", enabled, ThingsColors.MenuTitle, Modifier.weight(1f, fill = false), { onAction(SelectionAction.Move) }) { tint ->
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = tint, modifier = iconSize)
                }
                BarButton("Delete", enabled, DeleteTint, Modifier.weight(1f, fill = false), { onAction(SelectionAction.Delete) }) { tint ->
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = tint, modifier = iconSize)
                }
                Spacer(Modifier.width(2.dp))
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
            }
            if (moreOpen) {
                MoreMenu(
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
    modifier: Modifier,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val tint = if (enabled) color else color.copy(alpha = 0.4f)
    Row(
        modifier
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

/** Puts a popup above its anchor with the right edges on one line and [gap] pixels between them. */
private class AboveEndPositionProvider(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.right - popupContentSize.width).coerceAtLeast(0)
        val y = (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

@Composable
private fun MoreMenu(
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
    val gapPx = with(LocalDensity.current) { MenuGap.roundToPx() }
    val position = remember(gapPx) { AboveEndPositionProvider(gapPx) }

    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .widthIn(min = 240.dp)
                .clip(BarShape)
                .background(ThingsColors.MenuSurface)
                .padding(vertical = 6.dp),
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
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { onAction(action) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, contentDescription = null, tint = ThingsColors.Fab, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = ThingsType.RowTitle.copy(color = ThingsColors.MenuTitle))
                    }
                }
            }
        }
    }
}
