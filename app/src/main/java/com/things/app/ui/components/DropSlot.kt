package com.things.app.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/** Height of the empty space that opens in the list where a dragged add button would drop a to-do. */
val DropGapHeight = 56.dp

/**
 * Where the list opens a gap while the add button is dragged over it. Rows, project headers and the
 * page title read it through [LocalDropSlot] and make room: the other rows slide down.
 *
 * [atTop]: under the page title. [beforeTaskId] / [afterTaskId]: above or under that to-do.
 * [afterGroupId]: under the header of that project or heading. [beforeGroupId]: above the header of
 * that heading (where a new heading would go).
 */
@Immutable
class DropSlot(
    val atTop: Boolean = false,
    val beforeTaskId: String? = null,
    val afterTaskId: String? = null,
    val afterGroupId: String? = null,
    val beforeGroupId: String? = null,
) {
    companion object {
        val None = DropSlot()
    }
}

val LocalDropSlot = compositionLocalOf { DropSlot.None }
