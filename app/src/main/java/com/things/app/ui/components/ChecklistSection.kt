package com.things.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsSpacing
import com.things.app.ui.theme.ThingsType
import java.util.UUID
import kotlin.math.roundToInt

private val ItemTextStyle = ThingsType.RowTitle.copy(fontSize = 17.sp)
private val FocusShape = RoundedCornerShape(12.dp)
private val ItemKeyboard = KeyboardOptions(
    capitalization = KeyboardCapitalization.Sentences,
    imeAction = ImeAction.Next,
)

private fun <T> List<T>.swap(a: Int, b: Int): List<T> =
    toMutableList().also {
        val tmp = it[a]
        it[a] = it[b]
        it[b] = tmp
    }

/**
 * The checklist of an open to-do: one line per item with a circle, the text and a drag handle.
 * Geometry of a line (44dp high): 8dp of space, a 24dp tap area holding the 16dp circle, 12dp of
 * space, the text, then the 24dp handle and 8dp of space. Place the section so that the circle lines
 * up with the to-do title.
 *
 * The line being edited, new or old, has a grey rounded box behind it. Text starts with a capital.
 * Enter (Next on the keyboard) saves the line and opens a new empty one right under it; an empty
 * line is dropped when the cursor leaves it. When [adding] is true an empty line at the end takes
 * new items.
 *
 * Backspace on a line that is already empty deletes that line and puts the cursor at the end of
 * the line above it. Press and drag the handle to move a line up or down.
 */
@Composable
fun ChecklistSection(
    items: List<ChecklistItemModel>,
    onItemsChange: (List<ChecklistItemModel>) -> Unit,
    adding: Boolean,
    onAddingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var focusAtEnd by remember { mutableStateOf<String?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    val currentItems by rememberUpdatedState(items)
    val currentChange by rememberUpdatedState(onItemsChange)
    val rowPx = with(LocalDensity.current) { ThingsSpacing.RowHeight.toPx() }.roundToInt()

    Column(modifier) {
        items.forEachIndexed { index, item ->
            key(item.id) {
                ChecklistRow(
                    item = item,
                    requester = requesters.getOrPut(item.id) { FocusRequester() },
                    focusAtEnd = focusAtEnd == item.id,
                    onFocusHandled = { focusAtEnd = null },
                    onChange = { updated ->
                        onItemsChange(items.map { if (it.id == item.id) updated else it })
                    },
                    onBackspaceOnEmpty = {
                        onItemsChange(items.filter { it.id != item.id })
                        focusAtEnd = items.getOrNull(index - 1)?.id
                    },
                    onEnter = {
                        val fresh = ChecklistItemModel(UUID.randomUUID().toString(), "")
                        onItemsChange(items.take(index + 1) + fresh + items.drop(index + 1))
                        focusAtEnd = fresh.id
                    },
                    onBlurBlank = { onItemsChange(items.filter { it.id != item.id }) },
                    dragging = draggingId == item.id,
                    dragOffset = if (draggingId == item.id) dragY else 0f,
                    onHeight = { heights[item.id] = it },
                    onDragStart = {
                        draggingId = item.id
                        dragY = 0f
                    },
                    onDrag = { dy ->
                        val list = currentItems
                        val at = list.indexOfFirst { it.id == item.id }
                        dragY += dy
                        if (at >= 0) {
                            if (dragY > 0f && at < list.lastIndex) {
                                val h = (heights[list[at + 1].id] ?: rowPx).toFloat()
                                if (dragY > h / 2f) {
                                    currentChange(list.swap(at, at + 1))
                                    dragY -= h
                                }
                            } else if (dragY < 0f && at > 0) {
                                val h = (heights[list[at - 1].id] ?: rowPx).toFloat()
                                if (dragY < -h / 2f) {
                                    currentChange(list.swap(at, at - 1))
                                    dragY += h
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        draggingId = null
                        dragY = 0f
                    },
                )
            }
        }
        if (adding) {
            NewChecklistRow(
                onCommit = { text ->
                    onItemsChange(items + ChecklistItemModel(UUID.randomUUID().toString(), text.trim()))
                },
                onDismiss = { onAddingChange(false) },
                onBackspaceOnEmpty = {
                    onAddingChange(false)
                    focusAtEnd = items.lastOrNull()?.id
                },
            )
        }
    }
}

@Composable
private fun ChecklistRow(
    item: ChecklistItemModel,
    requester: FocusRequester,
    focusAtEnd: Boolean,
    onFocusHandled: () -> Unit,
    onChange: (ChecklistItemModel) -> Unit,
    onBackspaceOnEmpty: () -> Unit,
    onEnter: () -> Unit,
    onBlurBlank: () -> Unit,
    dragging: Boolean,
    dragOffset: Float,
    onHeight: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(item.title, TextRange(item.title.length))) }
    var focused by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentDragEnd by rememberUpdatedState(onDragEnd)

    LaunchedEffect(item.title) {
        if (value.text != item.title) value = TextFieldValue(item.title, TextRange(item.title.length))
    }
    LaunchedEffect(focusAtEnd) {
        if (focusAtEnd) {
            requester.requestFocus()
            value = value.copy(selection = TextRange(value.text.length))
            onFocusHandled()
        }
    }

    Row(
        Modifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffset
                if (dragging) shadowElevation = 8.dp.toPx()
            }
            .then(if (dragging) Modifier.background(ThingsColors.Background) else Modifier)
            .onSizeChanged { onHeight(it.height) }
            .fillMaxWidth()
            .then(
                if (focused) {
                    Modifier
                        .clip(FocusShape)
                        .background(ThingsColors.SearchBackground)
                } else {
                    Modifier
                }
            )
            .heightIn(min = ThingsSpacing.RowHeight)
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChecklistMark(
            done = item.done,
            description = item.title,
            onClick = { onChange(item.copy(done = !item.done)) },
        )
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = value,
            onValueChange = {
                value = it
                if (it.text != item.title) onChange(item.copy(title = it.text))
            },
            singleLine = true,
            textStyle = ItemTextStyle.copy(
                color = if (item.done) ThingsColors.TextSecondary else ThingsColors.TextPrimary,
            ),
            cursorBrush = SolidColor(ThingsColors.Fab),
            keyboardOptions = ItemKeyboard,
            keyboardActions = KeyboardActions(
                onNext = {
                    if (value.text.isNotBlank()) onEnter() else onBlurBlank()
                },
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(requester)
                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (state.isFocused) {
                        hadFocus = true
                    } else if (hadFocus) {
                        hadFocus = false
                        if (value.text.isBlank()) onBlurBlank()
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace && value.text.isEmpty()) {
                        onBackspaceOnEmpty()
                        true
                    } else {
                        false
                    }
                },
        )
        Box(
            Modifier
                .size(width = 24.dp, height = ThingsSpacing.RowHeight)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { currentDragStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            currentDrag(amount.y)
                        },
                        onDragEnd = { currentDragEnd() },
                        onDragCancel = { currentDragEnd() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.DragHandle,
                contentDescription = "Move ${item.title}",
                tint = ThingsColors.TextSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** A blue circle (16dp, in a 24dp tap area), or a grey check mark once the item is done. */
@Composable
private fun ChecklistMark(done: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 24.dp, height = ThingsSpacing.RowHeight)
            .clickable(onClickLabel = "Toggle $description", role = Role.Checkbox, onClick = onClick)
            .semantics { stateDescription = if (done) "Done" else "Not done" },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = ThingsColors.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Box(
                Modifier
                    .size(16.dp)
                    .border(2.dp, ThingsColors.ProjectBlue, CircleShape)
            )
        }
    }
}

/**
 * The empty line at the end of a checklist. Next on the keyboard saves the item and opens another
 * empty line; leaving the field saves what was typed and closes the line. Backspace on the empty
 * line closes it and puts the cursor at the end of the last item.
 */
@Composable
private fun NewChecklistRow(
    onCommit: (String) -> Unit,
    onDismiss: () -> Unit,
    onBackspaceOnEmpty: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var hadFocus by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }

    LaunchedEffect(Unit) { requester.requestFocus() }

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ThingsSpacing.RowHeight)
            .clip(FocusShape)
            .background(ThingsColors.SearchBackground)
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 24.dp, height = ThingsSpacing.RowHeight), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(16.dp)
                    .border(2.dp, ThingsColors.ProjectBlue, CircleShape)
            )
        }
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = ItemTextStyle.copy(color = ThingsColors.TextPrimary),
            cursorBrush = SolidColor(ThingsColors.Fab),
            keyboardOptions = ItemKeyboard,
            keyboardActions = KeyboardActions(
                onNext = {
                    if (text.isNotBlank()) {
                        onCommit(text)
                        text = ""
                    } else {
                        onDismiss()
                    }
                },
            ),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text("Checklist", style = ItemTextStyle.copy(color = ThingsColors.TextSecondary))
                    }
                    inner()
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(requester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace && text.isEmpty()) {
                        onBackspaceOnEmpty()
                        true
                    } else {
                        false
                    }
                }
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        hadFocus = true
                    } else if (hadFocus) {
                        if (text.isNotBlank()) onCommit(text)
                        onDismiss()
                    }
                }
                .testTag("new_checklist_field"),
        )
        Icon(
            Icons.Rounded.DragHandle,
            contentDescription = null,
            tint = ThingsColors.TextSecondary,
            modifier = Modifier.size(24.dp),
        )
    }
}
