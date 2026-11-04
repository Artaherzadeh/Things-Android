package com.things.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsSpacing
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val RevealWidth = 80.dp
private val DeleteRed = Color(0xFFFF3B30)

/**
 * Swipe the row to the left: it slides 80dp and a red button with a white cross appears behind it.
 * Tapping the button calls [onDelete]. Swiping back, or tapping the row while it is open, closes it.
 */
@Composable
fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val revealPx = with(LocalDensity.current) { RevealWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val open = offsetX.value < -1f
    val dragState = rememberDraggableState { delta ->
        scope.launch { offsetX.snapTo((offsetX.value + delta).coerceIn(-revealPx, 0f)) }
    }

    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .padding(end = ThingsSpacing.PageMargin),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .graphicsLayer { alpha = (-offsetX.value / revealPx).coerceIn(0f, 1f) }
                    .clip(CircleShape)
                    .background(DeleteRed)
                    .clickable(enabled = open, onClickLabel = "Delete", role = Role.Button, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Box(
            Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(ThingsColors.Background)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = dragState,
                    onDragStopped = { velocity ->
                        val target = if (offsetX.value < -revealPx / 2f || velocity < -1500f) -revealPx else 0f
                        scope.launch { offsetX.animateTo(target) }
                    },
                ),
        ) {
            content()
            if (open) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { scope.launch { offsetX.animateTo(0f) } },
                )
            }
        }
    }
}
