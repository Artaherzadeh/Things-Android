package com.things.app.data.db

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class UnifiedReadTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC)
    private fun packed(day: Int) = ThingsDate.packDate(LocalDate.of(2026, 11, day)).toLong()
    private fun row(id: String, type: Long = 0, project: String? = null, heading: String? = null,
        start: Long = 1, startDate: Long? = null, deadline: Long? = null, bucket: Long = 0,
        status: Long = 0, position: Long = 0, todayPosition: Long? = null, todayReference: Long? = null): TaskRecord {
        val values = mutableMapOf<String, DbValue>()
        listOf("notes", "project", "heading", "area", "startDate", "deadline", "reminderTime",
            "stopDate", "rt1_recurrenceRule", "repeater", "rt1_repeatingTemplate", "todayIndex",
            "todayIndexReferenceDate").forEach { values[it] = DbValue.Null }
        fun number(name: String, v: Long?) { values[name] = v?.let(DbValue::Integer) ?: DbValue.Null }
        values["uuid"] = DbValue.Text(id)
        values["title"] = DbValue.Text(id)
        number("type", type); number("status", status); number("trashed", 0)
        number("start", start); number("startBucket", bucket); number("startDate", startDate)
        number("deadline", deadline); number("index", position); number("todayIndex", todayPosition)
        number("todayIndexReferenceDate", todayReference)
        values["project"] = project?.let(DbValue::Text) ?: DbValue.Null
        values["heading"] = heading?.let(DbValue::Text) ?: DbValue.Null
        return TaskRecord(DbRecord(values))
    }
    private fun read(vararg rows: TaskRecord) = ThingsUiReader(clock = clock, locale = Locale.US).map(
        ThingsSnapshot(rows.toList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
    @Test fun todayCounterAndTaskMarkComeFromSharedMembership() {
        val result = read(row("due", deadline = packed(7)), row("evening", startDate = packed(7), bucket = 1))
        val lists = requireNotNull(result.lists)
        assertEquals(2, result.home.todayCount)
        assertEquals(setOf("due", "evening"), (lists.today + lists.evening).map { it.id }.toSet())
        assertTrue(result.tasks.values.all { it.isToday })
    }
    @Test fun futureDeadlineDoesNotMoveProjectTaskToLater() {
        val result = read(row("p", type = 1), row("t", project = "p", deadline = packed(18)))
        assertEquals(listOf("t"), result.projects.getValue("p").tasks.map { it.id })
        assertTrue(result.projects.getValue("p").laterTasks.isEmpty())
    }
    @Test fun inactiveProjectsRemainAddressableButAreNotOnHome() {
        val result = read(row("future", type = 1, startDate = packed(18)), row("someday", type = 1, start = 2))
        assertEquals(setOf("future", "someday"), result.projects.keys)
        assertTrue(result.home.looseProjects.isEmpty())
    }
    @Test fun conflictingOwnershipCannotPlaceOneTaskInTwoProjects() {
        val result = read(row("p1", type = 1), row("p2", type = 1), row("h", type = 2, project = "p2"),
            row("t", project = "p1", heading = "h"))
        assertTrue(result.projects.values.all { p -> p.tasks.isEmpty() && p.headings.all { it.tasks.isEmpty() } })
        assertFalse("t" in result.tasks)
        assertTrue(requireNotNull(result.lists).unclassified.any { it.id == "t" })
        assertTrue(result.snapshot.tasks.any { it.id == "t" })
    }
    @Test fun headingOnlyMembershipIsUsedByPresentation() {
        val result = read(row("p", type = 1), row("h", type = 2, project = "p"), row("t", heading = "h"))
        assertEquals(listOf("t"), result.projects.getValue("p").headings.single().tasks.map { it.id })
        assertEquals("p", requireNotNull(result.lists).projectOwners["t"])
    }
    @Test fun staleTodayOrderIsNotReusedAndCurrentOrderWins() {
        val result = read(
            row("stale", startDate = packed(7), position = 1, todayPosition = -100, todayReference = packed(6)),
            row("current", startDate = packed(7), position = 2, todayPosition = 10, todayReference = packed(7)))
        assertEquals(listOf("current", "stale"), requireNotNull(result.lists).today.map { it.id })
    }
    @Test fun invalidProjectDateDoesNotLeakChildIntoToday() {
        val invalid = ((2026 shl 16) or (2 shl 12) or (30 shl 7)).toLong()
        val result = read(row("p", type = 1, startDate = invalid), row("t", project = "p", deadline = packed(7)))
        assertEquals(0, result.home.todayCount)
        assertFalse("t" in result.tasks)
        assertTrue(result.warnings.any { it.entityId == "t" && it.field == "project" })
    }
    @Test fun futureTaskSectionAndSharedProjectionAgree() {
        val result = read(row("p", type = 1), row("t", project = "p", startDate = packed(18)))
        val expected = requireNotNull(result.lists).projectSections.getValue("p").later.map { it.id }
        assertEquals(expected, result.projects.getValue("p").laterTasks.map { it.id })
        assertTrue(result.projects.getValue("p").tasks.isEmpty())
    }
}
