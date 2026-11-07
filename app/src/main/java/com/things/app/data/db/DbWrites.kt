package com.things.app.data.db

import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalTime

/** Keep omits the column; Set(null) explicitly stores SQL NULL. */
sealed interface FieldChange<out T> {
    data object Keep : FieldChange<Nothing>
    data class Set<T>(val value: T?) : FieldChange<T>
}

data class TaskPatch(
    val title: FieldChange<String> = FieldChange.Keep,
    val notes: FieldChange<String> = FieldChange.Keep,
    val start: FieldChange<Long> = FieldChange.Keep,
    val startDate: FieldChange<LocalDate> = FieldChange.Keep,
    val startBucket: FieldChange<Long> = FieldChange.Keep,
    val reminderTime: FieldChange<LocalTime> = FieldChange.Keep,
    val deadline: FieldChange<LocalDate> = FieldChange.Keep,
)
data class NewTask(
    val title: String = "",
    val notes: String? = null,
    val projectId: String? = null,
    val headingId: String? = null,
    val areaId: String? = null,
    val position: Long = 0,
)
data class NewProject(
    val title: String = "",
    val notes: String? = null,
    val areaId: String? = null,
    val position: Long = 0,
)
data class NewHeading(val title: String = "", val projectId: String, val position: Long = 0)
data class ChecklistWrite(val id: String? = null, val title: String, val completed: Boolean = false)
enum class HeadingDeleteMode { KeepTasks, TrashTasks }

/** Local ID shape only; not an external ID-generation algorithm. */
internal object LocalDbIds {
    private const val Alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private val random = SecureRandom()
    fun next(): String = buildString(22) {
        repeat(22) { append(Alphabet[random.nextInt(Alphabet.length)]) }
    }
}

internal fun localOrderPositions(count: Int): List<Long> {
    require(count >= 0)
    return List(count) { it.toLong() * 1024L }
}
