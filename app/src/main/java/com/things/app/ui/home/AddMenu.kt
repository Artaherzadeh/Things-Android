package com.things.app.ui.home

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.things.app.ui.components.PlusGlyph
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsType
import kotlin.math.roundToInt

/** What the person picked in the add menu. */
enum class CreationType { ToDo, Project, Area }

// Motion: the + button and the menu are one surface, driven by a single 0f..1f progress value.
private const val OpenMillis = 320
private const val CloseMillis = 260
private val MenuEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

internal fun addMenuSpec(opening: Boolean): AnimationSpec<Float> =
    tween(durationMillis = if (opening) OpenMillis else CloseMillis, easing = MenuEasing)

// Geometry. Every value is a multiple of 4dp; the button radius is half of the 52dp button, so it
// stays a circle.
private val PanelMaxWidth = 340.dp
private val PanelEdgeMargin = 16.dp
private val FabCornerRadius = 26.dp
private val PanelCornerRadius = 24.dp
private val FabElevation = 8.dp
private val PanelElevation = 16.dp
internal val FabShadowColor = Color(0x334F8EEA)

/**
 * The "+" shown on the floating button: the same glyph, at the same size, as the add button of
 * every other page. Shared by the button and the morphing surface.
 */
@Composable
internal fun AddFabGlyph(modifier: Modifier = Modifier, contentDescription: String? = "Add") {
    val description = contentDescription
    Box(
        modifier.then(
            if (description != null) Modifier.semantics { this.contentDescription = description } else Modifier
        ),
        contentAlignment = Alignment.Center,
    ) {
        PlusGlyph(Modifier.size(24.dp))
    }
}

/**
 * Scrim plus a single surface that grows from [anchor] (the + button's bounds, in this overlay's
 * coordinates) into the menu panel, keeping the anchored bottom corner fixed.
 *
 * @param progress 0f = exactly the + button, 1f = fully open menu.
 * @param expanded the target state. Menu actions only react while this is true.
 */
@Composable
internal fun AddMenuOverlay(
    progress: State<Float>,
    expanded: Boolean,
    anchor: Rect,
    onDismiss: () -> Unit,
    onSelect: (CreationType) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val anchorEnd = layoutDirection == LayoutDirection.Ltr
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.getTop(density)

    // Rows ignore taps until the content is mostly visible, and never while closing.
    val interactive by remember(expanded) {
        derivedStateOf { expanded && progress.value >= 0.6f }
    }

    // Scrim: dims Home and swallows taps so nothing underneath is activated.
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress.value }
            .drawBehind { drawRect(ThingsColors.Scrim) }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Close menu",
                role = Role.Button,
                onClick = onDismiss,
            )
            .semantics { contentDescription = "Close menu" }
    )

    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            // Child 0: the morphing surface. At progress 0 it is the + button.
            Box(
                Modifier
                    .graphicsLayer {
                        val p = progress.value
                        shape = RoundedCornerShape(lerp(FabCornerRadius.toPx(), PanelCornerRadius.toPx(), p))
                        clip = true
                        shadowElevation = lerp(FabElevation.toPx(), PanelElevation.toPx(), p)
                        val shadow = lerpColor(FabShadowColor, Color.Black, p)
                        ambientShadowColor = shadow
                        spotShadowColor = shadow
                    }
                    .drawBehind {
                        drawRect(lerpColor(ThingsColors.Fab, ThingsColors.MenuSurface, progress.value))
                    }
                    // Taps on the panel itself must not reach the scrim and dismiss it.
                    .pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = Alignment.Center,
            ) {
                AddFabGlyph(
                    modifier = Modifier.graphicsLayer {
                        alpha = (1f - progress.value * 4f).coerceIn(0f, 1f)
                    },
                    contentDescription = null,
                )
            }

            // Child 1: the menu content, laid out at its final size and revealed by a growing clip.
            Box(
                Modifier
                    .semantics { paneTitle = "Create" }
                    .graphicsLayer {
                        val p = progress.value
                        shape = RevealShape(
                            visibleWidth = lerp(anchor.width, size.width, p),
                            visibleHeight = lerp(anchor.height, size.height, p),
                            radius = lerp(FabCornerRadius.toPx(), PanelCornerRadius.toPx(), p),
                            anchorEnd = anchorEnd,
                        )
                        clip = true
                    }
            ) {
                MenuContent(
                    expanded = expanded,
                    interactive = interactive,
                    onSelect = onSelect,
                    modifier = Modifier.graphicsLayer {
                        val reveal = ((progress.value - 0.3f) / 0.5f).coerceIn(0f, 1f)
                        alpha = reveal
                        translationY = (1f - reveal) * 8.dp.toPx()
                    },
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val margin = PanelEdgeMargin.roundToPx()

        val panelWidth = minOf(PanelMaxWidth.roundToPx(), width - 2 * margin).coerceAtLeast(0)
        val maxPanelHeight = (anchor.bottom.roundToInt() - topInset - margin).coerceAtLeast(0)
        val content = measurables[1].measure(
            Constraints(
                minWidth = panelWidth,
                maxWidth = panelWidth,
                minHeight = 0,
                maxHeight = maxPanelHeight,
            )
        )

        // Expanded rect keeps the button's bottom corner fixed.
        val rawLeft = if (anchorEnd) anchor.right - panelWidth else anchor.left
        val maxLeft = maxOf(margin, width - margin - panelWidth).toFloat()
        val expandedLeft = rawLeft.coerceIn(margin.toFloat(), maxLeft)
        val expandedRight = expandedLeft + panelWidth
        val expandedTop = anchor.bottom - content.height

        val p = progress.value
        val left = lerp(anchor.left, expandedLeft, p)
        val top = lerp(anchor.top, expandedTop, p)
        val right = lerp(anchor.right, expandedRight, p)
        val bottom = anchor.bottom

        val surface = measurables[0].measure(
            Constraints.fixed(
                (right - left).roundToInt().coerceAtLeast(0),
                (bottom - top).roundToInt().coerceAtLeast(0),
            )
        )

        layout(width, height) {
            surface.place(left.roundToInt(), top.roundToInt())
            content.place(expandedLeft.roundToInt(), expandedTop.roundToInt())
        }
    }
}

/** Clips to a rounded rectangle pinned to the bottom corner of the layer, growing with progress. */
private class RevealShape(
    private val visibleWidth: Float,
    private val visibleHeight: Float,
    private val radius: Float,
    private val anchorEnd: Boolean,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val left = if (anchorEnd) size.width - visibleWidth else 0f
        val top = size.height - visibleHeight
        return Outline.Rounded(
            RoundRect(
                left = left,
                top = top,
                right = left + visibleWidth,
                bottom = top + visibleHeight,
                cornerRadius = CornerRadius(radius),
            )
        )
    }
}

@Composable
private fun MenuContent(
    expanded: Boolean,
    interactive: Boolean,
    onSelect: (CreationType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
            // While closing, hide the actions from accessibility services.
            .then(if (expanded) Modifier else Modifier.clearAndSetSemantics { }),
    ) {
        MenuRow(
            title = "New To-Do",
            description = "Quickly add a to-do to your inbox.",
            enabled = interactive,
            onClick = { onSelect(CreationType.ToDo) },
            icon = {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = ThingsColors.MenuTitle,
                    modifier = Modifier.size(24.dp),
                )
            },
        )
        MenuRow(
            title = "New Project",
            description = "Define a goal, then work towards it one to-do at a time.",
            enabled = interactive,
            onClick = { onSelect(CreationType.Project) },
            icon = { ProgressIcon(progress = 0.7f, modifier = Modifier.size(24.dp), color = ThingsColors.MenuProject) },
        )
        MenuRow(
            title = "New Area",
            description = "Group projects and to-dos based on different responsibilities, such as Family or Work.",
            enabled = interactive,
            onClick = { onSelect(CreationType.Area) },
            icon = { AreaIcon(modifier = Modifier.size(24.dp), color = ThingsColors.MenuArea) },
        )
    }
}

@Composable
private fun MenuRow(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Icon slot matches the title's line height so the icon aligns with the first line.
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = ThingsType.MenuTitle)
            Text(description, style = ThingsType.MenuDescription)
        }
    }
}
