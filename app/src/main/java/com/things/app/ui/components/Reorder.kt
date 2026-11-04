package com.things.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.things.app.ui.theme.ThingsColors

private val LiftShape = RoundedCornerShape(12.dp)

/**
 * Press-and-hold reordering for the rows of one LazyColumn. Hold a row: it lifts and follows the
 * finger. While it moves, [onMove] is called each time it passes the middle of another row that
 * [canMove] accepts. The page owns the data; this class only tracks the finger.
 * Edge auto-scroll is not implemented.
 */
@Stable
class ReorderState internal constructor(private val listState: LazyListState) {
    var draggedKey: Any? by mutableStateOf<Any?>(null)
        private set

    private var fingerY by mutableFloatStateOf(0f)
    private var grabDy = 0f
    private var awaitingOrder: List<Any>? = null

    var canMove: (from: Any, to: Any) -> Boolean = { _, _ -> false }
    var onMove: (from: Any, to: Any) -> Unit = { _, _ -> }
    var onStart: (key: Any) -> Unit = {}
    var onEnd: () -> Unit = {}

    /** How far the lifted row has to be drawn from where the list laid it out. */
    internal fun translationFor(key: Any): Float {
        if (key != draggedKey) return 0f
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
        return fingerY - grabDy - info.offset
    }

    internal fun start(key: Any, localY: Float) {
        if (draggedKey != null) return
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        awaitingOrder = null
        grabDy = localY
        fingerY = info.offset + localY
        draggedKey = key
        onStart(key)
    }

    internal fun drag(dy: Float) {
        val key = draggedKey ?: return
        fingerY += dy
        val items = listState.layoutInfo.visibleItemsInfo
        val order = items.map { it.key }
        if (awaitingOrder == order) return
        awaitingOrder = null
        val me = items.firstOrNull { it.key == key } ?: return
        val center = fingerY - grabDy + me.size / 2f
        val down = items.lastOrNull {
            it.offset > me.offset && center > it.offset + it.size / 2f && canMove(key, it.key)
        }
        val up = items.firstOrNull {
            it.offset < me.offset && center < it.offset + it.size / 2f && canMove(key, it.key)
        }
        val target = down?.key ?: up?.key ?: return
        awaitingOrder = order
        onMove(key, target)
    }

    internal fun end() {
        if (draggedKey == null) return
        draggedKey = null
        awaitingOrder = null
        onEnd()
    }
}

@Composable
fun rememberReorderState(listState: LazyListState): ReorderState =
    remember(listState) { ReorderState(listState) }

/** Makes a row of the list draggable after a long press. [key] must be the key of its LazyColumn item. */
fun Modifier.reorderable(state: ReorderState, key: Any): Modifier {
    val dragged = state.draggedKey == key
    return this
        .zIndex(if (dragged) 1f else 0f)
        .graphicsLayer {
            if (state.draggedKey == key) {
                translationY = state.translationFor(key)
                scaleX = 1.03f
                scaleY = 1.03f
                shadowElevation = 12.dp.toPx()
                shape = LiftShape
                clip = false
            }
        }
        .then(if (dragged) Modifier.background(ThingsColors.Background, LiftShape) else Modifier)
        .pointerInput(key) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset -> state.start(key, offset.y) },
                onDrag = { change, amount ->
                    change.consume()
                    state.drag(amount.y)
                },
                onDragEnd = { state.end() },
                onDragCancel = { state.end() },
            )
        }
}
