package com.things.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.things.app.ui.settings.SettingsDialog
import com.things.app.ui.theme.ThingsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsDialogTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsMenuFirstAndOpensThingsCloudPage() {
        var dismissed = false
        rule.setContent {
            ThingsTheme {
                SettingsDialog(
                    onDismiss = { dismissed = true },
                )
            }
        }

        rule.onNodeWithText("Settings").assertIsDisplayed()
        rule.onNodeWithText("Things Cloud").assertIsDisplayed()
        rule.onNodeWithText("Get Things for Mac and iPhone.").assertIsDisplayed()

        // Tap Things Cloud
        rule.onNodeWithText("Things Cloud").performClick()

        // Verifies Things Cloud page opens
        rule.onNodeWithText("Signed Out").assertIsDisplayed()
        rule.onNodeWithText("Things Cloud sync is currently unavailable.").assertIsDisplayed()

        // Cloud switch should be disabled when cloud is not supported
        rule.onNodeWithContentDescription("Things Cloud Sync").assertIsNotEnabled()

        // Tap Back button to return to Settings menu
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Settings").assertIsDisplayed()

        // Tap Close button to dismiss dialog
        rule.onNodeWithContentDescription("Close").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun showsConnectedStateWhenAccountProvided() {
        rule.setContent {
            ThingsTheme {
                SettingsDialog(
                    onDismiss = {},
                    accountEmail = "test@example.com",
                    lastUpdated = "10 minutes ago",
                    cloudEnabled = true,
                    cloudSupported = true,
                )
            }
        }

        rule.onNodeWithText("Things Cloud").performClick()
        rule.onNodeWithText("test@example.com").assertIsDisplayed()
        rule.onNodeWithText("Your account is active.").assertIsDisplayed()
        rule.onNodeWithText("Last Updated: 10 minutes ago").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tappingSettingsOnHomeOpensDialogAndFullBackFlowWorks() {
        // Scroll to Settings button at bottom of Home and click it
        rule.onNodeWithText("Settings").performScrollTo()
        rule.onNodeWithText("Settings").performClick()

        // Verify Settings dialog opens
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Get Things for Mac and iPhone.").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Settings").assertIsDisplayed()

        // Open Things Cloud
        rule.onNodeWithText("Things Cloud").performClick()
        rule.onNodeWithText("Signed Out").assertIsDisplayed()

        // System Back from Things Cloud should return to Settings menu
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithText("Settings").assertIsDisplayed()

        // System Back from Settings menu should close the dialog
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Get Things for Mac and iPhone.").fetchSemanticsNodes().isEmpty() }

        // Reopening Settings should start on the Settings menu
        rule.onNodeWithText("Settings").performScrollTo()
        rule.onNodeWithText("Settings").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Get Things for Mac and iPhone.").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Settings").assertIsDisplayed()
    }
}
