package com.things.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.things.app.ui.area.AreaScreen
import com.things.app.ui.area.sampleAreaState
import com.things.app.ui.theme.ThingsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AreaScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsEveryProjectAndReportsTheTappedOne() {
        val state = sampleAreaState()
        var tapped: String? = null
        rule.setContent { ThingsTheme { AreaScreen(state, onProjectClick = { tapped = it }) } }

        state.projects.forEach { rule.onNodeWithText(it.title).assertIsDisplayed() }

        rule.onNodeWithText("Finance").performClick()
        assertEquals("finance", tapped)
    }
}

@RunWith(AndroidJUnit4::class)
class AreaNavigationTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tappingAnAreaOnHomeOpensItsPageAndBackReturns() {
        rule.onNodeWithText("Family").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("area_title").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("area_title").assertTextEquals("Family")
        rule.onNodeWithText("Vacation in Rome").assertIsDisplayed()

        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Quick Find").fetchSemanticsNodes().isNotEmpty() }
    }
}
