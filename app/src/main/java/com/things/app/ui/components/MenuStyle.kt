package com.things.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsType

/**
 * The look of every dropdown menu of the app. It is the look of the three-dots menu at the top right
 * of a page: a 216dp wide card with 24dp rounded corners on the page color and a soft shadow, 48dp
 * high entries with a 24dp icon, a thin line between sections, and the page dimmed while it is open.
 * The page menu, the project title menu and the heading menu all use these pieces, so a change here
 * changes all of them.
 */
object ThingsMenu {
    val MinWidth = 216.dp
    val Offset = DpOffset(0.dp, 8.dp)
    val Shape = RoundedCornerShape(24.dp)
    val ItemMinHeight = 48.dp
    val IconSize = 24.dp
}

/**
 * The card of a dropdown menu. While it is [expanded] the page behind it is dimmed through the
 * nearest [MenuScrimHost]; pass [dimsPage] false when the page already dims itself for this menu.
 */
@Composable
fun ThingsDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dimsPage: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (dimsPage) ReportMenuOpen(expanded)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier.widthIn(min = ThingsMenu.MinWidth),
        offset = ThingsMenu.Offset,
        shape = ThingsMenu.Shape,
        containerColor = ThingsColors.Background,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        content = content,
    )
}

/**
 * One entry of a dropdown menu. [icon] draws the 24dp icon in the color the entry has (blue when it
 * can be chosen, grey when it cannot).
 */
@Composable
fun ThingsMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (Color) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, style = ThingsType.RowTitle.copy(color = Color.Unspecified)) },
        onClick = onClick,
        modifier = modifier.heightIn(min = ThingsMenu.ItemMinHeight),
        leadingIcon = {
            Box(Modifier.size(ThingsMenu.IconSize), contentAlignment = Alignment.Center) {
                icon(LocalContentColor.current)
            }
        },
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = ThingsColors.TextPrimary,
            leadingIconColor = ThingsColors.Fab,
            disabledTextColor = ThingsColors.TextSecondary,
            disabledLeadingIconColor = ThingsColors.TextSecondary,
        ),
    )
}

/** The thin line between two sections of a dropdown menu. */
@Composable
fun ThingsMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        color = ThingsColors.Divider,
    )
}

/** How many dropdown menus are open on a page; the page is dimmed while it is more than none. */
@Stable
class MenuScrimState {
    var openMenus by mutableIntStateOf(0)
        internal set

    val isOpen: Boolean get() = openMenus > 0
}

val LocalMenuScrim = staticCompositionLocalOf<MenuScrimState?> { null }

/** Tells the nearest [MenuScrimHost] that a menu is open while [open] is true. */
@Composable
fun ReportMenuOpen(open: Boolean) {
    val scrim = LocalMenuScrim.current ?: return
    DisposableEffect(scrim, open) {
        if (open) scrim.openMenus += 1
        onDispose { if (open) scrim.openMenus -= 1 }
    }
}

/**
 * Draws [content] and dims it with the same black (40%) the page menu uses while any menu inside it
 * is open. Wrap a page that has dropdown menus other than the one the page scaffold manages.
 */
@Composable
fun MenuScrimHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { MenuScrimState() }
    val alpha by animateFloatAsState(if (state.isOpen) 0.4f else 0f, label = "menuScrim")
    CompositionLocalProvider(LocalMenuScrim provides state) {
        Box(modifier) {
            content()
            if (alpha > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = alpha))
                )
            }
        }
    }
}
