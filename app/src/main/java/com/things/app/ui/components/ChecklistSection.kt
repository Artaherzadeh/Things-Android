package com.things.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsSpacing
import com.things.app.ui.theme.ThingsType
import java.util.UUID

private val ItemTextStyle = ThingsType.RowTitle.copy(fontSize = 17.sp)

/**
 * The checklist of an open to-do: one line per item with a circle, the text and a drag handle.
 * Geometry of a line (44dp high): 8dp of space, a 24dp tap area holding the 16dp circle, 12dp of
 * space, the text, then the 24dp handle and 8dp of space. Place the section so that the circle lines
 * up with the to-do title.
 *
 * When [adding] is true an empty line at the end takes new items; every item is saved as soon as it
 * is typed. The handle is drawn but dragging to reorder comes later.
 */
@Composable
fun ChecklistSection(
    items: List<ChecklistItemModel>,
    onItemsChange: (List<ChecklistItemModel>) -> Unit,
    adding: Boolean,
    onAddingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        items.forEach { item ->
            key(item.id) {
                ChecklistRow(
                    item = item,
                    onChange = { updated ->
                        onItemsChange(items.map { if (it.id == item.id) updated else it })
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
            )
        }
    }
}

@Composable
private fun ChecklistRow(item: ChecklistItemModel, onChange: (ChecklistItemModel) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
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
            value = item.title,
            onValueChange = { onChange(item.copy(title = it)) },
            textStyle = ItemTextStyle.copy(
                color = if (item.done) ThingsColors.TextSecondary else ThingsColors.TextPrimary,
            ),
            cursorBrush = SolidColor(ThingsColors.Fab),
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Rounded.DragHandle,
            contentDescription = null,
            tint = ThingsColors.TextSecondary,
            modifier = Modifier.size(24.dp),
        )
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
 * empty line; leaving the field saves what was typed and closes the line.
 */
@Composable
private fun NewChecklistRow(onCommit: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var hadFocus by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }

    LaunchedEffect(Unit) { requester.requestFocus() }

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ThingsSpacing.RowHeight)
            .clip(RoundedCornerShape(12.dp))
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
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
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
