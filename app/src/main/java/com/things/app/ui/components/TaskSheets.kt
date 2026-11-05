package com.things.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.things.app.ui.home.ArchiveIcon
import com.things.app.ui.home.StarIcon
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsType
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Which sheet an open to-do is showing. */
enum class TaskSheet { When, Deadline, Tags }

/** A tag and the tags nested under it. */
data class TagModel(val name: String, val children: List<String> = emptyList())

/** Static glue: the tag list the sheet offers until real tags exist. New tags are added here. */
object SampleTagStore {
    val tags = mutableStateListOf(
        TagModel("Wishlist", listOf("Buy Next")),
        TagModel("Priority", listOf("Important", "ASAP")),
        TagModel("Next"),
    )
}

private val SheetShape = RoundedCornerShape(28.dp)
private val ClearRed = Color(0xFFD62C5C)

/**
 * A To-do wants the same sheet from two places: the open card and the select bar. [BulkRequest]
 * says what the select bar asked for, and [BulkSheets] shows the sheet and applies the answer to
 * every ticked to-do through [expansion].
 */
class BulkRequest(val action: SelectionAction, val ids: Set<String>)

@Composable
fun BulkSheets(request: BulkRequest, expansion: TaskExpansion?, onClose: () -> Unit) {
    when (request.action) {
        SelectionAction.When -> WhenSheet(current = null, onDismiss = onClose) { schedule ->
            expansion?.patchAll(request.ids) { it.withSchedule(schedule) }
        }
        SelectionAction.SetTags -> TagsSheet(selected = emptyList(), onDismiss = onClose, onDone = { tags ->
            expansion?.patchAll(request.ids) { it.withTags((it.details.tags + tags).distinct()) }
        })
        else -> Unit
    }
}

/** The dark rounded panel with a centered title and a round button at the top right. */
@Composable
private fun SheetFrame(
    title: String,
    onClose: () -> Unit,
    confirm: Boolean,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(SheetShape)
                .background(ThingsColors.MenuSurface)
                .padding(bottom = 16.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp)
                    .height(48.dp)
            ) {
                Text(
                    title,
                    style = ThingsType.RowTitleBold.copy(color = ThingsColors.MenuTitle),
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (confirm) ThingsColors.Fab else ThingsColors.MenuTitle.copy(alpha = 0.14f))
                        .clickable(
                            onClickLabel = if (confirm) "Done" else "Close",
                            role = Role.Button,
                        ) { if (confirm) onConfirm() else onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (confirm) Icons.Rounded.Check else Icons.Rounded.Close,
                        contentDescription = null,
                        tint = if (confirm) Color.White else ThingsColors.MenuTitle,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun SheetRow(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    labelColor: Color = ThingsColors.MenuTitle,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(12.dp))
        Text(label, style = ThingsType.RowTitle.copy(color = labelColor), modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = ThingsColors.Fab, modifier = Modifier.size(28.dp))
        }
    }
}

/** The wide red button at the bottom of a sheet that has a value to remove. */
@Composable
private fun ClearButton(onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(ClearRed)
            .clickable(onClickLabel = "Clear", role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("Clear", style = ThingsType.RowTitleBold.copy(color = Color.White))
    }
}

/**
 * The When sheet: Today, This Evening, a calendar, Someday, a reminder time and Clear. A choice is
 * reported through [onPick] and closes the sheet. Setting a reminder keeps the sheet open; with no
 * day chosen yet the reminder puts the to-do in Today. [onPick] gets null for Clear.
 */
@Composable
fun WhenSheet(current: ScheduleModel?, onDismiss: () -> Unit, onPick: (ScheduleModel?) -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    var pickingTime by remember { mutableStateOf(false) }
    val today = remember { Calendar.getInstance() }
    val reminder = current?.reminder

    fun pick(kind: ScheduleKind, label: String) {
        onPick(ScheduleModel(kind, label, if (kind == ScheduleKind.Someday) null else reminder))
        onDismiss()
    }

    SheetFrame(title = "When?", onClose = onDismiss, confirm = false, onConfirm = {}) {
        SheetRow(
            selected = current?.kind == ScheduleKind.Today,
            onClick = { pick(ScheduleKind.Today, "Today") },
            icon = { StarIcon(Modifier.size(28.dp)) },
            label = "Today",
        )
        SheetRow(
            selected = current?.kind == ScheduleKind.Evening,
            onClick = { pick(ScheduleKind.Evening, "This Evening") },
            icon = {
                Icon(
                    Icons.Outlined.NightsStay,
                    contentDescription = null,
                    tint = ThingsColors.ProjectBlue,
                    modifier = Modifier.size(26.dp),
                )
            },
            label = "This Evening",
        )
        CalendarMonth(
            page = page,
            today = today,
            selectedLabel = current?.takeIf { it.kind == ScheduleKind.Date }?.label,
            onPage = { page = it },
            onToday = { pick(ScheduleKind.Today, "Today") },
            onDate = { label -> pick(ScheduleKind.Date, label) },
        )
        SheetRow(
            selected = current?.kind == ScheduleKind.Someday,
            onClick = { pick(ScheduleKind.Someday, "Someday") },
            icon = { ArchiveIcon(Modifier.size(28.dp)) },
            label = "Someday",
        )
        SheetRow(
            selected = false,
            onClick = { pickingTime = true },
            icon = {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = ThingsColors.TextSecondary,
                    modifier = Modifier.size(28.dp),
                )
            },
            label = reminder ?: "Add Reminder",
            labelColor = if (reminder != null) ThingsColors.MenuTitle else ThingsColors.TextSecondary,
        )
        if (current != null) {
            ClearButton {
                onPick(null)
                onDismiss()
            }
        }
    }

    if (pickingTime) {
        TimePickerDialog(
            onCancel = { pickingTime = false },
            onSet = { text ->
                pickingTime = false
                onPick((current ?: ScheduleModel(ScheduleKind.Today, "Today")).copy(reminder = text))
            },
        )
    }
}

/** How many weeks the Deadline calendar scrolls through: about three years. */
private const val DeadlineWeeks = 160
private val DeadlineRowHeight = 52.dp

/**
 * The Deadline sheet: the weekday names, then the weeks one under another, starting with the week
 * of today, scrolling down for about three years. The first day of each month shows the month name
 * (January also the year) and a thin line runs around the days of that month. Days before today are
 * grey and cannot be chosen. The chosen day has a blue ring with its month above its number. A day
 * is reported through [onPick] and closes the sheet; Clear (shown when a deadline is set) reports
 * null. The text "N days left" is made here until the logic layer produces it.
 */
@Composable
fun DeadlineSheet(current: DeadlineModel?, onDismiss: () -> Unit, onPick: (DeadlineModel?) -> Unit) {
    val today = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val firstDow = remember { Calendar.getInstance().firstDayOfWeek }
    val gridStart = remember {
        (today.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) - firstDow + 7) % 7))
        }
    }
    val weekdayFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val labelFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val monthFormat = remember { SimpleDateFormat("MMM", Locale.getDefault()) }

    fun dateAt(offset: Int): Calendar =
        (gridStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, offset) }

    // Open at the week of the chosen day, so a far deadline is not hidden below the list.
    val startWeek = remember(current) {
        current?.let { c ->
            (0 until DeadlineWeeks * 7)
                .firstOrNull { labelFormat.format(dateAt(it).time) == c.dateLabel }
                ?.div(7)
        } ?: 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startWeek)
    val listHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp

    SheetFrame(title = "Deadline", onClose = onDismiss, confirm = false, onConfirm = {}) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            for (i in 0 until 7) {
                val day = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, (firstDow - 1 + i) % 7 + 1) }
                Text(
                    weekdayFormat.format(day.time),
                    style = ThingsType.Count.copy(color = ThingsColors.TextSecondary, fontSize = 14.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .height(listHeight)
                .padding(horizontal = 12.dp),
        ) {
            items(DeadlineWeeks) { week ->
                DeadlineWeek(
                    days = List(7) { dateAt(week * 7 + it) },
                    today = today,
                    labelFormat = labelFormat,
                    monthFormat = monthFormat,
                    selectedLabel = current?.dateLabel,
                    onPick = { date ->
                        val diff = Math.round((date.timeInMillis - today.timeInMillis) / 86_400_000.0).toInt()
                        val remaining = when {
                            diff <= 0 -> "Today"
                            diff == 1 -> "1 day left"
                            else -> "$diff days left"
                        }
                        onPick(DeadlineModel(labelFormat.format(date.time), remaining))
                        onDismiss()
                    },
                )
            }
        }
        if (current != null) {
            ClearButton {
                onPick(null)
                onDismiss()
            }
        }
    }
}

/**
 * One week of the Deadline calendar. When the 1st of a month is in the week, a thin line runs
 * along the top of that month's days and down its left side, and under the days of the month
 * before it, with rounded corners where it turns.
 */
@Composable
private fun DeadlineWeek(
    days: List<Calendar>,
    today: Calendar,
    labelFormat: SimpleDateFormat,
    monthFormat: SimpleDateFormat,
    selectedLabel: String?,
    onPick: (Calendar) -> Unit,
) {
    val firstCol = days.indexOfFirst { it.get(Calendar.DAY_OF_MONTH) == 1 }
    val lineColor = ThingsColors.MenuTitle.copy(alpha = 0.3f)

    Row(
        Modifier
            .fillMaxWidth()
            .height(DeadlineRowHeight)
            .drawBehind {
                if (firstCol >= 0) {
                    val w = size.width
                    val h = size.height
                    val x = firstCol * (w / 7f)
                    val r = 8.dp.toPx()
                    val path = Path()
                    if (firstCol == 0) {
                        path.moveTo(0f, 0f)
                        path.lineTo(w, 0f)
                    } else {
                        path.moveTo(0f, h)
                        path.lineTo(x - r, h)
                        path.quadraticBezierTo(x, h, x, h - r)
                        path.lineTo(x, r)
                        path.quadraticBezierTo(x, 0f, x + r, 0f)
                        path.lineTo(w, 0f)
                    }
                    drawPath(path, lineColor, style = Stroke(width = 1.dp.toPx()))
                }
            },
    ) {
        days.forEach { date ->
            val day = date.get(Calendar.DAY_OF_MONTH)
            val past = date.before(today)
            val selected = !past && labelFormat.format(date.time) == selectedLabel
            val january = date.get(Calendar.MONTH) == Calendar.JANUARY
            val withYear = day == 1 && january && !selected
            val text = when {
                selected || day == 1 -> buildString {
                    append(monthFormat.format(date.time))
                    append('\n')
                    append(day)
                    if (withYear) {
                        append('\n')
                        append(date.get(Calendar.YEAR))
                    }
                }
                else -> day.toString()
            }
            val color = if (past) ThingsColors.TextSecondary else ThingsColors.MenuTitle
            val style = when {
                withYear -> ThingsType.RowTitle.copy(color = color, fontSize = 12.sp, lineHeight = 14.sp)
                text.contains('\n') -> ThingsType.RowTitle.copy(color = color, fontSize = 13.sp, lineHeight = 15.sp)
                else -> ThingsType.RowTitle.copy(color = color)
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .then(if (selected) Modifier.border(2.dp, ThingsColors.Fab, CircleShape) else Modifier)
                        .clickable(enabled = !past, role = Role.Button) { onPick(date) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text, style = style, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * Days from today to the end of this month, then whole months with the arrow. Today is a grey star.
 * The week starts on the first day of the week of the phone's region.
 */
@Composable
private fun CalendarMonth(
    page: Int,
    today: Calendar,
    selectedLabel: String?,
    onPage: (Int) -> Unit,
    onToday: () -> Unit,
    onDate: (String) -> Unit,
) {
    val first = remember(page) {
        Calendar.getInstance().apply {
            time = today.time
            if (page > 0) {
                add(Calendar.MONTH, page)
                set(Calendar.DAY_OF_MONTH, 1)
            }
        }
    }
    val firstDow = Calendar.getInstance().firstDayOfWeek
    val startDay = first.get(Calendar.DAY_OF_MONTH)
    val lastDay = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    val lead = (first.get(Calendar.DAY_OF_WEEK) - firstDow + 7) % 7
    val weekdayFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val labelFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }

    // null is an empty cell, -1 is the arrow to the next month, anything else is a day number.
    val cells: List<Int?> = buildList {
        repeat(lead) { add(null) }
        for (d in startDay..lastDay) add(d)
        add(-1)
        while (size % 7 != 0) add(null)
    }

    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (page > 0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = "Previous month", role = Role.Button) { onPage(page - 1) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = ThingsColors.MenuTitle)
                }
                Text(
                    monthFormat.format(first.time),
                    style = ThingsType.RowTitle.copy(color = ThingsColors.MenuTitle),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val day = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, (firstDow - 1 + i) % 7 + 1) }
                Text(
                    weekdayFormat.format(day.time),
                    style = ThingsType.Count.copy(color = ThingsColors.TextSecondary, fontSize = 14.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            cell == null -> Unit
                            cell == -1 -> Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable(onClickLabel = "Next month", role = Role.Button) { onPage(page + 1) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = ThingsColors.MenuTitle)
                            }
                            else -> {
                                val date = (first.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, cell) }
                                val label = labelFormat.format(date.time)
                                val isToday = page == 0 && cell == startDay
                                val selected = label == selectedLabel
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .then(if (selected) Modifier.background(ThingsColors.Fab) else Modifier)
                                        .clickable(role = Role.Button) {
                                            if (isToday) onToday() else onDate(label)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isToday) {
                                        StarIcon(Modifier.size(24.dp))
                                    } else {
                                        Text(
                                            cell.toString(),
                                            style = ThingsType.RowTitle.copy(
                                                color = if (selected) Color.White else ThingsColors.MenuTitle,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Two wheels, hour and minute (in steps of five), with Cancel and Set. [onSet] gets the time as text. */
@Composable
private fun TimePickerDialog(onCancel: () -> Unit, onSet: (String) -> Unit) {
    val now = remember { Calendar.getInstance() }
    var hour by remember { mutableIntStateOf(now.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(now.get(Calendar.MINUTE) / 5 * 5) }
    val hours = remember { (0..23).map { "%02d".format(it) } }
    val minutes = remember { (0..55 step 5).map { "%02d".format(it) } }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 48.dp)
                .fillMaxWidth()
                .clip(SheetShape)
                .background(ThingsColors.MenuSurface)
                .padding(16.dp)
        ) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ThingsColors.MenuTitle.copy(alpha = 0.16f))
                )
                Row(Modifier.fillMaxWidth()) {
                    Wheel(hours, hour, { hour = it }, Modifier.weight(1f))
                    Wheel(minutes, minute / 5, { minute = it * 5 }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetButton("Cancel", Modifier.weight(1f), onCancel)
                SheetButton("Set", Modifier.weight(1f)) {
                    val time = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                    }
                    onSet(DateFormat.getTimeInstance(DateFormat.SHORT).format(time.time))
                }
            }
        }
    }
}

@Composable
private fun SheetButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(ThingsColors.MenuTitle.copy(alpha = 0.16f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = ThingsType.RowTitleBold.copy(color = ThingsColors.MenuTitle))
    }
}

/** A column of values that snaps to one of them: the one in the middle is the chosen one. */
@Composable
private fun Wheel(values: List<String>, selected: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    val itemHeight = 44.dp
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val center by remember {
        derivedStateOf {
            (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2f) 1 else 0)
                .coerceIn(values.indices)
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val index = center
                state.animateScrollToItem(index)
                onSelected(index)
            }
        }
    }

    LazyColumn(
        state = state,
        modifier = modifier.height(itemHeight * 5),
        contentPadding = PaddingValues(vertical = itemHeight * 2),
    ) {
        itemsIndexed(values) { index, text ->
            Box(Modifier.height(itemHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text,
                    style = ThingsType.RowTitle.copy(
                        fontSize = if (index == center) 24.sp else 18.sp,
                        color = if (index == center) ThingsColors.MenuTitle else ThingsColors.TextSecondary,
                    ),
                )
            }
        }
    }
}

/**
 * The Tags sheet: the tag list with the tags nested under their parent. Tap a tag to tick it. The
 * round button at the top right is a cross until something changed, then a blue check that applies
 * the change through [onDone]. New Tag adds a tag to the list and ticks it.
 */
@Composable
fun TagsSheet(
    selected: List<String>,
    onDismiss: () -> Unit,
    onDone: (List<String>) -> Unit,
    onManageTags: () -> Unit = {},
) {
    var chosen by remember { mutableStateOf(selected) }
    var creating by remember { mutableStateOf(false) }
    val changed = chosen.toSet() != selected.toSet()

    fun toggle(name: String) {
        chosen = if (name in chosen) chosen - name else chosen + name
    }

    SheetFrame(
        title = "Tags",
        onClose = onDismiss,
        confirm = changed,
        onConfirm = {
            onDone(chosen)
            onDismiss()
        },
    ) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .heightIn(min = 280.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            SampleTagStore.tags.forEach { tag ->
                TagRow(tag.name, 0, tag.name in chosen) { toggle(tag.name) }
                tag.children.forEach { child ->
                    TagRow(child, 1, child in chosen) { toggle(child) }
                }
            }
        }
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetButton("Manage Tags", Modifier.weight(1f), onManageTags)
            SheetButton("New Tag", Modifier.weight(1f)) { creating = true }
        }
    }

    if (creating) {
        NewTagDialog(
            onCancel = { creating = false },
            onAdd = { name ->
                creating = false
                if (SampleTagStore.tags.none { it.name.equals(name, ignoreCase = true) }) {
                    SampleTagStore.tags.add(TagModel(name))
                }
                if (name !in chosen) chosen = chosen + name
            },
        )
    }
}

@Composable
private fun TagRow(name: String, depth: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(start = 16.dp + 28.dp * depth, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TagIcon(
            Modifier.size(28.dp),
            color = if (selected) ThingsColors.MenuTitle else ThingsColors.TextSecondary,
        )
        Spacer(Modifier.width(12.dp))
        Text(name, style = ThingsType.RowTitle.copy(color = ThingsColors.MenuTitle), modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = ThingsColors.Fab, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun NewTagDialog(onCancel: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocus() }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .clip(SheetShape)
                .background(ThingsColors.MenuSurface)
                .padding(16.dp)
        ) {
            Text("New Tag", style = ThingsType.RowTitleBold.copy(color = ThingsColors.MenuTitle))
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = ThingsType.RowTitle.copy(color = ThingsColors.MenuTitle),
                cursorBrush = SolidColor(ThingsColors.Fab),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onAdd(name.trim()) }),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ThingsColors.MenuTitle.copy(alpha = 0.12f))
                            .padding(12.dp)
                    ) {
                        if (name.isEmpty()) {
                            Text("Tag name", style = ThingsType.RowTitle.copy(color = ThingsColors.TextSecondary))
                        }
                        inner()
                    }
                },
                modifier = Modifier.focusRequester(requester),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetButton("Cancel", Modifier.weight(1f), onCancel)
                SheetButton("Add", Modifier.weight(1f)) { if (name.isNotBlank()) onAdd(name.trim()) }
            }
        }
    }
}
