package com.things.app.data.db

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class ThingsListReaderTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC)
    private fun packed(day: Int) = ThingsDate.packDate(LocalDate.of(2026, 11, day)).toLong()
    private fun item(id: String, start: Long = 1, date: Long? = null, deadline: Long? = null,
        bucket: Long = 0, type: Long = 0, project: String? = null, status: Long = 0): TaskRecord {
        fun number(v: Long?): DbValue = v?.let(DbValue::Integer) ?: DbValue.Null
        return TaskRecord(DbRecord(mapOf(
            "uuid" to DbValue.Text(id), "type" to DbValue.Integer(type),
            "status" to DbValue.Integer(status), "trashed" to DbValue.Integer(0),
            "start" to DbValue.Integer(start), "startDate" to number(date),
            "deadline" to number(deadline), "startBucket" to DbValue.Integer(bucket),
            "project" to (project?.let(DbValue::Text) ?: DbValue.Null), "heading" to DbValue.Null,
            "rt1_recurrenceRule" to DbValue.Null, "repeater" to DbValue.Null,
            "index" to DbValue.Integer(0), "todayIndex" to DbValue.Null,
            "todayIndexReferenceDate" to DbValue.Null, "stopDate" to DbValue.Null,
        )))
    }
    private fun lists(vararg items: TaskRecord) = ThingsListReader(clock).read(
        ThingsSnapshot(items.toList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
    @Test fun dueTodayAppearsWithoutStartDate() {
        assertEquals(listOf("t"), lists(item("t", deadline = packed(7))).today.map { it.id })
    }
    @Test fun futureDeadlineDoesNotDeactivateAnAnytimeTask() {
        val result = lists(item("t", deadline = packed(18)))
        assertEquals(listOf("t"), result.anytime.map { it.id })
        assertEquals(UpcomingEventKind.Deadline, result.upcoming.single().kind)
    }
    @Test fun somedayIsExcludedFromDateBasedLists() {
        val result = lists(item("t", start = 2, deadline = packed(7)))
        assertEquals(listOf("t"), result.someday.map { it.id })
        assertTrue(result.today.isEmpty() && result.anytime.isEmpty() && result.upcoming.isEmpty())
    }
    @Test fun upcomingRetainsBothDates() {
        val result = lists(item("t", date = packed(10), deadline = packed(18)))
        assertEquals(listOf(UpcomingEventKind.Start, UpcomingEventKind.Deadline), result.upcoming.map { it.kind })
    }
    @Test fun projectSomedayPropagatesToItsTask() {
        val result = lists(item("p", start = 2, type = 1), item("t", project = "p"))
        assertTrue(result.someday.any { it.id == "t" })
        assertTrue(result.anytime.isEmpty())
    }
    @Test fun invalidProjectIsQuarantined() {
        val result = lists(item("t", project = "missing"))
        assertEquals(listOf("t"), result.unclassified.map { it.id })
        assertTrue(result.anytime.isEmpty())
    }
    @Test fun eveningAndTodayAreSeparate() {
        val result = lists(item("t", date = packed(7), bucket = 1))
        assertTrue(result.today.isEmpty())
        assertEquals(listOf("t"), result.evening.map { it.id })
    }
    @Test fun completedIsNotSilentlyNamedLogbook() {
        val result = lists(item("done", status = 3))
        assertEquals(listOf("done"), result.completed.map { it.id })
        assertTrue(result.anytime.isEmpty())
    }
}
