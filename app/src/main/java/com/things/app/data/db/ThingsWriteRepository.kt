package com.things.app.data.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local writes only. The caller owns the helper. */
class ThingsWriteRepository(
    private val helper: ThingsDb,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun createTask(input: NewTask): String = write { db ->
        require(input.projectId == null || input.areaId == null)
        input.areaId?.let { requireEntity(db, "TMArea", it) }
        input.projectId?.let { requireTask(db, it, DbTaskType.Project) }
        input.headingId?.let {
            require(input.projectId != null)
            require(requireTask(db, it, DbTaskType.Heading).project == input.projectId)
        }
        insertTask(db, DbTaskType.Todo, input.title, input.notes, input.position, input.projectId, input.headingId, input.areaId)
    }
    suspend fun createProject(input: NewProject): String = write { db ->
        input.areaId?.let { requireEntity(db, "TMArea", it) }
        insertTask(db, DbTaskType.Project, input.title, input.notes, input.position, area = input.areaId)
    }
    suspend fun createHeading(input: NewHeading): String = write { db ->
        requireTask(db, input.projectId, DbTaskType.Project)
        insertTask(db, DbTaskType.Heading, input.title, null, input.position, project = input.projectId)
    }
    suspend fun createArea(title: String, position: Long = 0): String = write { db ->
        val id = LocalDbIds.next()
        db.insertOrThrow("TMArea", null, ContentValues().apply {
            put("uuid", id); put("title", title); put("visible", 1); put("index", position)
        })
        id
    }
    suspend fun renameArea(areaId: String, title: String) = write { db ->
        requireEntity(db, "TMArea", areaId)
        check(db.update("TMArea", ContentValues().apply { put("title", title) }, "uuid = ?", arrayOf(areaId)) == 1)
    }
    suspend fun patchTask(id: String, patch: TaskPatch) = write { db ->
        requireTask(db, id)
        val v = ContentValues()
        v.applyChange("title", patch.title) { it }
        v.applyChange("notes", patch.notes) { it }
        v.applyChange("start", patch.start) { it }
        v.applyChange("startBucket", patch.startBucket) { it }
        v.applyChange("startDate", patch.startDate) { ThingsDate.packDate(it) }
        v.applyChange("deadline", patch.deadline) { ThingsDate.packDate(it) }
        v.applyChange("reminderTime", patch.reminderTime) { ThingsDate.packTime(it) }
        if (v.size() > 0) { v.put("userModificationDate", timestamp()); update(db, id, v) }
    }
    suspend fun setTaskCompleted(id: String, completed: Boolean) = write { db ->
        val current = requireTask(db, id, DbTaskType.Todo)
        require(current.status == 0L || current.status == 3L) { "Unknown task status" }
        val target = if (completed) 3L else 0L
        if (current.status != target) {
            val now = timestamp()
            update(db, id, ContentValues().apply {
                put("status", target)
                if (completed) put("stopDate", now) else putNull("stopDate")
                put("userModificationDate", now)
            })
        }
    }
    suspend fun trashTaskOrProject(id: String) = write { db ->
        val root = requireTask(db, id)
        require(root.type != DbTaskType.Heading.raw) { "Use deleteHeading" }
        val targets = if (root.type == DbTaskType.Project.raw) {
            ids(db, "SELECT uuid FROM TMTask WHERE project = ? OR heading IN (SELECT uuid FROM TMTask WHERE type = 2 AND project = ?)", arrayOf(id, id)).toSet() + id
        } else setOf(id)
        trash(db, targets)
    }

    /** The caller supplies the complete currently displayed active subtree, in display order. */
    suspend fun deleteHeading(headingId: String, mode: HeadingDeleteMode, displayedTaskIds: List<String>) = write { db ->
        val project = requireNotNull(requireTask(db, headingId, DbTaskType.Heading).project)
        requireTask(db, project, DbTaskType.Project)
        require(displayedTaskIds.distinct().size == displayedTaskIds.size)
        val all = ids(db, "SELECT uuid FROM TMTask WHERE type = 0 AND trashed = 0 AND heading = ? ORDER BY \"index\", uuid", arrayOf(headingId))
        val day = ThingsDate.packDate(LocalDate.now(clock))
        val active = ids(db,
            "SELECT uuid FROM TMTask WHERE type = 0 AND trashed = 0 AND status = 0 AND heading = ? " +
                "AND (startDate IS NULL OR startDate = 0 OR startDate <= ?) " +
                "AND (rt1_recurrenceRule IS NULL OR length(rt1_recurrenceRule) = 0) " +
                "AND (repeater IS NULL OR length(repeater) = 0)", arrayOf(headingId, day.toString()))
        require(active.toSet() == displayedTaskIds.toSet()) { "Heading changed; reload before deleting" }
        all.forEach {
            val owner = requireTask(db, it, DbTaskType.Todo).project
            require(owner == null || owner == project) { "Conflicting project reference" }
        }
        val selected = displayedTaskIds.toSet()
        val retained = if (mode == HeadingDeleteMode.KeepTasks) all else all.filterNot { it in selected }
        retained.forEach { id ->
            update(db, id, ContentValues().apply {
                put("project", project); putNull("heading"); put("userModificationDate", timestamp())
            })
        }
        if (mode == HeadingDeleteMode.KeepTasks) {
            val loose = ids(db, "SELECT uuid FROM TMTask WHERE type = 0 AND trashed = 0 AND project = ? AND heading IS NULL ORDER BY \"index\", uuid", arrayOf(project)).filterNot { it in selected }
            order(db, displayedTaskIds + loose)
        } else trash(db, selected)
        trash(db, setOf(headingId))
    }

    /** Full container order, not a filtered/visible subset. Area scopes direct tasks only. */
    suspend fun reorderTasks(projectId: String?, headingId: String?, orderedIds: List<String>, areaId: String? = null) = write { db ->
        require(orderedIds.distinct().size == orderedIds.size)
        require(areaId == null || (projectId == null && headingId == null))
        areaId?.let { requireEntity(db, "TMArea", it) }
        projectId?.let { requireTask(db, it, DbTaskType.Project) }
        val condition: String
        val args: Array<String>
        if (headingId != null) {
            require(requireTask(db, headingId, DbTaskType.Heading).project == projectId)
            condition = "heading = ?"; args = arrayOf(headingId)
        } else if (projectId != null) {
            condition = "heading IS NULL AND project = ?"; args = arrayOf(projectId)
        } else if (areaId != null) {
            condition = "heading IS NULL AND project IS NULL AND area = ?"; args = arrayOf(areaId)
        } else {
            condition = "heading IS NULL AND project IS NULL AND area IS NULL"; args = emptyArray()
        }
        val existing = ids(db, "SELECT uuid FROM TMTask WHERE type = 0 AND trashed = 0 AND $condition", args)
        require(existing.toSet() == orderedIds.toSet()) { "Order must contain every task in the container" }
        order(db, orderedIds)
    }

    /** Expected rows must come from the snapshot being edited; stale replacement is rejected. */
    suspend fun saveChecklist(taskId: String, items: List<ChecklistWrite>, expectedChecklist: List<ChecklistRecord>): List<String> = write { db ->
        requireTask(db, taskId, DbTaskType.Todo)
        require(expectedChecklist.all { it.taskId == taskId })
        require(expectedChecklist.map { it.id }.distinct().size == expectedChecklist.size)
        val current = checklistState(db, taskId)
        val expected = expectedChecklist.associate { it.id to it.row.columns }
        check(current == expected) { "Checklist changed; reload before saving" }
        val supplied = items.mapNotNull { it.id }
        require(supplied.distinct().size == supplied.size)
        require(supplied.all { it in current }) { "Checklist item belongs to another task" }
        val positions = localOrderPositions(items.size)
        val saved = items.mapIndexed { i, item ->
            val id = item.id ?: LocalDbIds.next()
            val previous = (current[id]?.get("status") as? DbValue.Integer)?.value
            val values = ContentValues().apply {
                put("title", item.title); put("status", if (item.completed) 3 else 0)
                put("index", positions[i]); put("userModificationDate", timestamp())
                if (!item.completed) putNull("stopDate")
                else if (item.id == null || previous != 3L) put("stopDate", timestamp())
            }
            if (item.id == null) {
                values.put("uuid", id); values.put("task", taskId); values.put("creationDate", timestamp())
                db.insertOrThrow("TMChecklistItem", null, values)
            } else check(db.update("TMChecklistItem", values, "uuid = ? AND task = ?", arrayOf(id, taskId)) == 1)
            id
        }
        val retained = saved.toSet()
        current.keys.filterNot { it in retained }.forEach {
            check(db.delete("TMChecklistItem", "uuid = ? AND task = ?", arrayOf(it, taskId)) == 1)
        }
        db.execSQL("UPDATE TMTask SET checklistItemsCount = (SELECT COUNT(*) FROM TMChecklistItem WHERE task = ?), openChecklistItemsCount = (SELECT COUNT(*) FROM TMChecklistItem WHERE task = ? AND status = 0) WHERE uuid = ?", arrayOf(taskId, taskId, taskId))
        touch(db, taskId)
        saved
    }
    suspend fun createTag(title: String, parentId: String? = null, position: Long = 0): String = write { db ->
        parentId?.let { requireEntity(db, "TMTag", it) }
        val id = LocalDbIds.next()
        db.insertOrThrow("TMTag", null, ContentValues().apply {
            put("uuid", id); put("title", title); put("parent", parentId); put("index", position)
        })
        id
    }
    suspend fun setTaskTags(taskId: String, tagIds: List<String>) = write { db ->
        requireTask(db, taskId)
        replaceTags(db, "TMTaskTag", "tasks", taskId, tagIds)
        touch(db, taskId)
    }
    suspend fun setAreaTags(areaId: String, tagIds: List<String>) = write { db ->
        requireEntity(db, "TMArea", areaId)
        replaceTags(db, "TMAreaTag", "areas", areaId, tagIds)
    }
    private fun replaceTags(db: SQLiteDatabase, table: String, ownerColumn: String, owner: String, tags: List<String>) {
        val unique = tags.distinct()
        unique.forEach { requireEntity(db, "TMTag", it) }
        db.delete(table, "$ownerColumn = ?", arrayOf(owner))
        unique.forEach { tag -> db.insertOrThrow(table, null, ContentValues().apply { put(ownerColumn, owner); put("tags", tag) }) }
    }
    private suspend fun <T> write(action: (SQLiteDatabase) -> T): T = withContext(dispatcher) {
        val db = helper.writableDatabase
        db.beginTransactionNonExclusive()
        try { val result = action(db); db.setTransactionSuccessful(); result } finally { db.endTransaction() }
    }
    private fun timestamp(): Double {
        val i = clock.instant()
        return i.epochSecond + i.nano / 1_000_000_000.0
    }
    private data class Identity(val type: Long?, val project: String?, val status: Long?)
    private fun requireTask(db: SQLiteDatabase, id: String, type: DbTaskType? = null): Identity {
        val result = db.rawQuery("SELECT type, project, status FROM TMTask WHERE uuid = ? AND trashed = 0", arrayOf(id)).use { c ->
            require(c.moveToFirst()) { "Missing or trashed task: $id" }
            Identity(if (c.isNull(0)) null else c.getLong(0), if (c.isNull(1)) null else c.getString(1), if (c.isNull(2)) null else c.getLong(2))
        }
        require(type == null || result.type == type.raw) { "Unexpected task type" }
        return result
    }
    private fun requireEntity(db: SQLiteDatabase, table: String, id: String) {
        require(table == "TMArea" || table == "TMTag")
        db.rawQuery("SELECT uuid FROM $table WHERE uuid = ?", arrayOf(id)).use { require(it.moveToFirst()) { "Missing entity: $id" } }
    }
    private fun insertTask(db: SQLiteDatabase, type: DbTaskType, title: String, notes: String?, position: Long, project: String? = null, heading: String? = null, area: String? = null): String {
        val id = LocalDbIds.next()
        val now = timestamp()
        db.insertOrThrow("TMTask", null, ContentValues().apply {
            put("uuid", id); put("type", type.raw); put("status", 0); put("trashed", 0)
            put("title", title); put("notes", notes); put("notesSync", 1)
            put("creationDate", now); put("userModificationDate", now); put("index", position)
            put("start", 1); put("startBucket", 0); put("leavesTombstone", 0)
            putNull("startDate"); putNull("reminderTime"); putNull("deadline"); putNull("stopDate")
            put("project", project); put("heading", heading); put("area", area)
            if (type == DbTaskType.Todo) { put("checklistItemsCount", 0); put("openChecklistItemsCount", 0) }
        })
        return id
    }
    private fun update(db: SQLiteDatabase, id: String, values: ContentValues) {
        check(db.update("TMTask", values, "uuid = ?", arrayOf(id)) == 1)
    }
    private fun touch(db: SQLiteDatabase, id: String) = update(db, id, ContentValues().apply { put("userModificationDate", timestamp()) })
    private fun ids(db: SQLiteDatabase, sql: String, args: Array<String>): List<String> = db.rawQuery(sql, args).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
    private fun order(db: SQLiteDatabase, ids: List<String>) {
        val positions = localOrderPositions(ids.size)
        ids.forEachIndexed { i, id -> update(db, id, ContentValues().apply { put("index", positions[i]); put("userModificationDate", timestamp()) }) }
    }
    private fun trash(db: SQLiteDatabase, ids: Set<String>) {
        ids.forEach { id -> db.update("TMTask", ContentValues().apply { put("trashed", 1); put("userModificationDate", timestamp()) }, "uuid = ? AND trashed = 0", arrayOf(id)) }
    }
    private fun checklistState(db: SQLiteDatabase, task: String): Map<String, Map<String, DbValue>> =
        db.rawQuery("SELECT * FROM TMChecklistItem WHERE task = ?", arrayOf(task)).use { c ->
            buildMap {
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
                    put((fields.getValue("uuid") as DbValue.Text).value, fields)
                }
            }
        }
}

private fun <T, R : Any> ContentValues.applyChange(column: String, change: FieldChange<T>, encode: (T) -> R) {
    when (change) {
        FieldChange.Keep -> Unit
        is FieldChange.Set -> {
            val source = change.value
            if (source == null) putNull(column) else when (val value = encode(source)) {
                is String -> put(column, value)
                is Int -> put(column, value)
                is Long -> put(column, value)
                else -> error("Unsupported encoded type for $column")
            }
        }
    }
}
