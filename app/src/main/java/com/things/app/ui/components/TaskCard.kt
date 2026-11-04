package com.things.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.things.app.ui.home.ArchiveIcon
import com.things.app.ui.home.CalendarIcon
import com.things.app.ui.home.StarIcon
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsType

private val CardShape = RoundedCornerShape(20.dp)

/**
 * Text and icons inside the card start 32dp after the start of the card content. The card is 8dp
 * from the screen edge and has 12dp of padding, so this puts them at x = 52dp: in line with the
 * title of a closed row. The right edge of every block is 20dp from the screen edge.
 */
private val Indent = 32.dp

/**
 * An open (expanded) to-do: the row grows into a card with the editable title, the Markdown notes,
 * the checklist, the When, deadline and repeat lines, the tags and the row of add buttons. Every
 * edit is reported at once through [onEdit] (the card has no Save button); [onEdit] receives a
 * function that turns the current to-do into the edited one.
 *
 * Everything except the title and the notes is optional. A line shows only when it is set, and the
 * button that sets it shows only while it is not: calendar (When), tag, checklist, flag (deadline).
 * The title is plain text; Markdown applies to the notes only.
 *
 * The checkbox box is at x = 20dp, exactly where it is in a closed row, so it does not move when
 * the row opens. The card has a soft shadow and leaves 12dp under it (4dp above) so the row below
 * does not touch it.
 */
@Composable
fun TaskCard(
    task: TaskRowModel,
    onEdit: ((TaskRowModel) -> TaskRowModel) -> Unit,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit = {},
    onScheduleClick: () -> Unit = {},
    onDeadlineClick: () -> Unit = {},
    onTagsClick: () -> Unit = {},
) {
    val details = task.details
    var notes by remember(task.id) { mutableStateOf(TextFieldValue(details.notes)) }
    var addingChecklist by rememberSaveable(task.id) { mutableStateOf(false) }
    val showChecklist = details.checklist.isNotEmpty() || addingChecklist

    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 12.dp)
            .shadow(
                elevation = 6.dp,
                shape = CardShape,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.10f),
            )
            .background(ThingsColors.Background, CardShape)
            .border(1.dp, ThingsColors.Divider, CardShape)
            // Taps on the card must not reach the page behind it, which closes the card.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            .testTag("task_card_${task.id}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TaskCheckbox(
                checked = task.checked,
                title = task.title,
                onToggle = onToggle,
                modifier = Modifier.height(44.dp),
                startPadding = 0.dp,
                slotWidth = Indent,
            )
            BasicTextField(
                value = task.title,
                onValueChange = { text -> onEdit { it.copy(title = text) } },
                textStyle = ThingsType.RowTitle.copy(
                    color = if (task.checked) ThingsColors.TextSecondary else ThingsColors.TextPrimary,
                    textDecoration = if (task.checked) TextDecoration.LineThrough else null,
                ),
                cursorBrush = SolidColor(ThingsColors.Fab),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { inner ->
                    Box {
                        if (task.title.isEmpty()) {
                            Text("New To-Do", style = ThingsType.RowTitle.copy(color = ThingsColors.TextSecondary))
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp, bottom = 8.dp, end = 8.dp)
                    .testTag("task_title_field"),
            )
        }

        NotesEditor(
            value = notes,
            onValueChange = { updated ->
                val textChanged = updated.text != notes.text
                notes = updated
                if (textChanged) {
                    onEdit {
                        it.copy(
                            details = it.details.copy(notes = updated.text),
                            hasNote = updated.text.isNotBlank(),
                        )
                    }
                }
            },
            modifier = Modifier
                .padding(start = Indent, end = 8.dp, top = 4.dp)
                .heightIn(min = 56.dp),
        )

        if (showChecklist) {
            // 8dp at the end, like the 12dp the checkbox is from the start edge once the card's own
            // 4dp is counted, so the checklist is not squeezed against the right border.
            ChecklistSection(
                items = details.checklist,
                onItemsChange = { items ->
                    onEdit {
                        it.copy(
                            details = it.details.copy(checklist = items),
                            hasChecklist = items.isNotEmpty(),
                        )
                    }
                },
                adding = addingChecklist,
                onAddingChange = { addingChecklist = it },
                modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            )
        }

        details.schedule?.let { schedule ->
            DetailRow(onClick = onScheduleClick, icon = { ScheduleIcon(schedule.kind) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(schedule.label, style = ThingsType.RowTitle)
                    schedule.reminder?.let { time ->
                        Spacer(Modifier.width(8.dp))
                        BellIcon(Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(time, style = ThingsType.Count)
                    }
                }
            }
        }
        details.deadline?.let { deadline ->
            DetailRow(
                onClick = onDeadlineClick,
                icon = {
                    Icon(
                        Icons.Outlined.Flag,
                        contentDescription = "Deadline",
                        tint = ThingsColors.TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(deadline.dateLabel, style = ThingsType.RowTitle)
                    deadline.remainingLabel?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(it, style = ThingsType.Count)
                    }
                }
            }
        }
        details.repeat?.let { repeat ->
            DetailRow(onClick = null, icon = { RepeatIcon(Modifier.size(20.dp)) }) {
                Text(repeat.title, style = ThingsType.RowTitle)
                Text(repeat.summary, style = ThingsType.Count)
            }
        }

        BottomBar(
            tags = details.tags,
            showDate = details.schedule == null,
            showTags = details.tags.isEmpty(),
            showChecklist = !showChecklist,
            showDeadline = details.deadline == null,
            onDate = onScheduleClick,
            onTags = onTagsClick,
            onChecklist = { addingChecklist = true },
            onDeadline = onDeadlineClick,
        )
    }
}

/** One line under the notes: an icon in a 24dp slot, 8dp of space, and one or two lines of text. */
@Composable
private fun DetailRow(
    onClick: (() -> Unit)?,
    icon: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = Indent, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), content = content)
    }
}

@Composable
private fun ScheduleIcon(kind: ScheduleKind) {
    when (kind) {
        ScheduleKind.Today -> StarIcon(Modifier.size(20.dp))
        ScheduleKind.Evening -> Icon(
            Icons.Outlined.NightsStay,
            contentDescription = null,
            tint = ThingsColors.ProjectBlue,
            modifier = Modifier.size(20.dp),
        )
        ScheduleKind.Someday -> ArchiveIcon(Modifier.size(20.dp))
        ScheduleKind.Date -> CalendarIcon(Modifier.size(20.dp))
    }
}

/**
 * The tags at the start and the add buttons at the end. Each button shows only while there is
 * nothing of its kind yet: the calendar until a date is set, the tag button until a tag is set (the
 * tag chips take its place and open the tag list), the checklist button until a checklist exists,
 * the flag until a deadline is set. The icons are 24dp in 40dp buttons; the last icon ends 20dp
 * from the screen edge.
 */
@Composable
private fun BottomBar(
    tags: List<String>,
    showDate: Boolean,
    showTags: Boolean,
    showChecklist: Boolean,
    showDeadline: Boolean,
    onDate: () -> Unit,
    onTags: () -> Unit,
    onChecklist: () -> Unit,
    onDeadline: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .padding(start = Indent),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tags.forEach { TagChip(it, onClick = onTags) }
        }
        if (showDate) {
            CardIconButton("Add date", onDate) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = ThingsColors.TextSecondary, modifier = Modifier.size(24.dp))
            }
        }
        if (showTags) {
            CardIconButton("Add tags", onTags) { TagIcon(Modifier.size(24.dp)) }
        }
        if (showChecklist) {
            CardIconButton("Add checklist", onChecklist) { ChecklistIcon(Modifier.size(24.dp)) }
        }
        if (showDeadline) {
            CardIconButton("Add deadline", onDeadline) {
                Icon(Icons.Outlined.Flag, null, tint = ThingsColors.TextSecondary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun CardIconButton(description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .semantics { contentDescription = description },
    ) {
        content()
    }
}

@Composable
private fun TagChip(name: String, onClick: () -> Unit) {
    Text(
        name,
        style = ThingsType.Count.copy(fontSize = 14.sp, color = ThingsColors.Anytime),
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(ThingsColors.AnytimeLight.copy(alpha = 0.25f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
