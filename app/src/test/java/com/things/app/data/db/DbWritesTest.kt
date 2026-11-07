package com.things.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DbWritesTest {
    @Test fun generatedIdsHaveTheLocalDocumentedShape() {
        val ids = List(1000) { LocalDbIds.next() }
        assertTrue(ids.all { it.length == 22 && it.all { c -> c in '0'..'9' || c in 'A'..'Z' || c in 'a'..'z' } })
        assertEquals(ids.size, ids.toSet().size)
    }
    @Test fun keepAndExplicitNullAreDifferentOperations() {
        val keep: FieldChange<String> = FieldChange.Keep
        val clear: FieldChange<String> = FieldChange.Set(null)
        assertNotEquals(keep, clear)
    }
    @Test fun orderPositionsAreStableAndSpaced() {
        assertEquals(listOf(0L, 1024L, 2048L), localOrderPositions(3))
        assertEquals(emptyList<Long>(), localOrderPositions(0))
    }
    @Test(expected = IllegalArgumentException::class)
    fun negativeOrderSizeIsRejected() {
        localOrderPositions(-1)
    }
}
