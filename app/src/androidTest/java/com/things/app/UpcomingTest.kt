package com.things.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.things.app.ui.theme.ThingsTheme
import com.things.app.ui.upcoming.UpcomingScreen
import com.things.app.ui.upcoming.sampleUpcomingState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpcomingScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsDayLabelsAndRepeatBadgeOnlyForRepeatingTasks() {
        val state = sampleUpcomingState()
        rule.setContent { ThingsTheme { UpcomingScreen(state) } }

        rule.onNodeWithText("Tomorrow").assertIsDisplayed()
        val repeating = state.days.sumOf { day -> day.tasks.count { it.isRecurring } }
        rule.onAllNodesWithContentDescription("Repeats", useUnmergedTree = true).assertCountEquals(repeating)
    }
}

@RunWith(AndroidJUnit4::class)
class UpcomingNavigationTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tappingUpcomingOpensUpcomingAndBackReturnsHome() {
        rule.onNodeWithText("Upcoming").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("upcoming_title").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("upcoming_title").assertIsDisplayed()

        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Quick Find").fetchSemanticsNodes().isNotEmpty() }
    }
}
