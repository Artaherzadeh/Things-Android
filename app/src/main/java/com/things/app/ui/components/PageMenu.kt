package com.things.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.LocalContentColor
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

/** What the three-dots menu at the top right of a list page offers. */
enum class PageMenuAction(val label: String) {
    FilterByTag("Filter by Tag"),
    Select("Select"),
    Paste("Paste"),
    Share("Share"),
}

/** Filter by Tag stays greyed out until tags exist; the other actions are available. */
val DefaultEnabledMenuActions: Set<PageMenuAction> =
    setOf(PageMenuAction.Select, PageMenuAction.Paste, PageMenuAction.Share)

/**
 * The bar at the top of every list page: back button at the start and the three-dots button at
 * the end. The button opens the page menu. [onMenuAction] reports the chosen action.
 */
@Composable
fun PageTopBar(
    onBack: () -> Unit,
    onMoreClick: () -> Unit,
    onMenuAction: (PageMenuAction) -> Unit,
    enabledActions: Set<PageMenuAction>,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "Back",
                tint = ThingsColors.TextSecondary,
                modifier = Modifier.size(34.dp),
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
                MoreCircleIcon(Modifier.size(30.dp))
            }
            PageMenu(
                expanded = menuOpen,
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
    enabledActions: Set<PageMenuAction>,
    onDismiss: () -> Unit,
    onAction: (PageMenuAction) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 216.dp),
        offset = DpOffset(0.dp, 6.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = ThingsColors.Background,
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
    ) {
        PageMenuAction.entries.forEach { action ->
            DropdownMenuItem(
                text = {
                    Text(action.label, style = ThingsType.RowTitle.copy(color = Color.Unspecified))
                },
                onClick = { onAction(action) },
                modifier = Modifier.heightIn(min = 46.dp),
                leadingIcon = {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { ActionIcon(action) }
                },
                enabled = action in enabledActions,
                colors = MenuDefaults.itemColors(
                    textColor = ThingsColors.TextPrimary,
                    leadingIconColor = ThingsColors.Fab,
                    disabledTextColor = ThingsColors.TextSecondary,
                    disabledLeadingIconColor = ThingsColors.TextSecondary,
                ),
            )
        }
    }
}

@Composable
private fun ActionIcon(action: PageMenuAction) {
    val color = LocalContentColor.current
    val size = Modifier.size(22.dp)
    when (action) {
        PageMenuAction.FilterByTag -> TagIcon(size, color)
        PageMenuAction.Select -> SelectIcon(size, color)
        PageMenuAction.Paste -> PasteIcon(size, color)
        PageMenuAction.Share -> ShareIcon(size, color)
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
