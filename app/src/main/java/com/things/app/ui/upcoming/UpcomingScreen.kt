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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.things.app.ui.components.ExpandableTaskRow
import com.things.app.ui.components.ListPageScaffold
import com.things.app.ui.components.RecurringStyle
import com.things.app.ui.components.rememberTaskExpansion
import com.things.app.ui.home.CalendarIcon
import com.things.app.ui.theme.ThingsColors
import com.things.app.ui.theme.ThingsSpacing
import com.things.app.ui.theme.ThingsTheme
import com.things.app.ui.theme.ThingsType

private val DaySpacing = 56.dp

/** Space between the divider and the day name under it. */
private val LabelGap = 4.dp

/** The height of a digit in proportion to the font size (about the cap height of the font). */
private const val DigitHeightRatio = 0.71f

/**
 * Static Upcoming page: one section per day with a large day number, a divider, the day name
 * under it, and that day's to-dos. Days with no to-dos still show their header. Every to-do opens
 * in place when tapped. The space under the title is the same as on the other pages.
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
    val expansion = rememberTaskExpansion()

    ListPageScaffold(
        title = "Upcoming",
        icon = { CalendarIcon(Modifier.size(28.dp)) },
        modifier = modifier,
        titleTag = "upcoming_title",
        onBack = onBack,
        onMoreClick = onMoreClick,
        expansion = expansion,
        onAddClick = onAddClick,
    ) {
        state.days.forEach { day ->
            item(key = "day-${day.id}") { DayHeader(day) }
            items(day.tasks, key = { "task-${day.id}-${it.id}" }) { task ->
                ExpandableTaskRow(
                    task = task,
                    expansion = expansion,
                    recurringStyle = RecurringStyle.LeadingBadge,
                    onOpen = { onTaskClick(task.id) },
                    onToggle = { onToggleTask(task.id) },
                )
            }
            item(key = "gap-${day.id}") { Spacer(Modifier.height(DaySpacing)) }
        }
    }
}

/**
 * The big day number with the divider next to it and the day name under the divider. The top of the
 * divider is level with the top of the digits: the space above it is worked out from where the
 * digits really start in the font that is used, not guessed.
 */
@Composable
private fun DayHeader(day: UpcomingDay) {
    val density = LocalDensity.current
    var digitTop by remember { mutableStateOf(8.dp) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ThingsSpacing.PageMargin),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            day.dayNumber.toString(),
            style = ThingsType.DayNumber,
            onTextLayout = { layout ->
                if (layout.lineCount > 0) {
                    digitTop = with(density) {
                        val fontPx = ThingsType.DayNumber.fontSize.toPx()
                        (layout.getLineBaseline(0) - DigitHeightRatio * fontPx).coerceAtLeast(0f).toDp()
                    }
                }
            },
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            HorizontalDivider(
                modifier = Modifier.padding(top = digitTop),
                thickness = 1.dp,
                color = ThingsColors.Divider,
            )
            Text(
                day.label,
                style = ThingsType.DayLabel,
                modifier = Modifier.padding(top = LabelGap),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Upcoming - sample")
@Composable
private fun UpcomingScreenPreview() {
    ThingsTheme { UpcomingScreen(sampleUpcomingState()) }
}
