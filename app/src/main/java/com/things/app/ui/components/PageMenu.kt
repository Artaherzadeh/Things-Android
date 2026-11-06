package com.things.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsType

/**
 * What the three-dots menu at the top right of a page can offer. Each page lists the ones it uses.
 * [Divider] is not an action: put it in the list where a thin line has to separate two sections.
 */
enum class PageMenuAction(val label: String) {
    FilterByTag("Filter by Tag"),
    NewHeading("New Heading"),
    Select("Select"),
    Paste("Paste"),
    Share("Share"),
    AddTags("Add Tags"),
    DeleteArea("Delete Area"),
    Divider(""),
}

/** The entries of the menu of a list page, in order. */
val DefaultMenuActions: List<PageMenuAction> =
    listOf(PageMenuAction.FilterByTag, PageMenuAction.Select, PageMenuAction.Paste, PageMenuAction.Share)

/** The entries of the menu of a project page, in order: Share is a section of its own. */
val ProjectMenuActions: List<PageMenuAction> = listOf(
    PageMenuAction.FilterByTag,
    PageMenuAction.NewHeading,
    PageMenuAction.Select,
    PageMenuAction.Paste,
    PageMenuAction.Divider,
    PageMenuAction.Share,
)

/** The entries of the menu of an area page, in order: three sections with a thin line between them. */
val AreaMenuActions: List<PageMenuAction> = listOf(
    PageMenuAction.AddTags,
    PageMenuAction.Divider,
    PageMenuAction.DeleteArea,
    PageMenuAction.Divider,
    PageMenuAction.Share,
)

/** Filter by Tag stays greyed out until tags exist; the other actions are available. */
val DefaultEnabledMenuActions: Set<PageMenuAction> =
    setOf(PageMenuAction.Select, PageMenuAction.Paste, PageMenuAction.Share)

/** The same for a project page, where New Heading is available too. */
val ProjectEnabledMenuActions: Set<PageMenuAction> = DefaultEnabledMenuActions + PageMenuAction.NewHeading

/**
 * The bar at the top of every list page: back button at the start and the three-dots button at
 * the end. The circle of the three-dots button ends 20dp from the screen edge, like the page
 * content. The button opens the page menu with the given [actions]; [onMenuAction] reports the
 * chosen one. [onMenuOpenChange] tells the page when the menu opens or closes so it can dim what
 * is behind it.
 */
@Composable
fun PageTopBar(
    onBack: () -> Unit,
    onMoreClick: () -> Unit,
    onMenuAction: (PageMenuAction) -> Unit,
    enabledActions: Set<PageMenuAction>,
    modifier: Modifier = Modifier,
    onMenuOpenChange: (Boolean) -> Unit = {},
    actions: List<PageMenuAction> = DefaultMenuActions,
) {
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(menuOpen) { onMenuOpenChange(menuOpen) }

    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "Back",
                tint = ThingsColors.TextSecondary,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(
                onClick = {
                    menuOpen = true
                    onMoreClick()
                },
                modifier = Modifier.semantics { contentDescription = "More" },
            ) {
                MoreCircleIcon(Modifier.size(32.dp))
            }
            PageMenu(
                expanded = menuOpen,
                actions = actions,
                enabledActions = enabledActions,
                onDismiss = { menuOpen = false },
                onAction = {
                    menuOpen = false
                    onMenuAction(it)
                },
            )
        }
    }
}

@Composable
private fun PageMenu(
    expanded: Boolean,
    actions: List<PageMenuAction>,
    enabledActions: Set<PageMenuAction>,
    onDismiss: () -> Unit,
    onAction: (PageMenuAction) -> Unit,
) {
    // The page scaffold dims the page for this menu itself (see onMenuOpenChange of PageTopBar).
    ThingsDropdownMenu(expanded = expanded, onDismiss = onDismiss, dimsPage = false) {
        actions.forEach { action ->
            if (action == PageMenuAction.Divider) {
                ThingsMenuDivider()
            } else {
                ThingsMenuItem(
                    label = action.label,
                    onClick = { onAction(action) },
                    enabled = action in enabledActions,
                ) { color -> ActionIcon(action, color) }
            }
        }
    }
}

@Composable
private fun ActionIcon(action: PageMenuAction, color: Color) {
    val size = Modifier.size(24.dp)
    when (action) {
        PageMenuAction.FilterByTag, PageMenuAction.AddTags -> TagIcon(size, color)
        PageMenuAction.NewHeading -> Icon(Icons.Outlined.Title, contentDescription = null, tint = color, modifier = size)
        PageMenuAction.Select -> SelectIcon(size, color)
        PageMenuAction.Paste -> PasteIcon(size, color)
        PageMenuAction.Share -> ShareIcon(size, color)
        PageMenuAction.DeleteArea -> Icon(Icons.Outlined.Delete, contentDescription = null, tint = color, modifier = size)
        PageMenuAction.Divider -> Unit
    }
}

/** Three lines with a column of dots: the list selection mode. */
@Composable
fun SelectIcon(modifier: Modifier = Modifier, color: Color = ThingsColors.TextSecondary) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val strokeWidth = w * 0.09f
    listOf(0.2f to 0.6f, 0.5f to 0.5f, 0.8f to 0.6f).forEach { (y, end) ->
        drawLine(
            color,
            Offset(w * 0.08f, h * y),
            Offset(w * end, h * y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
    drawCircle(color, radius = w * 0.1f, center = Offset(w * 0.82f, h * 0.2f))
    drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.82f, h * 0.5f), style = Stroke(strokeWidth))
    drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.82f, h * 0.8f), style = Stroke(strokeWidth))
}

/** A sheet in front of another sheet: paste. */
@Composable
fun PasteIcon(modifier: Modifier = Modifier, color: Color = ThingsColors.TextSecondary) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color,
        topLeft = Offset(w * 0.1f, h * 0.2f),
        size = Size(w * 0.5f, h * 0.7f),
        cornerRadius = CornerRadius(w * 0.12f),
        style = Stroke(w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    drawRoundRect(
        color,
        topLeft = Offset(w * 0.4f, h * 0.08f),
        size = Size(w * 0.5f, h * 0.66f),
        cornerRadius = CornerRadius(w * 0.12f),
    )
}

/** An open box with an arrow pointing up out of it: share. */
@Composable
fun ShareIcon(modifier: Modifier = Modifier, color: Color = ThingsColors.TextSecondary) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val box = Path().apply {
        moveTo(w * 0.3f, h * 0.4f)
        lineTo(w * 0.15f, h * 0.4f)
        lineTo(w * 0.15f, h * 0.9f)
        lineTo(w * 0.85f, h * 0.9f)
        lineTo(w * 0.85f, h * 0.4f)
        lineTo(w * 0.7f, h * 0.4f)
    }
    val arrow = Path().apply {
        moveTo(w * 0.5f, h * 0.6f)
        lineTo(w * 0.5f, h * 0.1f)
        moveTo(w * 0.32f, h * 0.28f)
        lineTo(w * 0.5f, h * 0.1f)
        lineTo(w * 0.68f, h * 0.28f)
    }
    drawPath(box, color, style = stroke)
    drawPath(arrow, color, style = stroke)
}
