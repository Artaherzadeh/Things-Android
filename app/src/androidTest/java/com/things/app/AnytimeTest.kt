package com.things.app

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.things.app.ui.anytime.AnytimeScreen
import com.things.app.ui.anytime.sampleAnytimeState
import com.things.app.ui.theme.ThingsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnytimeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun looseTasksComeBeforeFirstProjectHeader() {
        val state = sampleAnytimeState()
        rule.setContent { ThingsTheme { AnytimeScreen(state) } }

        val looseY = rule.onNodeWithText(state.looseTasks.first().title).fetchSemanticsNode().positionInRoot.y
        val headerY = rule.onNodeWithText(state.groups.first().projectTitle).fetchSemanticsNode().positionInRoot.y
        assertTrue("Loose to-dos must appear above the first project header", looseY < headerY)
    }

    @Test
    fun longProjectCollapsesAndShowMoreRevealsTheRest() {
        rule.setContent { ThingsTheme { AnytimeScreen(sampleAnytimeState()) } }

        rule.onNodeWithText("Water the plants").assertDoesNotExist()
        rule.onNodeWithText("Show 1 more").assertIsDisplayed().performClick()
        rule.onNodeWithText("Water the plants").assertIsDisplayed()
        rule.onNodeWithText("Show 1 more").assertDoesNotExist()
    }
}

@RunWith(AndroidJUnit4::class)
class AnytimeNavigationTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tappingAnytimeOpensAnytimeAndBackReturnsHome() {
        rule.onNodeWithText("Anytime").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("anytime_title").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("anytime_title").assertIsDisplayed()

        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Quick Find").fetchSemanticsNodes().isNotEmpty() }
    }
}
