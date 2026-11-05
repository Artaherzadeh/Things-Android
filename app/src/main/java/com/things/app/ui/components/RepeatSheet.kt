package com.things.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Both kinds of repeat of Things: a new copy a set time after the last one is completed, or on a fixed schedule. */
enum class RepeatMode { AfterCompletion, Regularly }

enum class RepeatUnit(val noun: String) { Day("day"), Week("week"), Month("month"), Year("year") }

/**
 * What the Repeat sheet collects so far. [onDay] is the weekday (a Calendar.DAY_OF_WEEK number) for
 * weeks and the day of the month for months. [next] and [ends] are dates as text; [ends] is null for
 * never. Static glue: the texts are made here until the logic layer makes them.
 */
data class RepeatRule(
    val mode: RepeatMode,
    val every: Int,
    val unit: RepeatUnit,
    val onDay: Int,
    val next: String,
    val ends: String?,
)

/** The pages of the sheet: the main one, and the two that replace it in place. */
private enum class RepeatPage { Main, Every, Next }

private const val DatePattern = "EEE, MMM d, yyyy"
private val GroupShape = RoundedCornerShape(20.dp)

private fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) {
        "th"
    } else {
        when (n % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }
    return "$n$suffix"
}

private fun weekdayName(dayOfWeek: Int): String =
    SimpleDateFormat("EEEE", Locale.getDefault())
        .format(Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, dayOfWeek) }.time)

private fun RepeatRule.everyText(): String = if (every == 1) unit.noun else "$every ${unit.noun}s"

private fun RepeatRule.onText(): String? = when (unit) {
    RepeatUnit.Week -> weekdayName(onDay)
    RepeatUnit.Month -> ordinal(onDay)
    else -> null
}

/** The rule with another unit; the "on" day starts from today's weekday or day of the month. */
private fun RepeatRule.withUnit(newUnit: RepeatUnit): RepeatRule {
    val today = Calendar.getInstance()
    val day = when (newUnit) {
        RepeatUnit.Week -> today.get(Calendar.DAY_OF_WEEK)
        RepeatUnit.Month -> today.get(Calendar.DAY_OF_MONTH)
        else -> onDay
    }
    return copy(unit = newUnit, onDay = day)
}

/** The two lines the open to-do shows for a repeat, for example "Every week on Friday" and "Next: Fri, Nov 6, 2026". */
fun RepeatRule.toInfo(): RepeatInfoModel {
    val title = buildString {
        append("Every ").append(everyText())
        onText()?.let { append(" on ").append(it) }
        if (mode == RepeatMode.AfterCompletion) append(" after completion")
    }
    val summary = buildString {
        append("Next: ").append(next)
        ends?.let { append(", ends ").append(it) }
    }
    return RepeatInfoModel(title, summary)
}

/**
 * The to-do made into a repeating template by [rule] (or back into a plain to-do when null). The
 * next date becomes its date; a reminder it already has is kept.
 */
fun TaskRowModel.withRepeat(rule: RepeatRule?): TaskRowModel =
    if (rule == null) {
        copy(isRepeatingTemplate = false, details = details.copy(repeat = null))
    } else {
        copy(
            isRepeatingTemplate = true,
            details = details.copy(
                repeat = rule.toInfo(),
                schedule = ScheduleModel(ScheduleKind.Date, rule.next, details.schedule?.reminder),
            ),
        )
    }

/**
 * The Repeat sheet. It starts as "Regularly, every week on today's weekday, first copy today, never
 * ends". One panel changes in place: the main page has the After Completion / Regularly switch and
 * the lines Every, On, Next, Ends, Add Deadline and Add Reminder; Every and Next replace it with a
 * page that has a back arrow (Every: the number and unit wheels; Next: the calendar of the Deadline
 * sheet under the title Next). On, Ends, Add Deadline and Add Reminder are drawn but do nothing yet.
 * The cross closes without changes; the blue check reports the rule through [onPick] and closes.
 */
@Composable
fun RepeatSheet(onDismiss: () -> Unit, onPick: (RepeatRule) -> Unit) {
    val formatter = remember { SimpleDateFormat(DatePattern, Locale.getDefault()) }
    var rule by remember {
        val today = Calendar.getInstance()
        mutableStateOf(
            RepeatRule(
                mode = RepeatMode.Regularly,
                every = 1,
                unit = RepeatUnit.Week,
                onDay = today.get(Calendar.DAY_OF_WEEK),
                next = formatter.format(today.time),
                ends = null,
            )
        )
    }
    var page by remember { mutableStateOf(RepeatPage.Main) }
    val onMain = page == RepeatPage.Main
    val close: () -> Unit = { if (page == RepeatPage.Main) onDismiss() else page = RepeatPage.Main }
    val spare = (LocalConfiguration.current.screenHeightDp * 0.25f).dp

    SheetFrame(
        title = if (page == RepeatPage.Next) "Next" else "Repeat",
        onClose = close,
        confirm = true,
        onConfirm = {
            onPick(rule)
            onDismiss()
        },
        leadingClose = onMain,
        leadingBack = !onMain,
        showTrailing = onMain,
    ) {
        when (page) {
            RepeatPage.Main -> Column(Modifier.padding(horizontal = 16.dp)) {
                ModeSwitch(rule.mode) { mode -> rule = rule.copy(mode = mode) }
                Text(
                    if (rule.mode == RepeatMode.Regularly) {
                        "You'll get a new copy regularly, whether you've completed the previous to-do or not."
                    } else {
                        "You'll get a new copy a set time after you complete the previous to-do."
                    },
                    style = ThingsType.RowTitle.copy(color = ThingsColors.MenuTitle.copy(alpha = 0.75f), fontSize = 16.sp),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                )
                Column(Modifier.fillMaxWidth().clip(GroupShape).background(ThingsColors.MenuTitle.copy(alpha = 0.08f))) {
                    RepeatRow("Every", rule.everyText()) { page = RepeatPage.Every }
                    rule.onText()?.let { on ->
                        RowDivider()
                        RepeatRow("On", on, null)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().clip(GroupShape).background(ThingsColors.MenuTitle.copy(alpha = 0.08f))) {
                    RepeatRow("Next", rule.next) { page = RepeatPage.Next }
                    RowDivider()
                    RepeatRow("Ends", rule.ends ?: "Never", null)
                }
                Spacer(Modifier.height(8.dp))
                PlainRow(
                    icon = { Icon(Icons.Outlined.Flag, null, tint = ThingsColors.TextSecondary, modifier = Modifier.size(24.dp)) },
                    label = "Add Deadline",
                )
                PlainRow(
                    icon = { Icon(Icons.Outlined.NotificationsNone, null, tint = ThingsColors.TextSecondary, modifier = Modifier.size(24.dp)) },
                    label = "Add Reminder",
                )
                Spacer(Modifier.height(24.dp))
            }
            RepeatPage.Every -> {
                EveryPicker(rule) { change -> rule = change(rule) }
                Spacer(Modifier.height(spare))
            }
            RepeatPage.Next -> DeadlineCalendar(
                current = DeadlineModel(rule.next),
                labelPattern = DatePattern,
                onPick = { picked ->
                    rule = rule.copy(next = picked.dateLabel)
                    page = RepeatPage.Main
                },
            )
        }
    }
}

/** The two-part switch at the top: After Completion and Regularly. */
@Composable
private fun ModeSwitch(mode: RepeatMode, onChange: (RepeatMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(ThingsColors.MenuTitle.copy(alpha = 0.08f))
            .padding(4.dp),
    ) {
        listOf(RepeatMode.AfterCompletion to "After Completion", RepeatMode.Regularly to "Regularly").forEach { (m, label) ->
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (m == mode) ThingsColors.MenuTitle.copy(alpha = 0.28f) else Color.Transparent)
                    .clickable(role = Role.Button) { onChange(m) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = ThingsType.RowTitleBold.copy(color = ThingsColors.MenuTitle, fontSize = 16.sp))
            }
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = ThingsColors.MenuTitle.copy(alpha = 0.15f),
    )
}

/**
 * A line of a grouped card: the name at the start, the value in blue and a chevron at the end. With
 * no [onClick] the line is drawn the same but does nothing.
 */
@Composable
private fun RepeatRow(label: String, value: String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = ThingsType.RowTitle.copy(color = ThingsColors.MenuTitle), modifier = Modifier.weight(1f))
        Text(value, style = ThingsType.RowTitle.copy(color = ThingsColors.Fab))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = ThingsColors.Fab)
    }
}

/** A grey line outside the cards (Add Deadline, Add Reminder); it does nothing yet. */
@Composable
private fun PlainRow(icon: @Composable () -> Unit, label: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(8.dp))
        Text(label, style = ThingsType.RowTitle.copy(color = ThingsColors.TextSecondary), modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = ThingsColors.TextSecondary)
    }
}

/**
 * The Every page: the current value in blue and two wheels, the number and the unit. Each wheel
 * hands the page a change to apply to the latest rule, so one wheel never undoes the other.
 */
@Composable
private fun EveryPicker(rule: RepeatRule, onChange: ((RepeatRule) -> RepeatRule) -> Unit) {
    val numbers = remember { (1..99).map { it.toString() } }
    val units = remember { RepeatUnit.values().toList() }
    val unitNames = remember { units.map { it.noun } }

    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(GroupShape)
            .background(ThingsColors.MenuTitle.copy(alpha = 0.08f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Every", style = ThingsType.RowTitle.copy(color = ThingsColors.MenuTitle), modifier = Modifier.weight(1f))
            Text(rule.everyText(), style = ThingsType.RowTitle.copy(color = ThingsColors.Fab))
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ThingsColors.MenuTitle.copy(alpha = 0.12f))
            )
            Row(Modifier.fillMaxWidth()) {
                Wheel(numbers, rule.every - 1, { index -> onChange { it.copy(every = index + 1) } }, Modifier.weight(1f))
                Wheel(unitNames, rule.unit.ordinal, { index -> onChange { it.withUnit(units[index]) } }, Modifier.weight(1f))
            }
        }
    }
}
