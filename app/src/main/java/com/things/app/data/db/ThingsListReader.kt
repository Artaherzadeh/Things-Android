package com.things.app.data.db

import java.time.Clock
import java.time.DateTimeException
import java.time.LocalDate

enum class UpcomingEventKind { Start, Deadline }
data class UpcomingEvent(val date: LocalDate, val kind: UpcomingEventKind, val item: TaskRecord)
data class ProjectReadSection(
    val active: List<TaskRecord>,
    val later: List<TaskRecord>,
    val completed: List<TaskRecord>,
)
data class ThingsLists(
    val inbox: List<TaskRecord>, val today: List<TaskRecord>, val evening: List<TaskRecord>,
    val upcoming: List<UpcomingEvent>, val anytime: List<TaskRecord>, val someday: List<TaskRecord>,
    val completed: List<TaskRecord>, val trash: List<TaskRecord>, val templates: List<TaskRecord>,
    val unclassified: List<TaskRecord>, val warnings: List<ReadWarning>,
    val projectSections: Map<String, ProjectReadSection> = emptyMap(),
    val activeProjectIds: Set<String> = emptySet(),
    val projectOwners: Map<String, String> = emptyMap(),
    val startDates: Map<String, LocalDate?> = emptyMap(),
    val deadlineDates: Map<String, LocalDate?> = emptyMap(),
)

/** One classification pass for list membership, project sections and Home visibility. */
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
        val starts = linkedMapOf<String, LocalDate?>()
        val deadlines = linkedMapOf<String, LocalDate?>()
        val owners = linkedMapOf<String, String>()
        fun reject(t: TaskRecord, field: String, message: String) {
            warnings += ReadWarning(t.id, field, message)
            rejected[t.id] = t
        }
        fun decode(t: TaskRecord, field: String): LocalDate? {
            val raw = t.row.integer(field) ?: return null
            if (raw == 0L) return null
            return try {
                require(raw in Int.MIN_VALUE.toLong()..0xFFFFFFFFL)
                ThingsDate.unpackDate(raw.toInt())
            } catch (_: IllegalArgumentException) {
                reject(t, field, "Invalid packed date; raw row preserved."); null
            } catch (_: DateTimeException) {
                reject(t, field, "Invalid calendar date; raw row preserved."); null
            }
        }
        fun template(t: TaskRecord) = t.recurrenceRule?.isNotEmpty() == true ||
            t.row.blob("repeater")?.isNotEmpty() == true
        val order = compareBy<TaskRecord> { it.position }.thenBy { it.id }
        val candidates = snapshot.tasks.filter {
            it.knownType == DbTaskType.Todo || it.knownType == DbTaskType.Project
        }
        val templates = candidates.filter { it.trashed == 0L && template(it) }
        val templateIds = templates.map { it.id }.toSet()
        candidates.forEach { t ->
            if (t.trashed != 0L && t.trashed != 1L) reject(t, "trashed", "Unknown trash state.")
            if (t.status != policy.openStatus && t.status != policy.completedStatus) reject(t, "status", "Unknown status.")
            starts[t.id] = decode(t, "startDate")
            deadlines[t.id] = decode(t, "deadline")
            if (t.id in templateIds) warnings += ReadWarning(t.id, "repeat", "Repeat forecasting is not implemented.")
            if (t.knownType == DbTaskType.Todo) {
                val direct = t.projectId?.let { byId[it] }
                val heading = t.headingId?.let { byId[it] }
                if (t.projectId != null && direct?.knownType != DbTaskType.Project) {
                    reject(t, "project", "Missing or invalid project.")
                }
                if (t.headingId != null && heading?.knownType != DbTaskType.Heading) {
                    reject(t, "heading", "Missing or invalid heading.")
                }
                val parent = if (heading != null) heading.projectId?.let { byId[it] } else direct
                if (heading != null && (parent == null || parent.knownType != DbTaskType.Project ||
                        (direct != null && direct.id != parent.id))) {
                    reject(t, "heading", "Conflicting or invalid heading parent.")
                }
                if (heading != null && heading.trashed != 0L) reject(t, "heading", "Heading is not active.")
                if (parent != null && t.id !in rejected) owners[t.id] = parent.id
            }
        }
        // Propagate malformed project scheduling after every project has been decoded.
        candidates.forEach { t ->
            if (owners[t.id] in rejected) reject(t, "project", "Project has invalid data.")
        }
        rejected.keys.forEach { owners.remove(it) }
        val live = candidates.filter { it.trashed == 0L && it.id !in rejected }
        val open = live.filter { it.status == policy.openStatus && it.id !in templateIds }
        val activeProjects = open.filter {
            it.knownType == DbTaskType.Project && it.start != policy.somedayStart &&
                starts[it.id]?.isAfter(day) != true
        }.map { it.id }.toSet()
        val inbox = mutableListOf<TaskRecord>()
        val today = mutableListOf<TaskRecord>()
        val evening = mutableListOf<TaskRecord>()
        val anytime = mutableListOf<TaskRecord>()
        val someday = mutableListOf<TaskRecord>()
        val upcoming = mutableListOf<UpcomingEvent>()
        open.forEach { t ->
            val parent = owners[t.id]?.let { byId[it] }
            if (parent != null && (parent.trashed != 0L || parent.status != policy.openStatus || template(parent))) return@forEach
            if (t.start == policy.somedayStart || parent?.start == policy.somedayStart) {
                someday += t
                return@forEach
            }
            val start = starts[t.id]
            val due = deadlines[t.id]
            val parentFuture = parent?.let { starts[it.id]?.isAfter(day) } == true
            if (t.start == policy.inboxStart && t.knownType == DbTaskType.Todo && parent == null) inbox += t
            start?.takeIf { it.isAfter(day) }?.let { upcoming += UpcomingEvent(it, UpcomingEventKind.Start, t) }
            due?.takeIf { it.isAfter(day) }?.let { upcoming += UpcomingEvent(it, UpcomingEventKind.Deadline, t) }
            val dueNow = due?.let { !it.isAfter(day) } == true
            val startsNow = start?.let { !it.isAfter(day) } == true
            if (!parentFuture && (dueNow || startsNow)) {
                if (t.startBucket == policy.eveningBucket && !dueNow) evening += t else today += t
            }
            if (!parentFuture && start?.isAfter(day) != true && t.start != policy.inboxStart) anytime += t
        }
        val sections = live.filter { it.knownType == DbTaskType.Project && it.status == policy.openStatus }.associate { p ->
            val children = live.filter { it.knownType == DbTaskType.Todo && owners[it.id] == p.id }.sortedWith(order)
            val pending = children.filter { it.status == policy.openStatus }
            fun later(t: TaskRecord) = t.id in templateIds || starts[t.id]?.isAfter(day) == true
            p.id to ProjectReadSection(
                pending.filterNot(::later), pending.filter(::later),
                children.filter { it.status == policy.completedStatus }
                    .sortedWith(compareByDescending<TaskRecord> { it.stopDate }.thenBy { it.id }),
            )
        }
        // Explicit Today positions precede fallback ordering; stale positions are not reused.
        val todayOrder = compareBy<TaskRecord> {
            if (it.row.integer("todayIndexReferenceDate") == packedToday && it.todayPosition != null) 0 else 1
        }.thenBy {
            if (it.row.integer("todayIndexReferenceDate") == packedToday) it.todayPosition else null
        }.then(order)
        return ThingsLists(
            inbox.sortedWith(order), today.sortedWith(todayOrder), evening.sortedWith(todayOrder),
            upcoming.sortedWith(compareBy<UpcomingEvent> { it.date }.thenBy { it.item.position }.thenBy { it.item.id }.thenBy { it.kind.ordinal }),
            anytime.sortedWith(order), someday.sortedWith(order),
            live.filter { it.status == policy.completedStatus }.sortedWith(compareByDescending<TaskRecord> { it.stopDate }.thenBy { it.id }),
            candidates.filter { it.trashed == 1L }.sortedWith(order), templates.sortedWith(order),
            rejected.values.toList(), warnings.toList(), sections, activeProjects, owners, starts, deadlines,
        )
    }
}
