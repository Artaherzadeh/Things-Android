package com.things.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MenuActionsTest {

    @Test
    fun projectPageMenuHasShareInASectionOfItsOwn() {
        assertEquals(
            listOf(
                PageMenuAction.FilterByTag,
                PageMenuAction.NewHeading,
                PageMenuAction.Select,
                PageMenuAction.Paste,
                PageMenuAction.Divider,
                PageMenuAction.Share,
            ),
            ProjectMenuActions,
        )
    }

    @Test
    fun areaPageMenuKeepsItsThreeSections() {
        assertEquals(
            listOf(
                PageMenuAction.AddTags,
                PageMenuAction.Divider,
                PageMenuAction.DeleteArea,
                PageMenuAction.Divider,
                PageMenuAction.Share,
            ),
            AreaMenuActions,
        )
    }

    @Test
    fun listPageMenuIsUnchanged() {
        assertEquals(
            listOf(PageMenuAction.FilterByTag, PageMenuAction.Select, PageMenuAction.Paste, PageMenuAction.Share),
            DefaultMenuActions,
        )
    }

    @Test
    fun theDividerIsNeverAnEnabledAction() {
        assertFalse(PageMenuAction.Divider in ProjectEnabledMenuActions)
        assertFalse(PageMenuAction.Divider in DefaultEnabledMenuActions)
    }

    @Test
    fun projectTitleMenuKeepsItsThreeSections() {
        assertEquals(
            listOf(
                listOf(
                    ProjectMenuAction.CompleteProject,
                    ProjectMenuAction.When,
                    ProjectMenuAction.AddTags,
                    ProjectMenuAction.AddDeadline,
                    ProjectMenuAction.Repeat,
                ),
                listOf(ProjectMenuAction.Move, ProjectMenuAction.Duplicate, ProjectMenuAction.DeleteProject),
                listOf(ProjectMenuAction.Share),
            ),
            ProjectMenuGroups,
        )
    }

    @Test
    fun headingMenuKeepsItsOrderAndOnlyWorkingEntriesAreEnabled() {
        assertEquals(
            listOf(
                HeadingMenuAction.Archive,
                HeadingMenuAction.Move,
                HeadingMenuAction.Duplicate,
                HeadingMenuAction.ConvertToProject,
                HeadingMenuAction.Delete,
            ),
            HeadingMenuAction.values().toList(),
        )
        assertEquals(
            setOf(HeadingMenuAction.Duplicate, HeadingMenuAction.ConvertToProject, HeadingMenuAction.Delete),
            DefaultEnabledHeadingActions,
        )
    }
}
