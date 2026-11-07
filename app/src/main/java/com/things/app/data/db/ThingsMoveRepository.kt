package com.things.app.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface MoveDestination {
    data object Loose : MoveDestination
    data class Area(val areaId: String) : MoveDestination
    data class Project(val projectId: String) : MoveDestination
    data class Heading(val headingId: String) : MoveDestination
}

/** Local relationship changes only. The caller owns the ThingsDb lifecycle. */
class ThingsMoveRepository(
    private val helper: ThingsDb,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Appends one entity type to its destination, preserving the supplied order. */
    suspend fun move(orderedIds: List<String>, destination: MoveDestination) = withContext(dispatcher) {
        require(orderedIds.isNotEmpty()) { "Nothing to move" }
        require(orderedIds.distinct().size == orderedIds.size) { "Duplicate IDs in move request" }
        val db = helper.writableDatabase
        db.beginTransactionNonExclusive()
        try {
            val records = orderedIds.map { readEntity(db, it) }
            require(records.map { it.type }.toSet().size == 1) { "Move one entity type at a time" }
            val type = records.first().type
            val target = resolveDestination(db, type, destination)
            val now = timestamp()
            records.forEach { record ->
                val position = nextPosition(db, type, target)
                update(db, record.id, ContentValues().apply {
                    put("area", target.areaId)
                    put("project", target.projectId)
                    put("heading", target.headingId)
                    put("index", position)
                    put("userModificationDate", now)
                })
                if (type == DbTaskType.Heading) {
                    moveHeadingChildren(db, record.id, record.projectId, requireNotNull(target.projectId), now)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
    private data class Entity(val id: String, val type: DbTaskType, val projectId: String?)
    private data class Target(val areaId: String? = null, val projectId: String? = null, val headingId: String? = null)
    private fun readEntity(db: SQLiteDatabase, id: String): Entity =
        db.rawQuery("SELECT type, project FROM TMTask WHERE uuid = ? AND trashed = 0", arrayOf(id)).use { cursor ->
            require(cursor.moveToFirst()) { "Entity is missing or trashed: $id" }
            val rawType = if (cursor.isNull(0)) null else cursor.getLong(0)
            val type = requireNotNull(DbTaskType.fromRaw(rawType)) { "Unknown entity type: $id" }
            Entity(id, type, if (cursor.isNull(1)) null else cursor.getString(1))
        }
    private fun resolveDestination(db: SQLiteDatabase, type: DbTaskType, destination: MoveDestination): Target = when (destination) {
        MoveDestination.Loose -> {
            require(type != DbTaskType.Heading) { "A heading must belong to a project" }
            Target()
        }
        is MoveDestination.Area -> {
            require(type != DbTaskType.Heading) { "A heading cannot belong directly to an area" }
            requireArea(db, destination.areaId)
            Target(areaId = destination.areaId)
        }
        is MoveDestination.Project -> {
            require(type != DbTaskType.Project) { "Projects cannot be nested inside projects" }
            val project = readEntity(db, destination.projectId)
            require(project.type == DbTaskType.Project) { "Destination is not a project" }
            Target(projectId = project.id)
        }
        is MoveDestination.Heading -> {
            require(type == DbTaskType.Todo) { "Only to-dos can be placed inside a heading" }
            val heading = readEntity(db, destination.headingId)
            require(heading.type == DbTaskType.Heading) { "Destination is not a heading" }
            val projectId = requireNotNull(heading.projectId) { "Destination heading has no project" }
            require(readEntity(db, projectId).type == DbTaskType.Project) { "Destination heading has an invalid project" }
            Target(projectId = projectId, headingId = heading.id)
        }
    }
    private fun requireArea(db: SQLiteDatabase, areaId: String) {
        db.rawQuery("SELECT uuid FROM TMArea WHERE uuid = ?", arrayOf(areaId)).use {
            require(it.moveToFirst()) { "Destination area does not exist" }
        }
    }
    /** Moves all children, including later/logged/trashed rows, without changing their statuses. */
    private fun moveHeadingChildren(db: SQLiteDatabase, headingId: String, previousProjectId: String?, destinationProjectId: String, timestamp: Double) {
        db.rawQuery("SELECT uuid, type, project FROM TMTask WHERE heading = ?", arrayOf(headingId)).use { cursor ->
            while (cursor.moveToNext()) {
                require(!cursor.isNull(1) && cursor.getLong(1) == DbTaskType.Todo.raw) { "Heading contains an invalid child type" }
                val projectId = if (cursor.isNull(2)) null else cursor.getString(2)
                require(projectId == null || projectId == previousProjectId) { "Heading child has conflicting project ownership" }
            }
        }
        db.update("TMTask", ContentValues().apply {
            put("project", destinationProjectId)
            putNull("area")
            put("userModificationDate", timestamp)
        }, "heading = ?", arrayOf(headingId))
    }
    private fun nextPosition(db: SQLiteDatabase, type: DbTaskType, target: Target): Long {
        val clauses = mutableListOf("type = ?", "trashed = 0")
        val arguments = mutableListOf(type.raw.toString())
        fun match(column: String, value: String?) {
            if (value == null) clauses += "$column IS NULL" else {
                clauses += "$column = ?"
                arguments += value
            }
        }
        match("area", target.areaId)
        match("project", target.projectId)
        match("heading", target.headingId)
        return db.rawQuery("SELECT MAX(\"index\") FROM TMTask WHERE ${clauses.joinToString(" AND ")}", arguments.toTypedArray()).use { cursor ->
            check(cursor.moveToFirst())
            if (cursor.isNull(0)) 0L else Math.addExact(cursor.getLong(0), 1024L)
        }
    }
    private fun update(db: SQLiteDatabase, id: String, values: ContentValues) {
        check(db.update("TMTask", values, "uuid = ? AND trashed = 0", arrayOf(id)) == 1) { "Move failed: $id" }
    }
    private fun timestamp(): Double {
        val instant = clock.instant()
        return instant.epochSecond + instant.nano / 1_000_000_000.0
    }
}
