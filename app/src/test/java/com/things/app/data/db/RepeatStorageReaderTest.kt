package com.things.app.data.db

import org.junit.Assert.*
import org.junit.Test

class RepeatStorageReaderTest {
    private fun task(id: String, type: Long = 0, rule: ByteArray? = null,
        repeater: ByteArray? = null, template: String? = null, status: Long = 0,
        trashed: Long = 0, creation: Double = 1.0): TaskRecord {
        val values = mutableMapOf<String, DbValue>(
            "uuid" to DbValue.Text(id), "type" to DbValue.Integer(type),
            "status" to DbValue.Integer(status), "trashed" to DbValue.Integer(trashed),
            "creationDate" to DbValue.Real(creation),
            "rt1_recurrenceRule" to (rule?.let(DbValue::Blob) ?: DbValue.Null),
            "repeater" to (repeater?.let(DbValue::Blob) ?: DbValue.Null),
            "rt1_repeatingTemplate" to (template?.let(DbValue::Text) ?: DbValue.Null),
            "repeaterMigrationDate" to DbValue.Null,
        )
        listOf("rt1_instanceCreationStartDate", "rt1_instanceCreationPaused", "rt1_instanceCreationCount",
            "rt1_afterCompletionReferenceDate", "rt1_nextInstanceStartDate").forEach { values[it] = DbValue.Null }
        return TaskRecord(DbRecord(values))
    }
    private fun read(vararg tasks: TaskRecord) = RepeatStorageReader().read(
        ThingsSnapshot(tasks.toList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
    @Test fun bothPayloadsSurviveWithoutDecoding() {
        val result = read(task("p", type = 1, rule = byteArrayOf(1,2), repeater = byteArrayOf(3,4)))
        val payload = result.templates.getValue("p").payload
        assertArrayEquals(byteArrayOf(1,2), payload.legacyRule?.bytes())
        assertArrayEquals(byteArrayOf(3,4), payload.repeater?.bytes())
    }
    @Test fun instancesRetainTheirIdsAndCreationOrder() {
        val result = read(task("t", rule = byteArrayOf(1)),
            task("new", template = "t", creation = 2.0), task("old", template = "t", creation = 1.0))
        assertEquals(listOf("old", "new"), result.instancesOf("t").map { it.task.id })
        assertTrue(result.unresolvedInstances.isEmpty())
    }
    @Test fun missingTemplateProducesWarningWithoutDroppingInstance() {
        val result = read(task("copy", template = "missing"))
        assertEquals(1, result.instances.size)
        assertEquals(1, result.unresolvedInstances.size)
        assertTrue(result.warnings.isNotEmpty())
    }
    @Test fun completedAndTrashedHistoryIsRetained() {
        val result = read(task("t", rule = byteArrayOf(1), trashed = 1),
            task("copy", template = "t", status = 3, trashed = 1))
        assertEquals(1, result.instances.size)
        assertEquals(3L, result.instances.single().task.status)
        assertEquals(1L, result.instances.single().task.trashed)
    }
    @Test fun emptyPayloadIsPreservedButNotUsable() {
        val result = read(task("t", rule = byteArrayOf()))
        assertNotNull(result.templates.getValue("t").payload.legacyRule)
        assertFalse(result.templates.getValue("t").payload.hasRuleBytes)
        assertTrue(result.warnings.isNotEmpty())
    }
    @Test fun selfReferenceAndTypeMismatchAreUnresolved() {
        val self = read(task("t", rule = byteArrayOf(1), template = "t"))
        assertEquals(1, self.unresolvedInstances.size)
        val mismatch = read(task("p", type = 1, rule = byteArrayOf(1)), task("copy", template = "p"))
        assertEquals(1, mismatch.unresolvedInstances.size)
    }
}
