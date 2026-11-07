package com.things.app.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Uses a fresh in-memory database, never the user's main.sqlite. */
class ThingsEntityOperationsTest {
    private lateinit var db: SQLiteDatabase
    private lateinit var ops: ThingsEntityOperations
    @Before fun setup() {
        db = SQLiteDatabase.create(null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val schema = context.assets.open("schema.sql").bufferedReader().use { it.readText() }
        schema.split(Regex("(?m)^-- @@\\s*$")).map { it.trim() }.filter { it.isNotEmpty() }.forEach { db.execSQL(it) }
        ops = ThingsEntityOperations({ db }, Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC), Dispatchers.Unconfined)
    }
    @After fun teardown() { db.close() }
    private fun item(type: Int, project: String? = null, heading: String? = null, area: String? = null, status: Int = 0): String {
        val id = UUID.randomUUID().toString()
        db.insertOrThrow("TMTask", null, ContentValues().apply {
            put("uuid", id); put("type", type); put("status", status); put("trashed", 0)
            put("title", "Title"); put("notes", "Markdown  \n\n    code\t")
            put("project", project); put("heading", heading); put("area", area)
            put("index", 0); put("start", 1); put("creationDate", 1.0); put("userModificationDate", 1.0)
            if (status == 3) put("stopDate", 123.0)
        })
        return id
    }
    private fun area(): String {
        val id = UUID.randomUUID().toString()
        db.insertOrThrow("TMArea", null, ContentValues().apply { put("uuid", id); put("title", "A"); put("index", 0); put("visible", 1) })
        return id
    }
    private fun tag(): String {
        val id = UUID.randomUUID().toString()
        db.insertOrThrow("TMTag", null, ContentValues().apply { put("uuid", id); put("title", "Wishlist"); put("index", 0) })
        return id
    }
    private fun checklist(task: String, status: Int = 3): String {
        val id = UUID.randomUUID().toString()
        db.insertOrThrow("TMChecklistItem", null, ContentValues().apply {
            put("uuid", id); put("task", task); put("title", "Child"); put("status", status); put("index", 0)
            if (status == 3) put("stopDate", 123.0)
        })
        return id
    }
    private fun scalar(sql: String, vararg args: String): String? = db.rawQuery(sql, args).use {
        if (!it.moveToFirst() || it.isNull(0)) null else it.getString(0)
    }
    @Test fun duplicatePreservesDetailsAndCopiesDependentRows() = runBlocking {
        val t = item(0)
        checklist(t)
        val tag = tag()
        db.execSQL("INSERT INTO TMTaskTag(tasks,tags) VALUES(?,?)", arrayOf(t, tag))
        db.execSQL("UPDATE TMTask SET deadline=132814464, rt1_recurrenceRule=? WHERE uuid=?", arrayOf(byteArrayOf(1,2,3), t))
        val copy = ops.duplicate(t).rootId
        assertNotEquals(t, copy)
        assertEquals(scalar("SELECT notes FROM TMTask WHERE uuid=?", t), scalar("SELECT notes FROM TMTask WHERE uuid=?", copy))
        assertEquals("132814464", scalar("SELECT deadline FROM TMTask WHERE uuid=?", copy))
        assertEquals("010203", scalar("SELECT hex(rt1_recurrenceRule) FROM TMTask WHERE uuid=?", copy))
        assertEquals("1", scalar("SELECT COUNT(*) FROM TMChecklistItem WHERE task=?", copy))
        assertEquals(tag, scalar("SELECT tags FROM TMTaskTag WHERE tasks=?", copy))
    }
    @Test fun projectDuplicateRemapsHeadingAndProjectIds() = runBlocking {
        val p = item(1); val h = item(2, project=p); val t = item(0, project=p, heading=h)
        val result = ops.duplicate(p)
        val copyTask = result.copiedTaskIds.getValue(t)
        assertEquals(result.rootId, scalar("SELECT project FROM TMTask WHERE uuid=?", copyTask))
        assertEquals(result.copiedTaskIds.getValue(h), scalar("SELECT heading FROM TMTask WHERE uuid=?", copyTask))
        assertEquals(h, scalar("SELECT heading FROM TMTask WHERE uuid=?", t))
    }
    @Test fun taskConversionPreservesDatesTagsAndOriginalChecklist() = runBlocking {
        val t = item(0); val c = checklist(t); val tag = tag()
        db.execSQL("INSERT INTO TMTaskTag(tasks,tags) VALUES(?,?)", arrayOf(t,tag))
        db.execSQL("UPDATE TMTask SET deadline=132814464, reminderTime=536870912, rt1_recurrenceRule=? WHERE uuid=?", arrayOf(byteArrayOf(4,5),t))
        val result = ops.convertTaskToProject(t)
        assertEquals("1", scalar("SELECT type FROM TMTask WHERE uuid=?", result.projectId))
        assertEquals("132814464", scalar("SELECT deadline FROM TMTask WHERE uuid=?", result.projectId))
        assertEquals("536870912", scalar("SELECT reminderTime FROM TMTask WHERE uuid=?", result.projectId))
        assertEquals("0405", scalar("SELECT hex(rt1_recurrenceRule) FROM TMTask WHERE uuid=?", result.projectId))
        assertEquals(tag, scalar("SELECT tags FROM TMTaskTag WHERE tasks=?", result.projectId))
        assertEquals("3", scalar("SELECT status FROM TMTask WHERE uuid=?", result.checklistTaskIds.getValue(c)))
        assertEquals(t, scalar("SELECT task FROM TMChecklistItem WHERE uuid=?", c))
        assertEquals("1", scalar("SELECT trashed FROM TMTask WHERE uuid=?", t))
    }
    @Test fun conversionFailureRollsBackAllRows() = runBlocking {
        val t = item(0); checklist(t, status=99)
        val before = scalar("SELECT COUNT(*) FROM TMTask")
        var failed = false
        try { ops.convertTaskToProject(t) } catch (_: IllegalArgumentException) { failed = true }
        assertTrue(failed)
        assertEquals(before, scalar("SELECT COUNT(*) FROM TMTask"))
        assertEquals("0", scalar("SELECT trashed FROM TMTask WHERE uuid=?",t))
    }
    @Test fun headingConversionKeepsChildIdAndCompletion() = runBlocking {
        val p = item(1); val h = item(2,project=p); val child = item(0,project=p,heading=h,status=3)
        val newProject = ops.convertHeadingToProject(h)
        assertEquals(newProject, scalar("SELECT project FROM TMTask WHERE uuid=?",child))
        assertNull(scalar("SELECT heading FROM TMTask WHERE uuid=?",child))
        assertEquals("3",scalar("SELECT status FROM TMTask WHERE uuid=?",child))
        assertEquals("1",scalar("SELECT trashed FROM TMTask WHERE uuid=?",h))
    }
    @Test fun repeatedProjectCompletionPreservesOriginalStopDate() = runBlocking {
        val p = item(1,status=3)
        ops.setProjectCompleted(p,true,ProjectCompletionPolicy.KeepTaskStates)
        assertEquals("123.0",scalar("SELECT stopDate FROM TMTask WHERE uuid=?",p))
    }
    @Test fun areaDeletionReceiptRestoresOnlyItsOwnChanges() = runBlocking {
        val a = area(); val p = item(1,area=a); val t = item(0,project=p)
        val oldTrash = item(0,project=p)
        db.execSQL("UPDATE TMTask SET trashed=1 WHERE uuid=?", arrayOf(oldTrash))
        val receipt = ops.deleteArea(a,AreaContentsDisposition.TrashContents)
        assertEquals(setOf(p,t), receipt.trashedTaskIds)
        assertEquals("0",scalar("SELECT COUNT(*) FROM TMArea WHERE uuid=?",a))
        ops.restoreArea(receipt)
        assertEquals(a,scalar("SELECT area FROM TMTask WHERE uuid=?",p))
        assertEquals("0",scalar("SELECT trashed FROM TMTask WHERE uuid=?",t))
        assertEquals("1",scalar("SELECT trashed FROM TMTask WHERE uuid=?",oldTrash))
    }
    @Test fun areaRestoreRefusesSubsequentEdits() = runBlocking {
        val a=area(); val p=item(1,area=a)
        val receipt=ops.deleteArea(a,AreaContentsDisposition.KeepContents)
        db.execSQL("UPDATE TMTask SET title='Edited after deletion' WHERE uuid=?",arrayOf(p))
        var failed=false
        try { ops.restoreArea(receipt) } catch (_: IllegalStateException) { failed=true }
        assertTrue(failed)
        assertEquals("0",scalar("SELECT COUNT(*) FROM TMArea WHERE uuid=?",a))
        assertEquals("Edited after deletion",scalar("SELECT title FROM TMTask WHERE uuid=?",p))
    }
    @Test fun tagDeletePromotesChildrenAndRemovesBothLinkKinds() = runBlocking {
        val parent=tag(); val child=tag(); val t=item(0); val a=area()
        db.execSQL("UPDATE TMTag SET parent=? WHERE uuid=?",arrayOf(parent,child))
        db.execSQL("INSERT INTO TMTaskTag(tasks,tags) VALUES(?,?)",arrayOf(t,parent))
        db.execSQL("INSERT INTO TMAreaTag(areas,tags) VALUES(?,?)",arrayOf(a,parent))
        ops.deleteTag(parent)
        assertNull(scalar("SELECT parent FROM TMTag WHERE uuid=?",child))
        assertEquals("0",scalar("SELECT COUNT(*) FROM TMTaskTag WHERE tags=?",parent))
        assertEquals("0",scalar("SELECT COUNT(*) FROM TMAreaTag WHERE tags=?",parent))
    }
    @Test fun incompleteReorderIsRejectedWithoutChanges() = runBlocking {
        val a=area(); val b=area()
        var failed=false
        try { ops.reorderAreas(listOf(a)) } catch (_: IllegalArgumentException) { failed=true }
        assertTrue(failed)
        assertEquals("0",scalar("SELECT \"index\" FROM TMArea WHERE uuid=?",b))
    }
}
