package com.things.app.data.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteCursor
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AreaContentsDisposition { KeepContents, TrashContents }
enum class ProjectCompletionPolicy { KeepTaskStates, CompleteOpenTasks }
data class DuplicateResult(val rootId: String, val copiedTaskIds: Map<String, String>)
data class TaskConversionResult(val projectId: String, val checklistTaskIds: Map<String, String>)
/** Retain this receipt to restore the exact deletion; it is not persisted automatically. */
data class AreaDeletionResult(
    val trashedTaskIds: Set<String>,
    val area: DbRecord,
    val areaTags: List<DbRecord>,
    val beforeTasks: Map<String, DbRecord>,
    val afterTasks: Map<String, DbRecord>,
)

/**
 * Local, transactional entity operations. No recurrence generation or cloud logic.
 * Only explicit columns are updated; row copies preserve opaque column values.
 * Area deletion is physical because the reference schema has no area trash column.
 */
class ThingsEntityOperations(
    private val database: () -> SQLiteDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(helper: ThingsDb, clock: Clock = Clock.systemDefaultZone(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO) : this({ helper.writableDatabase }, clock, dispatcher)

    /** Copies the non-trashed subtree, its checklists and tags. Completion states are preserved. */
    suspend fun duplicate(id: String): DuplicateResult = transaction { db ->
        val root = task(db, id)
        val graph = subtree(db, root)
        val siblings = siblings(db, root).map { it.requiredText("uuid") }
        val mapping = graph.associate { it.requiredText("uuid") to LocalDbIds.next() }
        val now = now()
        graph.forEach { original ->
            val oldId = original.requiredText("uuid")
            val newId = mapping.getValue(oldId)
            val values = original.toValues().apply {
                put("uuid", newId); put("creationDate", now); put("userModificationDate", now)
                put("leavesTombstone", 0)
                listOf("project", "heading", "rt1_repeatingTemplate").forEach { column ->
                    original.text(column)?.let { old -> mapping[old]?.let { put(column, it) } }
                }
            }
            db.insertOrThrow("TMTask", null, values)
            rows(db, "SELECT * FROM TMChecklistItem WHERE task = ? ORDER BY \"index\", uuid", arrayOf(oldId)).forEach { item ->
                val copy = item.toValues().apply {
                    put("uuid", LocalDbIds.next()); put("task", newId)
                    put("creationDate", now); put("userModificationDate", now); put("leavesTombstone", 0)
                }
                db.insertOrThrow("TMChecklistItem", null, copy)
            }
            copyTags(db, oldId, newId)
        }
        val rootCopy = mapping.getValue(id)
        val at = siblings.indexOf(id)
        check(at >= 0)
        assignOrder(db, "TMTask", siblings.toMutableList().apply { add(at + 1, rootCopy) })
        DuplicateResult(rootCopy, mapping)
    }

    /** Creates a new project, preserves source details and soft-trashes the original last. */
    suspend fun convertTaskToProject(id: String): TaskConversionResult = transaction { db ->
        val original = task(db, id, DbTaskType.Todo)
        val area = inheritedArea(db, original)
        val projectId = LocalDbIds.next()
        val now = now()
        val values = original.toValues().apply {
            put("uuid", projectId); put("type", DbTaskType.Project.raw)
            put("title", original.text("title")?.takeIf { it.isNotBlank() } ?: "New Project")
            put("status", 0); putNull("stopDate"); put("trashed", 0)
            put("area", area); putNull("project"); putNull("heading")
            put("creationDate", now); put("userModificationDate", now); put("leavesTombstone", 0)
            put("index", nextProjectPosition(db, area))
            put("checklistItemsCount", 0); put("openChecklistItemsCount", 0)
        }
        db.insertOrThrow("TMTask", null, values)
        copyTags(db, id, projectId)
        val mapping = linkedMapOf<String, String>()
        val checklist = rows(db, "SELECT * FROM TMChecklistItem WHERE task = ? ORDER BY \"index\", uuid", arrayOf(id))
        checklist.forEachIndexed { index, item ->
            val childId = LocalDbIds.next()
            val status = item.integer("status")
            require(status == 0L || status == 3L) { "Unknown checklist status" }
            db.insertOrThrow("TMTask", null, ContentValues().apply {
                put("uuid", childId); put("type", 0); put("status", status)
                put("trashed", 0); put("title", item.text("title")); put("project", projectId)
                put("start", 1); put("startBucket", 0); put("notesSync", 1); put("leavesTombstone", 0)
                put("creationDate", now); put("userModificationDate", now); put("index", index.toLong() * 1024L)
                if (status == 3L) put("stopDate", item.real("stopDate")) else putNull("stopDate")
                put("checklistItemsCount", 0); put("openChecklistItemsCount", 0)
            })
            mapping[item.requiredText("uuid")] = childId
        }
        // Original checklist rows remain attached to the trashed source for data preservation.
        markTrashed(db, setOf(id))
        TaskConversionResult(projectId, mapping)
    }

    /** Keeps child IDs and details; moves all non-trashed heading children into the new project. */
    suspend fun convertHeadingToProject(id: String): String = transaction { db ->
        val heading = task(db, id, DbTaskType.Heading)
        val children = subtree(db, heading).filter { it.requiredText("uuid") != id }
        val area = inheritedArea(db, heading)
        val projectId = LocalDbIds.next()
        val now = now()
        db.insertOrThrow("TMTask", null, heading.toValues().apply {
            put("uuid", projectId); put("type", 1); put("status", 0); putNull("stopDate")
            put("title", heading.text("title")?.takeIf { it.isNotBlank() } ?: "New Project")
            put("area", area); putNull("project"); putNull("heading")
            put("index", nextProjectPosition(db, area)); put("leavesTombstone", 0)
            put("creationDate", now); put("userModificationDate", now)
        })
        copyTags(db, id, projectId)
        children.forEach { child ->
            updateTask(db, child.requiredText("uuid"), ContentValues().apply {
                put("project", projectId); putNull("heading"); putNull("area"); put("userModificationDate", now)
            })
        }
        markTrashed(db, setOf(id))
        projectId
    }

    /** Child handling is explicit; reopening never silently reopens completed children. */
    suspend fun setProjectCompleted(id: String, completed: Boolean, childPolicy: ProjectCompletionPolicy) = transaction { db ->
        val root = task(db, id, DbTaskType.Project)
        require(completed || childPolicy == ProjectCompletionPolicy.KeepTaskStates)
        val targets = if (completed && childPolicy == ProjectCompletionPolicy.CompleteOpenTasks) {
            subtree(db, root).filter { record ->
                record.requiredText("uuid") == id ||
                    (record.integer("type") == 0L && record.integer("status") == 0L &&
                        record.blob("rt1_recurrenceRule")?.isNotEmpty() != true && record.blob("repeater")?.isNotEmpty() != true)
            }
        } else listOf(root)
        val target = if (completed) 3L else 0L
        val now = now()
        targets.forEach { record ->
            val previous = record.integer("status")
            require(previous == 0L || previous == 3L) { "Unknown status" }
            if (previous != target) updateTask(db, record.requiredText("uuid"), ContentValues().apply {
                put("status", target); put("userModificationDate", now)
                if (completed) put("stopDate", now) else putNull("stopDate")
            })
        }
    }

    /** Physically deletes the area and its tag links; task rows themselves are never physically deleted. */
    suspend fun deleteArea(areaId: String, disposition: AreaContentsDisposition): AreaDeletionResult = transaction { db ->
        val area = requireOne(db, "TMArea", areaId)
        val areaTags = rows(db, "SELECT * FROM TMAreaTag WHERE areas = ?", arrayOf(areaId))
        val roots = rows(db, "SELECT * FROM TMTask WHERE area = ? AND trashed = 0", arrayOf(areaId))
        require(roots.all { it.integer("type") == 0L || it.integer("type") == 1L }) { "Invalid direct area child" }
        val affected = linkedSetOf<String>()
        if (disposition == AreaContentsDisposition.TrashContents) roots.forEach { root ->
            subtree(db, root).forEach { affected += it.requiredText("uuid") }
        }
        val direct = rows(db, "SELECT * FROM TMTask WHERE area = ?", arrayOf(areaId))
        val touched = affected + direct.map { it.requiredText("uuid") }
        val before = touched.associateWith { task(db, it, allowTrashed = true) }
        markTrashed(db, affected)
        // Detach also previously trashed direct children so later restore has no dangling area.
        db.update("TMTask", ContentValues().apply { putNull("area"); put("userModificationDate", now()) }, "area = ?", arrayOf(areaId))
        db.delete("TMAreaTag", "areas = ?", arrayOf(areaId))
        check(db.delete("TMArea", "uuid = ?", arrayOf(areaId)) == 1)
        val after = touched.associateWith { task(db, it, allowTrashed = true) }
        AreaDeletionResult(affected, area, areaTags, before, after)
    }

    /** Exact receipt-based undo; refuses to overwrite any task edited after deletion. */
    suspend fun restoreArea(receipt: AreaDeletionResult) = transaction { db ->
        val areaId = receipt.area.requiredText("uuid")
        require(rows(db, "SELECT * FROM TMArea WHERE uuid = ?", arrayOf(areaId)).isEmpty()) { "Area already exists" }
        require(rows(db, "SELECT * FROM TMAreaTag WHERE areas = ?", arrayOf(areaId)).isEmpty()) { "Unexpected area tag links" }
        require(receipt.beforeTasks.keys == receipt.afterTasks.keys)
        receipt.afterTasks.forEach { (id, expected) ->
            check(task(db, id, allowTrashed = true).columns == expected.columns) { "Task changed after deletion: $id" }
        }
        receipt.areaTags.forEach { link ->
            require(link.requiredText("areas") == areaId)
            requireOne(db, "TMTag", link.requiredText("tags"))
        }
        db.insertOrThrow("TMArea", null, receipt.area.toValues())
        receipt.areaTags.forEach { db.insertOrThrow("TMAreaTag", null, it.toValues()) }
        receipt.beforeTasks.forEach { (id, before) ->
            updateTask(db, id, ContentValues().apply {
                put("area", before.text("area")); put("trashed", before.integer("trashed"))
                put("userModificationDate", now())
            })
        }
    }

    /** Restores only the explicit IDs, never all descendants that may have been trashed earlier. */
    suspend fun restoreTasks(ids: Set<String>) = transaction { db ->
        val records = ids.associateWith { task(db, it, allowTrashed = true) }
        records.values.forEach { record ->
            val id = record.requiredText("uuid")
            listOf("project" to DbTaskType.Project, "heading" to DbTaskType.Heading).forEach { (column, expected) ->
                record.text(column)?.let { parentId ->
                    val parent = records[parentId] ?: task(db, parentId)
                    require(parent.integer("type") == expected.raw && (parentId in ids || parent.integer("trashed") == 0L))
                }
            }
            record.text("heading")?.let { headingId ->
                val heading = records[headingId] ?: task(db, headingId)
                val projectId = requireNotNull(heading.text("project")) { "Heading has no project" }
                require(record.text("project") == null || record.text("project") == projectId) { "Conflicting project ownership" }
                val project = records[projectId] ?: task(db, projectId, DbTaskType.Project)
                require(project.integer("type") == 1L && (projectId in ids || project.integer("trashed") == 0L))
            }
            record.text("area")?.let { requireOne(db, "TMArea", it) }
            require(record.integer("trashed") == 0L || record.integer("trashed") == 1L) { "Unknown trash state: $id" }
        }
        records.forEach { (id, record) ->
            if (record.integer("trashed") == 1L) updateTask(db, id, ContentValues().apply { put("trashed", 0); put("userModificationDate", now()) })
        }
    }

    suspend fun renameTag(id: String, title: String) = transaction { db ->
        requireOne(db, "TMTag", id)
        check(db.update("TMTag", ContentValues().apply { put("title", title) }, "uuid = ?", arrayOf(id)) == 1)
    }
    /** Matches the current UI's one-level nesting; cycles and nesting a parent tag are rejected. */
    suspend fun moveTag(id: String, parentId: String?) = transaction { db ->
        requireOne(db, "TMTag", id)
        require(parentId != id)
        if (parentId != null) {
            val parent = requireOne(db, "TMTag", parentId)
            require(parent.text("parent") == null) { "Only one nesting level is supported" }
            require(rows(db, "SELECT * FROM TMTag WHERE parent = ?", arrayOf(id)).isEmpty()) { "A parent tag cannot be nested" }
        }
        val last = rows(db, "SELECT * FROM TMTag WHERE parent IS ? ORDER BY \"index\", uuid", arrayOfNullable(parentId))
            .filter { it.requiredText("uuid") != id }.lastOrNull()?.integer("index")
        check(db.update("TMTag", ContentValues().apply {
            put("parent", parentId); put("index", last?.let { Math.addExact(it, 1024L) } ?: 0L)
        }, "uuid = ?", arrayOf(id)) == 1)
    }
    suspend fun deleteTag(id: String) = transaction { db ->
        val tag = requireOne(db, "TMTag", id)
        val parent = tag.text("parent")
        parent?.let { requireOne(db, "TMTag", it) }
        val siblings = rows(db, "SELECT * FROM TMTag WHERE parent IS ? ORDER BY \"index\", uuid", arrayOfNullable(parent))
            .map { it.requiredText("uuid") }.filterNot { it == id }
        val promoted = rows(db, "SELECT * FROM TMTag WHERE parent = ? ORDER BY \"index\", uuid", arrayOf(id)).map { it.requiredText("uuid") }
        db.update("TMTag", ContentValues().apply { put("parent", parent) }, "parent = ?", arrayOf(id))
        assignOrder(db, "TMTag", siblings + promoted)
        db.delete("TMTaskTag", "tags = ?", arrayOf(id))
        db.delete("TMAreaTag", "tags = ?", arrayOf(id))
        check(db.delete("TMTag", "uuid = ?", arrayOf(id)) == 1)
    }
    suspend fun reorderAreas(orderedIds: List<String>) = transaction { db ->
        reorderExact(db, "TMArea", rows(db, "SELECT * FROM TMArea"), orderedIds)
    }
    suspend fun reorderProjects(areaId: String?, orderedIds: List<String>) = transaction { db ->
        areaId?.let { requireOne(db, "TMArea", it) }
        val existing = rows(db, "SELECT * FROM TMTask WHERE type = 1 AND trashed = 0 AND area IS ?", arrayOfNullable(areaId))
        reorderExact(db, "TMTask", existing, orderedIds)
    }
    suspend fun reorderHeadings(projectId: String, orderedIds: List<String>) = transaction { db ->
        task(db, projectId, DbTaskType.Project)
        reorderExact(db, "TMTask", rows(db, "SELECT * FROM TMTask WHERE type = 2 AND trashed = 0 AND project = ?", arrayOf(projectId)), orderedIds)
    }
    suspend fun reorderTags(parentId: String?, orderedIds: List<String>) = transaction { db ->
        parentId?.let { requireOne(db, "TMTag", it) }
        reorderExact(db, "TMTag", rows(db, "SELECT * FROM TMTag WHERE parent IS ?", arrayOfNullable(parentId)), orderedIds)
    }

    private suspend fun <T> transaction(action: (SQLiteDatabase) -> T): T = withContext(dispatcher) {
        val db = database()
        db.beginTransactionNonExclusive()
        try { val result = action(db); db.setTransactionSuccessful(); result } finally { db.endTransaction() }
    }
    private fun now(): Double { val i = clock.instant(); return i.epochSecond + i.nano / 1_000_000_000.0 }
    private fun task(db: SQLiteDatabase, id: String, type: DbTaskType? = null, allowTrashed: Boolean = false): DbRecord {
        val record = requireOne(db, "TMTask", id)
        require(record.integer("trashed") == 0L || (allowTrashed && record.integer("trashed") == 1L)) { "Task is trashed or has an unknown trash state" }
        require(record.integer("type") in setOf(0L, 1L, 2L)) { "Unknown entity type" }
        require(type == null || record.integer("type") == type.raw) { "Unexpected entity type" }
        return record
    }
    private fun requireOne(db: SQLiteDatabase, table: String, id: String): DbRecord {
        require(table in setOf("TMTask", "TMArea", "TMTag"))
        return rows(db, "SELECT * FROM $table WHERE uuid = ?", arrayOf(id)).singleOrNull() ?: error("Missing $table entity: $id")
    }
    private fun subtree(db: SQLiteDatabase, root: DbRecord): List<DbRecord> {
        val id = root.requiredText("uuid")
        val type = root.integer("type")
        val children = when (type) {
            0L -> emptyList()
            1L -> rows(db, "SELECT * FROM TMTask WHERE trashed = 0 AND (project = ? OR heading IN (SELECT uuid FROM TMTask WHERE type = 2 AND project = ?)) ORDER BY \"index\", uuid", arrayOf(id, id))
            2L -> rows(db, "SELECT * FROM TMTask WHERE trashed = 0 AND heading = ? ORDER BY \"index\", uuid", arrayOf(id))
            else -> error("Unknown entity type")
        }
        children.forEach { child ->
            require(child.integer("type") == 0L || (type == 1L && child.integer("type") == 2L)) { "Invalid subtree type" }
            val project = child.text("project")
            if (type == 1L) require(project == null || project == id) { "Conflicting project ownership" }
            if (type == 2L) require(project == null || project == root.text("project")) { "Conflicting heading ownership" }
        }
        children.forEach { child ->
            child.text("heading")?.let { headingId ->
                val heading = task(db, headingId, DbTaskType.Heading)
                val expectedProject = if (type == 1L) id else root.text("project")
                require(heading.text("project") == expectedProject) { "Child heading belongs to another project" }
                if (type == 2L) require(headingId == id) { "Child belongs to another heading" }
            }
        }
        return (listOf(root) + children).distinctBy { it.requiredText("uuid") }
    }
    private fun inheritedArea(db: SQLiteDatabase, record: DbRecord): String? {
        val direct = record.text("area")
        val headingProject = record.text("heading")?.let { task(db, it, DbTaskType.Heading).text("project") }
        val projectId = record.text("project") ?: headingProject
        require(headingProject == null || record.text("project") == null || headingProject == record.text("project"))
        val inherited = projectId?.let { task(db, it, DbTaskType.Project).text("area") }
        require(direct == null || inherited == null || direct == inherited) { "Conflicting area ownership" }
        return (inherited ?: direct)?.also { requireOne(db, "TMArea", it) }
    }
    private fun siblings(db: SQLiteDatabase, root: DbRecord): List<DbRecord> = rows(db,
        "SELECT * FROM TMTask WHERE type = ? AND trashed = 0 AND area IS ? AND project IS ? AND heading IS ? ORDER BY \"index\", uuid",
        arrayOf(root.integer("type").toString(), root.text("area"), root.text("project"), root.text("heading")))
    private fun nextProjectPosition(db: SQLiteDatabase, area: String?): Long {
        val last = rows(db, "SELECT * FROM TMTask WHERE type = 1 AND trashed = 0 AND area IS ? ORDER BY \"index\", uuid", arrayOfNullable(area)).lastOrNull()?.integer("index")
        return last?.let { Math.addExact(it, 1024L) } ?: 0L
    }
    private fun copyTags(db: SQLiteDatabase, from: String, to: String) {
        rows(db, "SELECT * FROM TMTaskTag WHERE tasks = ?", arrayOf(from)).forEach { link ->
            db.insertOrThrow("TMTaskTag", null, link.toValues().apply { put("tasks", to) })
        }
    }
    private fun reorderExact(db: SQLiteDatabase, table: String, existing: List<DbRecord>, orderedIds: List<String>) {
        require(orderedIds.distinct().size == orderedIds.size)
        require(existing.map { it.requiredText("uuid") }.toSet() == orderedIds.toSet()) { "Order must include the full container" }
        assignOrder(db, table, orderedIds)
    }
    private fun assignOrder(db: SQLiteDatabase, table: String, ids: List<String>) {
        require(table in setOf("TMTask", "TMArea", "TMTag"))
        ids.forEachIndexed { index, id ->
            val v = ContentValues().apply { put("index", index.toLong() * 1024L); if (table == "TMTask") put("userModificationDate", now()) }
            check(db.update(table, v, "uuid = ?", arrayOf(id)) == 1)
        }
    }
    private fun markTrashed(db: SQLiteDatabase, ids: Set<String>) {
        ids.forEach { id -> db.update("TMTask", ContentValues().apply { put("trashed", 1); put("userModificationDate", now()) }, "uuid = ? AND trashed = 0", arrayOf(id)) }
    }
    private fun updateTask(db: SQLiteDatabase, id: String, values: ContentValues) {
        check(db.update("TMTask", values, "uuid = ?", arrayOf(id)) == 1)
    }
    private fun arrayOfNullable(value: String?): Array<String?> = arrayOf(value)
    private fun rows(db: SQLiteDatabase, sql: String, args: Array<out String?> = emptyArray()): List<DbRecord> =
        db.rawQueryWithFactory({ _, driver, table, query ->
            args.forEachIndexed { index, value ->
                if (value == null) query.bindNull(index + 1) else query.bindString(index + 1, value)
            }
            SQLiteCursor(driver, table, query)
        }, sql, emptyArray(), null).use { c ->
        buildList {
            while (c.moveToNext()) {
                val fields = buildMap<String, DbValue> {
                    for (i in 0 until c.columnCount) put(c.getColumnName(i), when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> DbValue.Null
                        Cursor.FIELD_TYPE_INTEGER -> DbValue.Integer(c.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> DbValue.Real(c.getDouble(i))
                        Cursor.FIELD_TYPE_STRING -> DbValue.Text(c.getString(i))
                        Cursor.FIELD_TYPE_BLOB -> DbValue.Blob(c.getBlob(i))
                        else -> error("Unsupported SQLite value")
                    })
                }
                add(DbRecord(fields))
            }
        }
    }
}

private fun DbRecord.toValues(): ContentValues = ContentValues().also { out ->
    columns.forEach { (name, value) -> when (value) {
        DbValue.Null -> out.putNull(name)
        is DbValue.Integer -> out.put(name, value.value)
        is DbValue.Real -> out.put(name, value.value)
        is DbValue.Text -> out.put(name, value.value)
        is DbValue.Blob -> out.put(name, value.bytes())
    } }
}
