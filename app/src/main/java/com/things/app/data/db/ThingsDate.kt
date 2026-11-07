package com.things.app.data.db

import java.time.LocalDate
import java.time.LocalTime

/** Storage codecs only: no scheduling, recurrence or timezone rules. */
object ThingsDate {
    /** Stores a calendar day without a timezone. Only years fitting the 16-bit field are accepted. */
    fun packDate(date: LocalDate): Int {
        require(date.year in 1..65535) { "Year must be between 1 and 65535" }
        return (date.year shl 16) or (date.monthValue shl 12) or (date.dayOfMonth shl 7)
    }

    /** Rejects reserved bits and invalid calendar dates rather than silently changing them. */
    fun unpackDate(value: Int): LocalDate {
        require(value and 0x7F == 0) { "Packed date contains reserved bits" }
        val year = value ushr 16
        require(year != 0) { "Packed date has no valid year" }
        return LocalDate.of(year, (value ushr 12) and 0xF, (value ushr 7) and 0x1F)
    }

    /** The storage format has minute precision. Sub-minute values are rejected, not truncated. */
    fun packTime(time: LocalTime): Int {
        require(time.second == 0 && time.nano == 0) { "Time must have minute precision" }
        return (time.hour shl 26) or (time.minute shl 20)
    }

    fun unpackTime(value: Int): LocalTime {
        require(value and 0xFFFFF == 0) { "Packed time contains reserved bits" }
        return LocalTime.of(value ushr 26, (value ushr 20) and 0x3F)
    }
}
