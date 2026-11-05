package com.things.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.things.app.ui.project.ProjectScreen
import com.things.app.ui.project.sampleProjectState
import com.things.app.ui.project.sampleVacationProjectState
import com.things.app.ui.theme.ThingsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun laterAndLoggedItemsAreHiddenUntilRevealed() {
        val state = sampleProjectState()
        rule.setContent { ThingsTheme { ProjectScreen(state) } }

        rule.onNodeWithText("Notes").assertIsDisplayed()
        rule.onNodeWithText("Waiting").assertDoesNotExist()
        rule.onNodeWithText("Show ${state.laterTasks.size} later items").assertIsDisplayed()
        rule.onNodeWithText("Show ${state.loggedTasks.size} logged items").assertIsDisplayed()
    }

    @Test
    fun togglesRevealAndHideTheGroups() {
        val state = sampleProjectState()
        rule.setContent { ThingsTheme { ProjectScreen(state) } }

        rule.onNodeWithText("Show ${state.laterTasks.size} later items").performClick()
        rule.onNodeWithText("Waiting").assertIsDisplayed()
        rule.onNodeWithText("Hide later items").assertIsDisplayed()

        rule.onNodeWithText("Show ${state.loggedTasks.size} logged items").performClick()
        rule.onNodeWithText("Hide logged items").assertExists()

        rule.onNodeWithText("Hide later items").performClick()
        rule.onNodeWithText("Waiting").assertDoesNotExist()
    }

    @Test
    fun headingTitleComesBeforeItsTasks() {
        val state = sampleVacationProjectState()
        rule.setContent { ThingsTheme { ProjectScreen(state) } }

        val headingY = rule.onNodeWithText("Planning").fetchSemanticsNode().positionInRoot.y
        val taskY = rule.onNodeWithText("Borrow Emma's travel guide").fetchSemanticsNode().positionInRoot.y
        assertTrue("A heading must be drawn above the to-dos under it", headingY < taskY)
        rule.onNodeWithContentDescription("Heading menu: Planning").assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
class ProjectNavigationTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tappingAProjectOnHomeOpensItsPage() {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Vacation in Rome"))
        rule.onNodeWithText("Vacation in Rome").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("project_title").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("project_title").assertTextEquals("Vacation in Rome")
        rule.onNodeWithText("Planning").assertExists()

        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Quick Find").fetchSemanticsNodes().isNotEmpty() }
    }
}
