package com.things.app

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.things.app.ui.components.DeadlineModel
import com.things.app.ui.components.ExpandableTaskRow
import com.things.app.ui.components.RepeatInfoModel
import com.things.app.ui.components.ScheduleKind
import com.things.app.ui.components.ScheduleModel
import com.things.app.ui.components.TaskDetailsModel
import com.things.app.ui.components.TaskRowModel
import com.things.app.ui.components.rememberTaskExpansion
import com.things.app.ui.theme.ThingsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskCardTest {
    @get:Rule
    val rule = createComposeRule()

    private val cleaning = TaskRowModel(
        id = "cleaning",
        title = "Cleaning House",
        details = TaskDetailsModel(
            schedule = ScheduleModel(ScheduleKind.Today, "Today"),
            deadline = DeadlineModel("Mon, Nov 16", "12 days left"),
            repeat = RepeatInfoModel(
                title = "This is a copy of a repeating to-do",
                summary = "Repeat every week on Friday",
            ),
        ),
    )
    private val plants = TaskRowModel(id = "plants", title = "Water the plants")

    private fun show(vararg tasks: TaskRowModel) {
        rule.setContent {
            ThingsTheme {
                val expansion = rememberTaskExpansion()
                Column {
                    tasks.forEach { ExpandableTaskRow(it, expansion) }
                }
            }
        }
    }

    @Test
    fun tappingARowOpensItAsACardWithItsDetails() {
        show(cleaning, plants)
        rule.onNodeWithTag("task_card_cleaning").assertDoesNotExist()

        rule.onNodeWithText("Cleaning House").performClick()

        rule.onNodeWithTag("task_card_cleaning").assertIsDisplayed()
        rule.onNodeWithText("Notes").assertIsDisplayed()
        rule.onNodeWithText("Today").assertIsDisplayed()
        rule.onNodeWithText("Mon, Nov 16").assertIsDisplayed()
        rule.onNodeWithText("12 days left").assertIsDisplayed()
        rule.onNodeWithText("This is a copy of a repeating to-do").assertIsDisplayed()
        rule.onNodeWithText("Repeat every week on Friday").assertIsDisplayed()
    }

    @Test
    fun aToDoWithNothingSetShowsOnlyTitleNotesAndTheFourButtons() {
        show(plants)

        rule.onNodeWithText("Water the plants").performClick()

        rule.onNodeWithText("Notes").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add date").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add tags").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add checklist").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add deadline").assertIsDisplayed()
        rule.onNodeWithContentDescription("Deadline").assertDoesNotExist()
    }

    @Test
    fun openingAnotherToDoClosesTheFirstOne() {
        show(cleaning, plants)

        rule.onNodeWithText("Cleaning House").performClick()
        rule.onNodeWithText("Water the plants").performClick()

        rule.onNodeWithTag("task_card_cleaning").assertDoesNotExist()
        rule.onNodeWithTag("task_card_plants").assertIsDisplayed()
    }

    @Test
    fun editsAreKeptWithoutSaving() {
        show(cleaning, plants)

        rule.onNodeWithText("Cleaning House").performClick()
        rule.onNodeWithTag("task_title_field").performTextReplacement("Cleaning the kitchen")
        rule.onNodeWithText("Water the plants").performClick()

        rule.onNodeWithTag("task_card_cleaning").assertDoesNotExist()
        rule.onNodeWithText("Cleaning the kitchen").assertIsDisplayed()
    }

    @Test
    fun addButtonsShowOnlyWhileThereIsNothingOfThatKind() {
        show(cleaning, plants)

        rule.onNodeWithText("Cleaning House").performClick()
        rule.onNodeWithContentDescription("Add date").assertDoesNotExist()
        rule.onNodeWithContentDescription("Add deadline").assertDoesNotExist()
        rule.onNodeWithContentDescription("Add tags").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add checklist").assertIsDisplayed()

        rule.onNodeWithText("Water the plants").performClick()
        rule.onNodeWithContentDescription("Add date").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add deadline").assertIsDisplayed()
    }

    @Test
    fun tagChipsTakeThePlaceOfTheTagButton() {
        val party = TaskRowModel(
            id = "party",
            title = "Buy party decorations",
            details = TaskDetailsModel(tags = listOf("Errand")),
        )
        show(party)

        rule.onNodeWithText("Buy party decorations").performClick()

        rule.onNodeWithText("Errand").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add tags").assertDoesNotExist()
    }

    @Test
    fun reminderTimeShowsNextToTheDateOnlyWhenItIsSet() {
        val withReminder = TaskRowModel(
            id = "dentist",
            title = "Call the dentist",
            details = TaskDetailsModel(
                schedule = ScheduleModel(ScheduleKind.Date, "Fri, Nov 6", reminder = "9:00 AM"),
            ),
        )
        show(withReminder, plants)

        rule.onNodeWithText("Call the dentist").performClick()
        rule.onNodeWithText("Fri, Nov 6").assertIsDisplayed()
        rule.onNodeWithText("9:00 AM").assertIsDisplayed()
    }

    @Test
    fun checklistButtonOpensAnEmptyChecklistLine() {
        show(plants)

        rule.onNodeWithText("Water the plants").performClick()
        rule.onNodeWithContentDescription("Add checklist").performClick()

        rule.onNodeWithTag("new_checklist_field").assertIsDisplayed()
    }
}
