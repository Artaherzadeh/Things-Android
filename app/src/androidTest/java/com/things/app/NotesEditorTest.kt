package com.things.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.things.app.ui.components.NotesEditor
import com.things.app.ui.theme.ThingsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesEditorTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun selectingTextShowsTheToolbarAndBoldWrapsTheSelection() {
        var value by mutableStateOf(TextFieldValue("hello world", TextRange(0, 5)))
        rule.setContent { ThingsTheme { NotesEditor(value = value, onValueChange = { value = it }) } }

        rule.onNodeWithTag("notes_editor").performSemanticsAction(SemanticsActions.RequestFocus)
        rule.onNodeWithContentDescription("Bold").assertIsDisplayed()

        rule.onNodeWithContentDescription("Bold").performClick()
        assertEquals("**hello** world", value.text)
        assertEquals(TextRange(2, 7), value.selection)
    }

    @Test
    fun theToolbarCanSwitchToParagraphStyles() {
        var value by mutableStateOf(TextFieldValue("one", TextRange(0, 3)))
        rule.setContent { ThingsTheme { NotesEditor(value = value, onValueChange = { value = it }) } }

        rule.onNodeWithTag("notes_editor").performSemanticsAction(SemanticsActions.RequestFocus)
        rule.onNodeWithContentDescription("Paragraph styles").performClick()
        rule.onNodeWithContentDescription("List").performClick()

        assertEquals("- one", value.text)
    }

    @Test
    fun noToolbarWithoutASelection() {
        var value by mutableStateOf(TextFieldValue("hello", TextRange(2)))
        rule.setContent { ThingsTheme { NotesEditor(value = value, onValueChange = { value = it }) } }

        rule.onNodeWithTag("notes_editor").performSemanticsAction(SemanticsActions.RequestFocus)
        rule.onNodeWithContentDescription("Bold").assertDoesNotExist()
    }
}
