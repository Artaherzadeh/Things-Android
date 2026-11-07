package com.things.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThingsSnapshotTest {
    private fun task(id: String, type: Long, project: String? = null, heading: String? = null) = TaskRecord(
        DbRecord(mapOf("uuid" to DbValue.Text(id), "type" to DbValue.Integer(type),
            "project" to text(project), "heading" to text(heading))),
    )
    private fun text(value: String?): DbValue = value?.let(DbValue::Text) ?: DbValue.Null
    private fun snapshot(tasks: List<TaskRecord>) = ThingsSnapshot(tasks, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    @Test fun projectMembershipIncludesHeadingOnlyLinks() {
        val data = snapshot(listOf(task("p", 1), task("h", 2, project = "p"),
            task("loose", 0, project = "p"), task("via-heading", 0, heading = "h"),
            task("both", 0, project = "p", heading = "h"), task("other", 0, project = "other")))
        assertEquals(listOf("loose", "via-heading", "both"), data.tasksInProject("p").map { it.id })
    }
    @Test fun headingMembershipPreservesOrder() {
        val data = snapshot(listOf(task("a", 0, heading = "h1"), task("b", 0, heading = "h2"), task("c", 0, heading = "h1")))
        assertEquals(listOf("a", "c"), data.tasksInHeading("h1").map { it.id })
    }
    @Test fun unknownTypeIsPreserved() {
        val record = task("unknown", 99)
        assertEquals(99L, record.type)
        assertNull(record.knownType)
    }
    @Test fun nullIsNotConverted() {
        val row = DbRecord(mapOf("title" to DbValue.Null, "deadline" to DbValue.Null))
        assertNull(row.text("title"))
        assertNull(row.integer("deadline"))
    }
    @Test fun blobCopiesProtectStoredBytes() {
        val source = byteArrayOf(1, 2, 3)
        val blob = DbValue.Blob(source)
        source[0] = 9
        blob.bytes()[1] = 9
        assertEquals(listOf<Byte>(1, 2, 3), blob.bytes().toList())
    }
    @Test fun unknownColumnsRemainAvailable() {
        assertEquals("keep me", DbRecord(mapOf("future" to DbValue.Text("keep me"))).text("future"))
    }
}
