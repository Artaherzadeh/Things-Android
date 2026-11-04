package com.things.app.ui.upcoming

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.things.app.ui.components.ListPageScaffold
import com.things.app.ui.components.RecurringStyle
import com.things.app.ui.components.TaskRow
import com.things.app.ui.home.CalendarIcon
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsTheme
import com.things.app.ui.theme.ThingsType

private val DaySpacing = 56.dp

/**
 * Static Upcoming page: one section per day with a large day number, a divider, the day name
 * under it, and that day's to-dos. Days with no to-dos still show their header.
 */
@Composable
fun UpcomingScreen(
    state: UpcomingUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onTaskClick: (String) -> Unit = {},
    onToggleTask: (String) -> Unit = {},
    onAddClick: () -> Unit = {},
) {
    ListPageScaffold(
        title = "Upcoming",
        icon = { CalendarIcon(Modifier.size(26.dp)) },
        modifier = modifier,
        titleTag = "upcoming_title",
        onBack = onBack,
        onMoreClick = onMoreClick,
        onAddClick = onAddClick,
    ) {
        item(key = "top-gap") { Spacer(Modifier.height(40.dp)) }

        state.days.forEach { day ->
            item(key = "day-${day.id}") { DayHeader(day) }
            items(day.tasks, key = { "task-${day.id}-${it.id}" }) { task ->
                TaskRow(
                    task = task,
                    recurringStyle = RecurringStyle.LeadingBadge,
                    onClick = { onTaskClick(task.id) },
                    onToggle = { onToggleTask(task.id) },
                )
            }
            item(key = "gap-${day.id}") { Spacer(Modifier.height(DaySpacing)) }
        }
    }
}

@Composable
private fun DayHeader(day: UpcomingDay) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(day.dayNumber.toString(), style = ThingsType.DayNumber)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 10.dp),
                thickness = 1.dp,
                color = ThingsColors.Divider,
            )
            Text(day.label, style = ThingsType.DayLabel)
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Upcoming - sample")
@Composable
private fun UpcomingScreenPreview() {
    ThingsTheme { UpcomingScreen(sampleUpcomingState()) }
}
