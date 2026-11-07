package com.things.app.data.db

/** The line between two statements of schema.sql. The statements hold semicolons, so no split on those. */
private val StatementSeparator = Regex("(?m)^-- @@\\s*\$")

/** The statements of the schema file, in order, trimmed, with no empty ones. */
internal fun splitSchema(sql: String): List<String> =
    sql.split(StatementSeparator).map { it.trim() }.filter { it.isNotEmpty() }

/** The value the Meta table holds for databaseVersion: a property list with the number in it. */
internal fun databaseVersionPlist(version: Int): String =
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" " +
        "\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
        "<plist version=\"1.0\"><integer>$version</integer></plist>"
