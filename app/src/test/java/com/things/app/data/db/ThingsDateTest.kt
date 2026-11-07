package com.things.app.data.db

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ThingsDateTest {
    @Test
    fun referenceDateMatchesDocument() {
        val date = LocalDate.of(2026, 9, 13)
        assertEquals(132814464, ThingsDate.packDate(date))
        assertEquals(date, ThingsDate.unpackDate(132814464))
    }

    @Test
    fun referenceTimeMatchesDocument() {
        val time = LocalTime.of(8, 0)
        assertEquals(536870912, ThingsDate.packTime(time))
        assertEquals(time, ThingsDate.unpackTime(536870912))
    }

    @Test
    fun datesRoundTripAcrossLeapAndCenturyBoundaries() {
        var date = LocalDate.of(1999, 1, 1)
        val end = LocalDate.of(2101, 1, 1)
        while (date.isBefore(end)) {
            val packed = ThingsDate.packDate(date)
            assertEquals(date, ThingsDate.unpackDate(packed))
            assertEquals(packed, ThingsDate.packDate(ThingsDate.unpackDate(packed)))
            date = date.plusDays(1)
        }
    }

    @Test
    fun boundaryYearsRoundTripIncludingSignedIntRepresentation() {
        listOf(1, 32767, 32768, 65535).forEach { year ->
            val date = LocalDate.of(year, 12, 31)
            assertEquals(date, ThingsDate.unpackDate(ThingsDate.packDate(date)))
        }
    }

    @Test
    fun everyMinuteRoundTrips() {
        for (hour in 0..23) {
            for (minute in 0..59) {
                val time = LocalTime.of(hour, minute)
                val packed = ThingsDate.packTime(time)
                assertEquals(time, ThingsDate.unpackTime(packed))
                assertEquals(packed, ThingsDate.packTime(ThingsDate.unpackTime(packed)))
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsYearZero() {
        ThingsDate.packDate(LocalDate.of(0, 1, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsYearOverflow() {
        ThingsDate.packDate(LocalDate.of(65536, 1, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroAsDate() {
        ThingsDate.unpackDate(0)
    }

    @Test(expected = DateTimeException::class)
    fun rejectsInvalidDay() {
        ThingsDate.unpackDate((2026 shl 16) or (2 shl 12) or (30 shl 7))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDateReservedBits() {
        ThingsDate.unpackDate(132814464 or 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSeconds() {
        ThingsDate.packTime(LocalTime.of(8, 0, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNanoseconds() {
        ThingsDate.packTime(LocalTime.of(8, 0, 0, 1))
    }

    @Test(expected = DateTimeException::class)
    fun rejectsInvalidHour() {
        ThingsDate.unpackTime(24 shl 26)
    }

    @Test(expected = DateTimeException::class)
    fun rejectsInvalidMinute() {
        ThingsDate.unpackTime(60 shl 20)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTimeReservedBits() {
        ThingsDate.unpackTime(536870912 or 1)
    }
}
