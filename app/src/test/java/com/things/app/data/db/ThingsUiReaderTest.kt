package com.things.app.data.db

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThingsUiReaderTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC)
    private fun text(value: String?): DbValue = value?.let(DbValue::Text) ?: DbValue.Null
    private fun number(value: Long?): DbValue = value?.let(DbValue::Integer) ?: DbValue.Null
    private fun packed(year: Int, month: Int, day: Int) = ThingsDate.packDate(LocalDate.of(year, month, day)).toLong()
    private fun task(
        id: String, type: Long = 0, project: String? = null, heading: String? = null,
        status: Long = 0, startDate: Long? = null, deadline: Long? = null,
        notes: String? = null, stopDate: Double? = null, rule: ByteArray? = null,
    ) = TaskRecord(DbRecord(mapOf(
        "uuid" to DbValue.Text(id), "type" to DbValue.Integer(type), "status" to DbValue.Integer(status),
        "trashed" to DbValue.Integer(0), "title" to DbValue.Text(id), "notes" to text(notes),
        "project" to text(project), "heading" to text(heading), "area" to DbValue.Null,
        "start" to DbValue.Integer(1), "startDate" to number(startDate), "startBucket" to DbValue.Integer(0),
        "reminderTime" to DbValue.Null, "deadline" to number(deadline),
        "stopDate" to (stopDate?.let(DbValue::Real) ?: DbValue.Null),
        "rt1_repeatingTemplate" to DbValue.Null,
        "rt1_recurrenceRule" to (rule?.let(DbValue::Blob) ?: DbValue.Null), "repeater" to DbValue.Null,
        "index" to DbValue.Integer(0), "todayIndex" to DbValue.Null,
        "creationDate" to DbValue.Null, "userModificationDate" to DbValue.Null,
    )))
    private fun snapshot(
        tasks: List<TaskRecord>, checklist: List<ChecklistRecord> = emptyList(),
        tags: List<TagRecord> = emptyList(), taskTags: List<TaskTagLink> = emptyList(),
    ) = ThingsSnapshot(tasks, emptyList(), tags, checklist, taskTags, emptyList())
    private fun read(data: ThingsSnapshot) = ThingsUiReader(clock = clock).map(data)
    @Test fun headingOnlyMembershipAppearsUnderHeading() {
        val result = read(snapshot(listOf(task("p", type = 1), task("h", type = 2, project = "p"), task("t", heading = "h"))))
        val p = result.projects.getValue("p")
        assertTrue(p.tasks.isEmpty())
        assertEquals(listOf("t"), p.headings.single().tasks.map { it.id })
    }
    @Test fun projectNotesAndDeadlineReachUi() {
        val notes = "first  \n\n    code\t \n"
        val p = read(snapshot(listOf(task("p", type = 1, notes = notes, deadline = packed(2026, 11, 18))))).projects.getValue("p")
        assertEquals(notes, p.notes)
        assertEquals("11 days left", p.deadline?.remainingLabel)
    }
    @Test fun futureAndCompletedTasksAreGrouped() {
        val result = read(snapshot(listOf(task("p", type = 1), task("active", project = "p"),
            task("future", project = "p", startDate = packed(2026, 11, 18)),
            task("done", project = "p", status = 3, stopDate = Instant.parse("2026-11-06T10:00:00Z").epochSecond.toDouble()))))
        val p = result.projects.getValue("p")
        assertEquals(listOf("active"), p.tasks.map { it.id })
        assertEquals(listOf("future"), p.laterTasks.map { it.id })
        assertEquals("11/6", p.loggedTasks.single().dateLabel)
        assertEquals(0.5f, p.progress, 0.0001f)
    }
    @Test fun checklistAndTagsComeFromRows() {
        val item = ChecklistRecord(DbRecord(mapOf("uuid" to DbValue.Text("c"), "task" to DbValue.Text("t"),
            "title" to DbValue.Text("Bring documents"), "status" to DbValue.Integer(3), "index" to DbValue.Integer(0))))
        val tag = TagRecord(DbRecord(mapOf("uuid" to DbValue.Text("tag"), "title" to DbValue.Text("Wishlist"),
            "parent" to DbValue.Null, "index" to DbValue.Integer(0))))
        val ui = read(snapshot(listOf(task("t")), listOf(item), listOf(tag), listOf(TaskTagLink("t", "tag")))).tasks.getValue("t")
        assertEquals(listOf("Wishlist"), ui.details.tags)
        assertTrue(ui.hasTag && ui.hasChecklist && ui.details.checklist.single().done)
        assertEquals("c", ui.details.checklist.single().id)
    }
    @Test fun invalidDateProducesWarning() {
        val invalid = ((2026 shl 16) or (2 shl 12) or (30 shl 7)).toLong()
        val result = read(snapshot(listOf(task("t", deadline = invalid))))
        assertEquals(null, result.tasks.getValue("t").details.deadline)
        assertTrue(result.warnings.any { it.field == "deadline" })
        assertEquals(invalid, result.snapshot.tasks.single().deadline)
    }
    @Test fun repeatBlobIsPreserved() {
        val result = read(snapshot(listOf(task("p", type = 1), task("template", project = "p", rule = byteArrayOf(1, 2, 3)))))
        assertTrue(result.tasks.getValue("template").isRepeatingTemplate)
        assertEquals(null, result.tasks.getValue("template").details.repeat)
        assertEquals(listOf<Byte>(1, 2, 3), result.snapshot.tasks.first { it.id == "template" }.recurrenceRule?.toList())
        assertTrue(result.warnings.any { it.entityId == "template" && it.field == "repeat" })
    }
    @Test fun homeUsesMappedProgress() {
        val result = read(snapshot(listOf(task("p", type = 1), task("open", project = "p"),
            task("done", project = "p", status = 3, stopDate = clock.instant().epochSecond.toDouble()))))
        assertEquals("p", result.home.looseProjects.single().id)
        assertEquals(result.projects.getValue("p").progress, result.home.looseProjects.single().progress, 0.0001f)
    }
    @Test fun unknownStatusIsNotClassifiedAsOpen() {
        val result = read(snapshot(listOf(task("p", type = 1), task("unknown", project = "p", status = 99))))
        val p = result.projects.getValue("p")
        assertTrue(p.tasks.isEmpty() && p.loggedTasks.isEmpty())
        assertTrue(result.warnings.any { it.entityId == "unknown" && it.field == "status" })
        assertTrue(result.snapshot.tasks.isNotEmpty())
    }
}
