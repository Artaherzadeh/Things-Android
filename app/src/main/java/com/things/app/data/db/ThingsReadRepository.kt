package com.things.app.data.db

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only application API. The caller owns the helper's lifecycle. */
class ThingsReadRepository(
    private val helper: ThingsDb,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun readSnapshot(): ThingsSnapshot = withContext(dispatcher) {
        val db = helper.readableDatabase
        db.beginTransactionNonExclusive()
        try {
            val snapshot = ThingsSnapshot(
                tasks = rows(db, "SELECT * FROM TMTask ORDER BY \"index\", uuid").map(::TaskRecord),
                areas = rows(db, "SELECT * FROM TMArea ORDER BY \"index\", uuid").map(::AreaRecord),
                tags = rows(db, "SELECT * FROM TMTag ORDER BY \"index\", uuid").map(::TagRecord),
                checklist = rows(db, "SELECT * FROM TMChecklistItem ORDER BY \"index\", uuid").map(::ChecklistRecord),
                taskTags = rows(db, "SELECT tasks, tags FROM TMTaskTag ORDER BY tasks, tags").map {
                    TaskTagLink(it.requiredText("tasks"), it.requiredText("tags"))
                },
                areaTags = rows(db, "SELECT areas, tags FROM TMAreaTag ORDER BY areas, tags").map {
                    AreaTagLink(it.requiredText("areas"), it.requiredText("tags"))
                },
            )
            db.setTransactionSuccessful()
            snapshot
        } finally {
            db.endTransaction()
        }
    }

    private fun rows(db: SQLiteDatabase, sql: String): List<DbRecord> =
        db.rawQuery(sql, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.readRecord())
            }
        }
}

private fun Cursor.readRecord(): DbRecord {
    val values = LinkedHashMap<String, DbValue>(columnCount)
    for (i in 0 until columnCount) {
        values[getColumnName(i)] = when (getType(i)) {
            Cursor.FIELD_TYPE_NULL -> DbValue.Null
            Cursor.FIELD_TYPE_INTEGER -> DbValue.Integer(getLong(i))
            Cursor.FIELD_TYPE_FLOAT -> DbValue.Real(getDouble(i))
            Cursor.FIELD_TYPE_STRING -> DbValue.Text(getString(i))
            Cursor.FIELD_TYPE_BLOB -> DbValue.Blob(getBlob(i))
            else -> error("Unsupported SQLite value type at column $i")
        }
    }
    return DbRecord(values)
}
