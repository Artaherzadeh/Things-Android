package com.things.app

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
import com.things.app.ui.theme.ThingsTheme
import com.things.app.ui.today.TodayScreen
import com.things.app.ui.today.sampleTodayState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayNavigationTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tappingTodayOpensTodayAndBackReturnsHome() {
        rule.onNodeWithText("Today").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("today_title").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("today_title").assertIsDisplayed()

        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Quick Find").fetchSemanticsNodes().isNotEmpty() }
    }
}

@RunWith(AndroidJUnit4::class)
class TodayScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun looseItemsComeBeforeFirstProjectHeader() {
        val state = sampleTodayState()
        rule.setContent { ThingsTheme { TodayScreen(state) } }

        val looseY = rule.onNodeWithText(state.looseItems.first().title).fetchSemanticsNode().positionInRoot.y
        val headerY = rule.onNodeWithText(state.groups.first().projectTitle).fetchSemanticsNode().positionInRoot.y
        assertTrue("Loose to-dos must appear above the first project header", looseY < headerY)
    }

    @Test
    fun noLooseItemsStartsDirectlyWithFirstProject() {
        val state = sampleTodayState().copy(looseItems = emptyList())
        rule.setContent { ThingsTheme { TodayScreen(state) } }
        rule.onNodeWithText(state.groups.first().projectTitle).assertIsDisplayed()
    }
}
