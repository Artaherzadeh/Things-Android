package com.things.app.data.db

import java.time.Clock
import java.time.DateTimeException
import java.time.LocalDate

/** Events are distinct: one item can have both a future start and a deadline. */
enum class UpcomingEventKind { Start, Deadline }
data class UpcomingEvent(val date: LocalDate, val kind: UpcomingEventKind, val item: TaskRecord)

data class ThingsLists(
    val inbox: List<TaskRecord>,
    val today: List<TaskRecord>,
    val evening: List<TaskRecord>,
    val upcoming: List<UpcomingEvent>,
    val anytime: List<TaskRecord>,
    val someday: List<TaskRecord>,
    val completed: List<TaskRecord>,
    val trash: List<TaskRecord>,
    val templates: List<TaskRecord>,
    val unclassified: List<TaskRecord>,
    val warnings: List<ReadWarning>,
)

/**
 * Read-only local list classification. Does not alter rows or generate repeat instances.
 * Completed is intentionally not called Logbook: manual logging settings are not decoded yet.
 * Raw start/bucket conventions come from LocalReadPolicy and remain explicit.
 */
class ThingsListReader(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val policy: LocalReadPolicy = LocalReadPolicy(),
) {
    fun read(snapshot: ThingsSnapshot): ThingsLists {
        val day = LocalDate.now(clock)
        val packedToday = ThingsDate.packDate(day).toLong()
        val byId = snapshot.tasks.associateBy { it.id }
        val warnings = linkedSetOf<ReadWarning>()
        val rejected = linkedMapOf<String, TaskRecord>()
        val dates = mutableMapOf<Pair<String, String>, LocalDate?>()
        fun reject(t: TaskRecord, field: String, message: String) {
            warnings += ReadWarning(t.id, field, message)
            rejected[t.id] = t
        }
        fun date(t: TaskRecord, column: String): LocalDate? = dates.getOrPut(t.id to column) {
            val raw = t.row.integer(column)
            if (raw == null || raw == 0L) null else {
                try {
                    require(raw in Int.MIN_VALUE.toLong()..0xFFFFFFFFL)
                    ThingsDate.unpackDate(raw.toInt())
                } catch (_: IllegalArgumentException) {
                    reject(t, column, "Invalid packed date; raw row preserved.")
                    null
                } catch (_: DateTimeException) {
                    reject(t, column, "Invalid calendar date; raw row preserved.")
                    null
                }
            }
        }
        fun template(t: TaskRecord): Boolean =
            t.recurrenceRule?.isNotEmpty() == true || t.row.blob("repeater")?.isNotEmpty() == true

        fun owner(t: TaskRecord): TaskRecord? {
            val direct = t.projectId?.let { byId[it] }
            if (t.projectId != null && direct?.knownType != DbTaskType.Project) {
                reject(t, "project", "Missing or invalid project.")
                return null
            }
            val heading = t.headingId?.let { byId[it] }
            if (t.headingId != null && heading?.knownType != DbTaskType.Heading) {
                reject(t, "heading", "Missing or invalid heading.")
                return null
            }
            if (heading != null) {
                val parent = heading.projectId?.let { byId[it] }
                if (parent?.knownType != DbTaskType.Project ||
                    (direct != null && direct.id != parent.id)) {
                    reject(t, "heading", "Conflicting or invalid heading parent.")
                    return null
                }
                if (heading.trashed != 0L) reject(t, "heading", "Heading is not active.")
                return parent
            }
            return direct
        }
        val normalOrder = compareBy<TaskRecord> { it.position }.thenBy { it.id }
        val candidates = snapshot.tasks.filter {
            it.knownType == DbTaskType.Todo || it.knownType == DbTaskType.Project
        }
        val trash = candidates.filter { it.trashed == 1L }.sortedWith(normalOrder)
        val completed = candidates.filter { it.trashed == 0L && it.status == policy.completedStatus }
            .sortedWith(compareByDescending<TaskRecord> { it.stopDate }.thenBy { it.id })
        val templates = mutableListOf<TaskRecord>()
        val open = candidates.filter { t ->
            when {
                t.trashed != 0L && t.trashed != 1L -> {
                    reject(t, "trashed", "Unknown trash state."); false
                }
                t.trashed == 1L -> false
                t.status == policy.completedStatus -> false
                t.status != policy.openStatus -> {
                    reject(t, "status", "Unknown status; not treated as open."); false
                }
                template(t) -> {
                    templates += t
                    warnings += ReadWarning(t.id, "repeat", "Repeat forecasting is not implemented.")
                    false
                }
                else -> true
            }
        }
        val inbox = mutableListOf<TaskRecord>()
        val today = mutableListOf<TaskRecord>()
        val evening = mutableListOf<TaskRecord>()
        val anytime = mutableListOf<TaskRecord>()
        val someday = mutableListOf<TaskRecord>()
        val upcoming = mutableListOf<UpcomingEvent>()
        open.forEach { t ->
            val start = date(t, "startDate")
            val deadline = date(t, "deadline")
            val parent = if (t.knownType == DbTaskType.Todo) owner(t) else null
            val parentStart = parent?.let { date(it, "startDate") }
            if (parent != null && parent.id in rejected) {
                reject(t, "project", "Project has unreadable scheduling data.")
            }
            if (t.id in rejected) return@forEach
            val parentUnavailable = parent != null &&
                (parent.trashed != 0L || parent.status != policy.openStatus || template(parent))
            if (parentUnavailable) return@forEach
            val isSomeday = t.start == policy.somedayStart || parent?.start == policy.somedayStart
            if (isSomeday) {
                someday += t
                return@forEach
            }
            if (t.start == policy.inboxStart && t.knownType == DbTaskType.Todo && parent == null) {
                inbox += t
            }
            start?.takeIf { it.isAfter(day) }?.let {
                upcoming += UpcomingEvent(it, UpcomingEventKind.Start, t)
            }
            deadline?.takeIf { it.isAfter(day) }?.let {
                upcoming += UpcomingEvent(it, UpcomingEventKind.Deadline, t)
            }
            val inactive = start?.isAfter(day) == true || parentStart?.isAfter(day) == true
            val isDue = deadline?.let { !it.isAfter(day) } == true
            val isScheduled = start?.let { !it.isAfter(day) } == true
            if (parentStart?.isAfter(day) != true && (isDue || isScheduled)) {
                if (t.startBucket == policy.eveningBucket && !isDue) evening += t else today += t
            }
            if (!inactive && t.start != policy.inboxStart) anytime += t
        }
        // The database's reference date prevents using a stale Today order on another day.
        val todayOrder = compareBy<TaskRecord> {
            if (it.row.integer("todayIndexReferenceDate") == packedToday) it.todayPosition else null
        }.then(normalOrder)
        return ThingsLists(
            inbox.sortedWith(normalOrder), today.sortedWith(todayOrder), evening.sortedWith(todayOrder),
            upcoming.sortedWith(compareBy<UpcomingEvent> { it.date }.thenBy { it.item.position }
                .thenBy { it.item.id }.thenBy { it.kind.ordinal }),
            anytime.sortedWith(normalOrder), someday.sortedWith(normalOrder), completed, trash,
            templates.sortedWith(normalOrder), rejected.values.toList(), warnings.toList(),
        )
    }
}
