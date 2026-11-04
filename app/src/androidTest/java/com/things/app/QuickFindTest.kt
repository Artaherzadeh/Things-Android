package com.things.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.things.app.ui.quickfind.QuickFindItem
import com.things.app.ui.quickfind.QuickFindOverlay
import com.things.app.ui.quickfind.QuickFindTarget
import com.things.app.ui.quickfind.sampleQuickFindState
import com.things.app.ui.theme.ThingsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickFindOverlayTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsRecentListsAndReportsTheTappedOne() {
        val state = sampleQuickFindState()
        var tapped: QuickFindItem? = null
        rule.setContent {
            ThingsTheme {
                QuickFindOverlay(
                    visible = true,
                    state = state,
                    onDismiss = {},
                    onItemClick = { tapped = it },
                )
            }
        }

        rule.onNodeWithText("Recent").assertIsDisplayed()
        state.recent.forEach { rule.onNodeWithText(it.title).assertIsDisplayed() }

        rule.onNodeWithText("Personal").performClick()
        assertEquals(QuickFindTarget.Project("personal"), tapped?.target)
    }

    @Test
    fun closeButtonDismisses() {
        var dismissed = false
        rule.setContent {
            ThingsTheme {
                QuickFindOverlay(
                    visible = true,
                    state = sampleQuickFindState(),
                    onDismiss = { dismissed = true },
                    onItemClick = {},
                )
            }
        }

        rule.onNodeWithContentDescription("Close Quick Find").performClick()
        assertTrue(dismissed)
    }
}

@RunWith(AndroidJUnit4::class)
class QuickFindNavigationTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tappingQuickFindOnHomeOpensThePopoverAndCloseHidesIt() {
        rule.onNodeWithText("Quick Find").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Recent").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Recent").assertIsDisplayed()

        rule.onNodeWithContentDescription("Close Quick Find").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Recent").fetchSemanticsNodes().isEmpty() }
    }
}
