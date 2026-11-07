package com.things.app.data.db

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaTest {

    private val schema = File("src/main/assets/schema.sql").readText()

    @Test
    fun theSchemaFileSplitsIntoThirteenTablesAndElevenIndexes() {
        val statements = splitSchema(schema)

        assertEquals(24, statements.size)
        assertEquals(13, statements.count { it.startsWith("CREATE TABLE") })
        assertEquals(11, statements.count { it.startsWith("CREATE INDEX") })
    }

    @Test
    fun theTaskTableAndItsPartialIndexAreKeptWhole() {
        val statements = splitSchema(schema)

        assertTrue(statements.any { it.startsWith("CREATE TABLE TMTask") && it.contains("\"rt1_recurrenceRule\"") })
        assertTrue(statements.any { it.contains("WHERE rt1_recurrenceRule IS NOT NULL") })
    }

    @Test
    fun splittingNeedsTheSeparatorLineOnly() {
        assertEquals(listOf("a; b", "c"), splitSchema("a; b\n-- @@\nc\n"))
        assertEquals(listOf("a -- @@ b"), splitSchema("a -- @@ b"))
    }

    @Test
    fun theVersionPlistHoldsTheNumber() {
        assertTrue(databaseVersionPlist(30).endsWith("<plist version=\"1.0\"><integer>30</integer></plist>"))
    }
}
