package com.things.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.things.app.ui.home.StarIcon
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsType

/** Everything a list row needs to draw one to-do. Pure UI data; no business rules. */
data class TaskRowModel(
    val id: String,
    val title: String,
    /** Project or area name shown under the title. Null hides the line. */
    val subtitle: String? = null,
    val checked: Boolean = false,
    /** The to-do is a copy created by a repeating template: draws the grey repeat icon. */
    val isRecurring: Boolean = false,
    val hasReminder: Boolean = false,
    val hasTag: Boolean = false,
    val hasNote: Boolean = false,
    val hasChecklist: Boolean = false,
    /** True when the to-do is scheduled for Today: draws the yellow star before the title. */
    val isToday: Boolean = false,
    /** Short grey label before the title, such as the next day (Fri) or a status (Waiting). */
    val chip: String? = null,
    /** The row is a repeating template: it gets the blue round repeat badge before the title. */
    val isRepeatingTemplate: Boolean = false,
)

/** Where the repeat marker of a recurring to-do is drawn. */
enum class RecurringStyle { TrailingIcon, LeadingBadge }

private val RowMinHeight = 44.dp
private val TwoLineRowMinHeight = 56.dp
private val CheckboxSize = 20.dp

@Composable
fun TaskRow(
    task: TaskRowModel,
    modifier: Modifier = Modifier,
    recurringStyle: RecurringStyle = RecurringStyle.TrailingIcon,
    onClick: () -> Unit = {},
    onToggle: () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (task.subtitle == null) RowMinHeight else TwoLineRowMinHeight)
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskCheckbox(checked = task.checked, title = task.title, onToggle = onToggle)

        task.chip?.let {
            TaskChip(it)
            Spacer(Modifier.width(8.dp))
        }
        if (task.isRepeatingTemplate || (task.isRecurring && recurringStyle == RecurringStyle.LeadingBadge)) {
            RepeatBadge()
            Spacer(Modifier.width(8.dp))
        }
        if (task.isToday) {
            StarIcon(Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }

        Column(Modifier.weight(1f, fill = false)) {
            Text(
                task.title,
                style = if (task.checked) {
                    ThingsType.RowTitle.copy(
                        color = ThingsColors.TextSecondary,
                        textDecoration = TextDecoration.LineThrough,
                    )
                } else {
                    ThingsType.RowTitle
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            task.subtitle?.let {
                Text(it, style = ThingsType.RowSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (task.hasTag) TrailingGlyph("Tags") { TagIcon(Modifier.size(16.dp)) }
        if (task.isRecurring && recurringStyle == RecurringStyle.TrailingIcon) {
            TrailingGlyph("Repeats") { RepeatIcon(Modifier.size(16.dp)) }
        }
        if (task.hasReminder) TrailingGlyph("Reminder") { BellIcon(Modifier.size(16.dp)) }
        if (task.hasNote) TrailingGlyph("Notes") { NoteIcon(Modifier.size(16.dp)) }
        if (task.hasChecklist) TrailingGlyph("Checklist") { ChecklistIcon(Modifier.size(16.dp)) }
    }
}

@Composable
private fun TaskChip(text: String) {
    Text(
        text,
        style = ThingsType.Count,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ThingsColors.SearchBackground)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun TrailingGlyph(description: String, glyph: @Composable () -> Unit) {
    Spacer(Modifier.width(8.dp))
    Box(Modifier.semantics { contentDescription = description }) { glyph() }
}

/** A 38dp-wide tap slot so the visible 20dp box lines up 18dp from the start edge. */
@Composable
private fun TaskCheckbox(checked: Boolean, title: String, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        Modifier
            .width(38.dp)
            .fillMaxHeight()
            .clickable(onClickLabel = "Toggle $title", role = Role.Checkbox, onClick = onToggle)
            .semantics { stateDescription = if (checked) "Completed" else "Not completed" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = 12.dp)
                .size(CheckboxSize)
                .clip(shape)
                .then(if (checked) Modifier.background(ThingsColors.ProjectBlue) else Modifier)
                .border(1.5.dp, if (checked) ThingsColors.ProjectBlue else ThingsColors.CheckboxStroke, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) CheckMark(Modifier.size(CheckboxSize))
        }
    }
}
