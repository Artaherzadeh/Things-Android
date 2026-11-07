package com.things.app.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local persistence. The caller owns the helper. Unknown columns and blobs are not rewritten. */
class ThingsWriteRepository(
    private val helper: ThingsDb,
    private val clock: Clock = Clock.systemUTC(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun createTask(input: NewTask): String = write { db ->
        validateTaskLocation(db, input.projectId, input.headingId, input.areaId)
        insertTask(db, DbTaskType.Todo, input.title, input.notes, input.position,
            input.projectId, input.headingId, input.areaId)
    }
    suspend fun createProject(input: NewProject): String = write { db ->
        input.areaId?.let { requireArea(db, it) }
        insertTask(db, DbTaskType.Project, input.title, input.notes, input.position, areaId = input.areaId)
    }
    suspend fun createHeading(input: NewHeading): String = write { db ->
        requireTask(db, input.projectId, DbTaskType.Project)
        insertTask(db, DbTaskType.Heading, input.title, null, input.position, projectId = input.projectId)
    }
    suspend fun createArea(title: String, position: Long = 0): String = write { db ->
        val id = LocalDbIds.next()
        db.insertOrThrow("TMArea", null, ContentValues().apply {
            put("uuid", id)
            put("title", title)
            put("visible", 1)
            put("index", position)
        })
        id
    }
    suspend fun renameArea(areaId: String, title: String) = write { db ->
        requireArea(db, areaId)
        check(db.update("TMArea", ContentValues().apply { put("title", title) },
            "uuid = ?", arrayOf(areaId)) == 1)
    }
    suspend fun patchTask(id: String, patch: TaskPatch) = write { db ->
        requireTask(db, id)
        val values = ContentValues()
        values.applyChange("title", patch.title) { it }
        values.applyChange("notes", patch.notes) { it }
        values.applyChange("start", patch.start) { it }
        values.applyChange("startBucket", patch.startBucket) { it }
        values.applyChange("startDate", patch.startDate) { ThingsDate.packDate(it) }
        values.applyChange("reminderTime", patch.reminderTime) { ThingsDate.packTime(it) }
        values.applyChange("deadline", patch.deadline) { ThingsDate.packDate(it) }
        if (values.size() > 0) {
            values.put("userModificationDate", timestamp())
            updateTask(db, id, values)
        }
    }

    /** Completes a single to-do; does not generate recurring instances. */
    suspend fun setTaskCompleted(id: String, completed: Boolean) = write { db ->
        requireTask(db, id, DbTaskType.Todo)
        updateTask(db, id, ContentValues().apply {
            put("status", if (completed) 3 else 0)
            if (completed) put("stopDate", timestamp()) else putNull("stopDate")
            put("userModificationDate", timestamp())
        })
    }

    /** Soft trash only. A project includes its subtree. Headings require an explicit choice. */
    suspend fun trashTaskOrProject(id: String) = write { db ->
        val root = requireTask(db, id)
        require(root.type != DbTaskType.Heading.raw) { "Use deleteHeading for headings" }
        val targets = if (root.type == DbTaskType.Project.raw) projectSubtree(db, id) else setOf(id)
        trash(db, targets)
    }

    /** Moves original task rows without recreating IDs or replacing their details. */
    suspend fun deleteHeading(headingId: String, mode: HeadingDeleteMode) = write { db ->
        val heading = requireTask(db, headingId, DbTaskType.Heading)
        val projectId = requireNotNull(heading.projectId) { "Heading has no project" }
        requireTask(db, projectId, DbTaskType.Project)
        val movedIds = ids(db,
            "SELECT uuid FROM TMTask WHERE type = 0 AND trashed = 0 AND heading = ? ORDER BY \"index\", uuid",
            arrayOf(headingId))
        when (mode) {
            HeadingDeleteMode.KeepTasks -> {
                val looseIds = ids(db,
                    "SELECT uuid FROM TMTask WHERE type = 0 AND trashed = 0 AND project = ? AND heading IS NULL ORDER BY \"index\", uuid",
                    arrayOf(projectId))
                movedIds.forEach { taskId ->
                    updateTask(db, taskId, ContentValues().apply {
                        put("project", projectId)
                        putNull("heading")
                        put("userModificationDate", timestamp())
                    })
                }
                applyOrder(db, movedIds + looseIds)
            }
            HeadingDeleteMode.TrashTasks -> trash(db, movedIds.toSet())
        }
        trash(db, setOf(headingId))
    }

    /** Requires every non-trashed task in this container exactly once, including hidden rows. */
    suspend fun reorderTasks(projectId: String?, headingId: String?, orderedIds: List<String>) = write { db ->
        require(orderedIds.distinct().size == orderedIds.size) { "Duplicate IDs in order" }
        val existing = if (headingId != null) {
            val heading = requireTask(db, headingId, DbTaskType.Heading)
            require(projectId == heading.projectId) { "Heading belongs to another project" }
            ids(db, "SELECT uuid FROM TMTask WHERE type = 0 AND trashed = 0 AND heading = ? ORDER BY \"index\", uuid",
                arrayOf(headingId))
        } else {
            projectId?.let { requireTask(db, it, DbTaskType.Project) }
            val condition = if (projectId == null) "project IS NULL" else "project = ?"
            ids(db, "SELECT uuid FROM TMTask WHERE type = 0 AND trashed = 0 AND heading IS NULL AND $condition ORDER BY \"index\", uuid",
                projectId?.let { arrayOf(it) } ?: emptyArray())
        }
        require(existing.toSet() == orderedIds.toSet()) { "Order must contain every task in this container" }
        applyOrder(db, orderedIds)
    }

    /** Local-only checklist replacement. Existing IDs retain all untouched columns. */
    suspend fun saveChecklist(taskId: String, items: List<ChecklistWrite>): List<String> = write { db ->
        requireTask(db, taskId, DbTaskType.Todo)
        val suppliedIds = items.mapNotNull { it.id }
        require(suppliedIds.distinct().size == suppliedIds.size) { "Duplicate checklist IDs" }
        val existing = linkedMapOf<String, Long?>()
        db.rawQuery("SELECT uuid, status FROM TMChecklistItem WHERE task = ?", arrayOf(taskId)).use { c ->
            while (c.moveToNext()) existing[c.getString(0)] = if (c.isNull(1)) null else c.getLong(1)
        }
        require(suppliedIds.all { it in existing }) { "Checklist item does not belong to this task" }
        val positions = localOrderPositions(items.size)
        val savedIds = items.mapIndexed { index, item ->
            val id = item.id ?: LocalDbIds.next()
            val targetStatus = if (item.completed) 3L else 0L
            val values = ContentValues().apply {
                put("title", item.title)
                put("status", targetStatus)
                put("index", positions[index])
                put("userModificationDate", timestamp())
            }
            val previousStatus = existing[id]
            if (!item.completed) values.putNull("stopDate")
            else if (item.id == null || previousStatus != 3L) values.put("stopDate", timestamp())
            if (item.id == null) {
                values.put("uuid", id)
                values.put("task", taskId)
                values.put("creationDate", timestamp())
                db.insertOrThrow("TMChecklistItem", null, values)
            } else {
                check(db.update("TMChecklistItem", values, "uuid = ? AND task = ?", arrayOf(id, taskId)) == 1)
            }
            id
        }
        val retained = savedIds.toSet()
        existing.keys.filterNot { it in retained }.forEach { removedId ->
            check(db.delete("TMChecklistItem", "uuid = ? AND task = ?", arrayOf(removedId, taskId)) == 1)
        }
        updateChecklistCounts(db, taskId)
        touchTask(db, taskId)
        savedIds
    }
    suspend fun createTag(title: String, parentId: String? = null, position: Long = 0): String = write { db ->
        parentId?.let { requireTag(db, it) }
        val id = LocalDbIds.next()
        db.insertOrThrow("TMTag", null, ContentValues().apply {
            put("uuid", id)
            put("title", title)
            put("parent", parentId)
            put("index", position)
        })
        id
    }

    /** cachedTags is opaque and deliberately left untouched. */
    suspend fun setTaskTags(taskId: String, tagIds: List<String>) = write { db ->
        requireTask(db, taskId)
        val unique = tagIds.distinct()
        unique.forEach { requireTag(db, it) }
        db.delete("TMTaskTag", "tasks = ?", arrayOf(taskId))
        unique.forEach { tagId ->
            db.insertOrThrow("TMTaskTag", null, ContentValues().apply {
                put("tasks", taskId)
                put("tags", tagId)
            })
        }
        touchTask(db, taskId)
    }
    suspend fun setAreaTags(areaId: String, tagIds: List<String>) = write { db ->
        requireArea(db, areaId)
        val unique = tagIds.distinct()
        unique.forEach { requireTag(db, it) }
        db.delete("TMAreaTag", "areas = ?", arrayOf(areaId))
        unique.forEach { tagId ->
            db.insertOrThrow("TMAreaTag", null, ContentValues().apply {
                put("areas", areaId)
                put("tags", tagId)
            })
        }
    }

    private suspend fun <T> write(action: (SQLiteDatabase) -> T): T = withContext(dispatcher) {
        val db = helper.writableDatabase
        db.beginTransactionNonExclusive()
        try {
            val result = action(db)
            db.setTransactionSuccessful()
            result
        } finally {
            db.endTransaction()
        }
    }
    private fun timestamp(): Double {
        val instant = clock.instant()
        return instant.epochSecond + instant.nano / 1_000_000_000.0
    }
    private data class TaskIdentity(val type: Long?, val projectId: String?, val headingId: String?)
    private fun requireTask(db: SQLiteDatabase, id: String, expectedType: DbTaskType? = null): TaskIdentity {
        val identity = db.rawQuery("SELECT type, project, heading FROM TMTask WHERE uuid = ? AND trashed = 0", arrayOf(id)).use { c ->
            require(c.moveToFirst()) { "Task does not exist or is trashed: $id" }
            TaskIdentity(if (c.isNull(0)) null else c.getLong(0),
                if (c.isNull(1)) null else c.getString(1), if (c.isNull(2)) null else c.getString(2))
        }
        if (expectedType != null) require(identity.type == expectedType.raw) { "Unexpected entity type: $id" }
        return identity
    }
    private fun requireArea(db: SQLiteDatabase, id: String) {
        db.rawQuery("SELECT uuid FROM TMArea WHERE uuid = ?", arrayOf(id)).use {
            require(it.moveToFirst()) { "Area does not exist: $id" }
        }
    }
    private fun requireTag(db: SQLiteDatabase, id: String) {
        db.rawQuery("SELECT uuid FROM TMTag WHERE uuid = ?", arrayOf(id)).use {
            require(it.moveToFirst()) { "Tag does not exist: $id" }
        }
    }
    private fun validateTaskLocation(db: SQLiteDatabase, projectId: String?, headingId: String?, areaId: String?) {
        require(projectId == null || areaId == null) { "A task cannot be assigned directly to both a project and an area" }
        areaId?.let { requireArea(db, it) }
        projectId?.let { requireTask(db, it, DbTaskType.Project) }
        if (headingId != null) {
            require(projectId != null) { "A heading task must specify its project" }
            val heading = requireTask(db, headingId, DbTaskType.Heading)
            require(heading.projectId == projectId) { "Heading belongs to another project" }
        }
    }
    private fun insertTask(
        db: SQLiteDatabase, type: DbTaskType, title: String, notes: String?, position: Long,
        projectId: String? = null, headingId: String? = null, areaId: String? = null,
    ): String {
        val id = LocalDbIds.next()
        val now = timestamp()
        db.insertOrThrow("TMTask", null, ContentValues().apply {
            put("uuid", id)
            put("type", type.raw)
            put("status", 0)
            put("trashed", 0)
            put("title", title)
            put("notes", notes)
            put("notesSync", 1)
            put("creationDate", now)
            put("userModificationDate", now)
            put("index", position)
            // Local default: newly created content is unscheduled.
            put("start", 1)
            put("startBucket", 0)
            putNull("startDate")
            putNull("reminderTime")
            putNull("deadline")
            putNull("stopDate")
            put("project", projectId)
            put("heading", headingId)
            put("area", areaId)
            put("leavesTombstone", 0)
            if (type == DbTaskType.Todo) {
                put("checklistItemsCount", 0)
                put("openChecklistItemsCount", 0)
            }
        })
        return id
    }
    private fun updateTask(db: SQLiteDatabase, id: String, values: ContentValues) {
        check(db.update("TMTask", values, "uuid = ?", arrayOf(id)) == 1) { "Task update failed: $id" }
    }
    private fun touchTask(db: SQLiteDatabase, id: String) {
        updateTask(db, id, ContentValues().apply { put("userModificationDate", timestamp()) })
    }
    private fun ids(db: SQLiteDatabase, sql: String, args: Array<String>): List<String> =
        db.rawQuery(sql, args).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
    private fun applyOrder(db: SQLiteDatabase, orderedIds: List<String>) {
        val positions = localOrderPositions(orderedIds.size)
        orderedIds.forEachIndexed { index, id ->
            updateTask(db, id, ContentValues().apply {
                put("index", positions[index])
                put("userModificationDate", timestamp())
            })
        }
    }
    private fun projectSubtree(db: SQLiteDatabase, projectId: String): Set<String> {
        val descendants = ids(db,
            "SELECT uuid FROM TMTask WHERE project = ? OR heading IN (SELECT uuid FROM TMTask WHERE type = 2 AND project = ?)",
            arrayOf(projectId, projectId))
        return (descendants + projectId).toSet()
    }
    private fun trash(db: SQLiteDatabase, ids: Set<String>) {
        ids.forEach { id ->
            db.update("TMTask", ContentValues().apply {
                put("trashed", 1)
                put("userModificationDate", timestamp())
            }, "uuid = ? AND trashed = 0", arrayOf(id))
        }
    }
    private fun updateChecklistCounts(db: SQLiteDatabase, taskId: String) {
        db.execSQL(
            "UPDATE TMTask SET checklistItemsCount = (SELECT COUNT(*) FROM TMChecklistItem WHERE task = ?), " +
                "openChecklistItemsCount = (SELECT COUNT(*) FROM TMChecklistItem WHERE task = ? AND status = 0) WHERE uuid = ?",
            arrayOf(taskId, taskId, taskId))
    }
}

private fun <T, R : Any> ContentValues.applyChange(column: String, change: FieldChange<T>, encode: (T) -> R) {
    when (change) {
        FieldChange.Keep -> Unit
        is FieldChange.Set -> {
            val source = change.value
            if (source == null) putNull(column) else {
                when (val value = encode(source)) {
                    is String -> put(column, value)
                    is Int -> put(column, value)
                    is Long -> put(column, value)
                    else -> error("Unsupported encoded type for $column")
                }
            }
        }
    }
}
