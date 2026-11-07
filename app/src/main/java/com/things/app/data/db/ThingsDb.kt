package com.things.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * The local database of the app, shaped like the database of Things: to-dos, projects and headings
 * are rows of TMTask told apart by `type`, areas, tags, checklist items and tombstones have tables of
 * their own, and the links between them are plain text columns (no foreign keys, like the original).
 *
 * The schema is the file assets/schema.sql, run as it is when the database is created, so it can be
 * compared with the original. The write-ahead log is on, as in the original. The version of the
 * schema itself is kept in Meta (databaseVersion, [SchemaVersion]); SQLite's own user_version is
 * the helper's version number and means nothing else.
 *
 * Nothing is copied from another database: the tables start empty. This class only opens and
 * creates the file; reading and writing rows belongs to the classes that use it.
 */
class ThingsDb(private val context: Context) : SQLiteOpenHelper(context, Name, null, HelperVersion) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(false)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        val sql = context.assets.open(SchemaAsset).bufferedReader().use { it.readText() }
        splitSchema(sql).forEach { db.execSQL(it) }
        db.execSQL(
            "INSERT INTO Meta(\"key\", \"value\") VALUES('databaseVersion', ?)",
            arrayOf(databaseVersionPlist(SchemaVersion)),
        )
    }

    // The helper version stays 1 until there is a second schema; its migrations will go here, and
    // each of them has to update Meta.databaseVersion as well.
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    companion object {
        const val Name = "main.sqlite"
        const val SchemaAsset = "schema.sql"

        /** The number kept in Meta.databaseVersion. */
        const val SchemaVersion = 30

        private const val HelperVersion = 1
    }
}
