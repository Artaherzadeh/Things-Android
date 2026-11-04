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

/** Text and icons inside the card line up with the title text, after the checkbox. */
private val Indent = 38.dp

/**
 * An open (expanded) to-do: the row grows into a card with the editable title, the Markdown notes,
 * the checklist, the When, deadline and repeat lines, the tags and the row of add buttons. Every
 * edit is reported at once through [onEdit] (the card has no Save button); [onEdit] receives a
 * function that turns the current to-do into the edited one.
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
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(8.dp, CardShape)
            .background(ThingsColors.Background, CardShape)
            .border(1.dp, ThingsColors.Divider, CardShape)
            // Taps on the card must not reach the page behind it, which closes the card.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 4.dp)
            .testTag("task_card_${task.id}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TaskCheckbox(
                checked = task.checked,
                title = task.title,
                onToggle = onToggle,
                modifier = Modifier.height(44.dp),
                startPadding = 0.dp,
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
                    .padding(vertical = 8.dp)
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
                .padding(start = Indent, end = 4.dp, top = 2.dp)
                .heightIn(min = 56.dp),
        )

        if (showChecklist) {
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
                modifier = Modifier.padding(start = Indent - 8.dp, top = 4.dp, bottom = 4.dp),
            )
        }

        details.schedule?.let { schedule ->
            DetailRow(onClick = onScheduleClick, icon = { ScheduleIcon(schedule.kind) }) {
                Text(schedule.label, style = ThingsType.RowTitle)
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
            showChecklist = !showChecklist,
            showDeadline = details.deadline == null,
            onDate = onScheduleClick,
            onTags = onTagsClick,
            onChecklist = { addingChecklist = true },
            onDeadline = onDeadlineClick,
        )
    }
}

/** One line under the notes: an icon in the 24dp slot and one or two lines of text. */
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
            .padding(start = Indent, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(10.dp))
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
 * The tags at the start and the add buttons at the end. A button shows only while there is nothing
 * of its kind yet: the calendar until a date is set, the checklist button until a checklist exists,
 * the flag until a deadline is set. The tag button always shows.
 */
@Composable
private fun BottomBar(
    tags: List<String>,
    showDate: Boolean,
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
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .padding(start = Indent),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tags.forEach { TagChip(it) }
        }
        if (showDate) {
            CardIconButton("Add date", onDate) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = ThingsColors.TextSecondary, modifier = Modifier.size(22.dp))
            }
        }
        CardIconButton("Add tags", onTags) { TagIcon(Modifier.size(20.dp)) }
        if (showChecklist) {
            CardIconButton("Add checklist", onChecklist) { ChecklistIcon(Modifier.size(20.dp)) }
        }
        if (showDeadline) {
            CardIconButton("Add deadline", onDeadline) {
                Icon(Icons.Outlined.Flag, null, tint = ThingsColors.TextSecondary, modifier = Modifier.size(22.dp))
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
private fun TagChip(name: String) {
    Text(
        name,
        style = ThingsType.Count.copy(fontSize = 14.sp, color = ThingsColors.Anytime),
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(ThingsColors.AnytimeLight.copy(alpha = 0.25f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
