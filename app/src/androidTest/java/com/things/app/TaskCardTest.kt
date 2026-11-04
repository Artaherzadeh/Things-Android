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

    private fun show() {
        rule.setContent {
            ThingsTheme {
                val expansion = rememberTaskExpansion()
                Column {
                    ExpandableTaskRow(cleaning, expansion)
                    ExpandableTaskRow(plants, expansion)
                }
            }
        }
    }

    @Test
    fun tappingARowOpensItAsACardWithItsDetails() {
        show()
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
    fun openingAnotherToDoClosesTheFirstOne() {
        show()

        rule.onNodeWithText("Cleaning House").performClick()
        rule.onNodeWithText("Water the plants").performClick()

        rule.onNodeWithTag("task_card_cleaning").assertDoesNotExist()
        rule.onNodeWithTag("task_card_plants").assertIsDisplayed()
    }

    @Test
    fun editsAreKeptWithoutSaving() {
        show()

        rule.onNodeWithText("Cleaning House").performClick()
        rule.onNodeWithTag("task_title_field").performTextReplacement("Cleaning the kitchen")
        rule.onNodeWithText("Water the plants").performClick()

        rule.onNodeWithTag("task_card_cleaning").assertDoesNotExist()
        rule.onNodeWithText("Cleaning the kitchen").assertIsDisplayed()
    }

    @Test
    fun addButtonsShowOnlyWhileThereIsNothingOfThatKind() {
        show()

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
    fun checklistButtonOpensAnEmptyChecklistLine() {
        show()

        rule.onNodeWithText("Water the plants").performClick()
        rule.onNodeWithContentDescription("Add checklist").performClick()

        rule.onNodeWithTag("new_checklist_field").assertIsDisplayed()
    }
}
