package com.things.app.data.db

import com.things.app.ui.components.ChecklistItemModel
import com.things.app.ui.components.DeadlineModel
import com.things.app.ui.components.ScheduleKind
import com.things.app.ui.components.ScheduleModel
import com.things.app.ui.components.TaskDetailsModel
import com.things.app.ui.components.TaskRowModel
import com.things.app.ui.home.Area
import com.things.app.ui.home.HomeUiState
import com.things.app.ui.home.Project
import com.things.app.ui.project.LoggedTaskModel
import com.things.app.ui.project.ProjectHeadingModel
import com.things.app.ui.project.ProjectUiState
import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor

data class LocalReadPolicy(
    val openStatus: Long = 0, val completedStatus: Long = 3,
    val inboxStart: Long = 0, val somedayStart: Long = 2, val eveningBucket: Long = 1,
)
data class ReadWarning(val entityId: String, val field: String, val message: String)
data class ThingsUiRead(
    val snapshot: ThingsSnapshot, val home: HomeUiState,
    val projects: Map<String, ProjectUiState>, val tasks: Map<String, TaskRowModel>,
    val warnings: List<ReadWarning>, val lists: ThingsLists? = null,
)

/** Presentation only: list membership and project sections belong to ThingsListReader. */
class ThingsUiReader(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = clock.zone,
    private val locale: Locale = Locale.getDefault(),
    private val policy: LocalReadPolicy = LocalReadPolicy(),
) {
    fun map(snapshot: ThingsSnapshot): ThingsUiRead {
        // One instant and zone for the entire read, even at a midnight boundary.
        val fixedClock = Clock.fixed(clock.instant(), zone)
        val today = LocalDate.now(fixedClock)
        val lists = ThingsListReader(fixedClock, policy).read(snapshot)
        val warnings = lists.warnings.toMutableSet()
        val blocked = lists.unclassified.map { it.id }.toSet()
        val todayIds = (lists.today + lists.evening).map { it.id }.toSet()
        val templateIds = lists.templates.map { it.id }.toSet()
        val byId = snapshot.tasks.associateBy { it.id }
        val areasById = snapshot.areas.associateBy { it.id }
        val checklist = snapshot.checklist.groupBy { it.taskId }
        val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d", locale)
        val timeFormat = DateTimeFormatter.ofPattern("HH:mm", locale)
        val completionFormat = DateTimeFormatter.ofPattern("M/d", locale)
        fun warn(id: String, field: String, message: String) { warnings += ReadWarning(id, field, message) }
        fun schedule(t: TaskRecord): ScheduleModel? {
            val time = t.reminderTime?.let { raw ->
                try {
                    require(raw in 0L..Int.MAX_VALUE.toLong())
                    ThingsDate.unpackTime(raw.toInt()).format(timeFormat)
                } catch (_: IllegalArgumentException) {
                    warn(t.id, "reminderTime", "Invalid packed reminder; raw value preserved."); null
                } catch (_: DateTimeException) {
                    warn(t.id, "reminderTime", "Invalid reminder time; raw value preserved."); null
                }
            }
            if (t.start == policy.somedayStart) return ScheduleModel(ScheduleKind.Someday, "Someday", time)
            val day = lists.startDates[t.id]
            if (day == null) {
                if (time != null) warn(t.id, "reminderTime", "Reminder has no readable start date.")
                return null
            }
            val kind = when {
                day == today && t.startBucket == policy.eveningBucket -> ScheduleKind.Evening
                day == today -> ScheduleKind.Today
                else -> ScheduleKind.Date
            }
            val label = when (kind) {
                ScheduleKind.Today -> "Today"
                ScheduleKind.Evening -> "This Evening"
                else -> day.format(dateFormat)
            }
            return ScheduleModel(kind, label, time)
        }
        fun deadline(t: TaskRecord): DeadlineModel? {
            val date = lists.deadlineDates[t.id] ?: return null
            val days = ChronoUnit.DAYS.between(today, date)
            val remaining = when {
                days == 0L -> "Today"
                days == 1L -> "1 day left"
                days > 1L -> "$days days left"
                days == -1L -> "1 day overdue"
                else -> "${-days} days overdue"
            }
            return DeadlineModel(date.format(dateFormat), remaining)
        }
        fun tags(id: String) = snapshot.tagsForTask(id).mapNotNull { it.title }
        fun completion(t: TaskRecord): String {
            val raw = t.stopDate
            if (raw == null || !raw.isFinite()) {
                warn(t.id, "stopDate", "Completed task has no valid completion timestamp."); return ""
            }
            return try {
                val seconds = floor(raw)
                require(seconds in -62135596800.0..253402300799.0)
                val nanos = ((raw - seconds) * 1_000_000_000.0).toLong().coerceIn(0L, 999_999_999L)
                Instant.ofEpochSecond(seconds.toLong(), nanos).atZone(zone).toLocalDate().format(completionFormat)
            } catch (_: IllegalArgumentException) {
                warn(t.id, "stopDate", "Invalid completion timestamp; raw value preserved."); ""
            } catch (_: DateTimeException) {
                warn(t.id, "stopDate", "Unreadable completion timestamp; raw value preserved."); ""
            }
        }
        val models = snapshot.tasks.filter {
            it.knownType == DbTaskType.Todo && it.trashed == 0L && it.id !in blocked
        }.associate { t ->
            val taskTags = tags(t.id)
            val items = checklist[t.id].orEmpty().map { item ->
                if (item.status != policy.openStatus && item.status != policy.completedStatus) warn(item.id, "status", "Unknown checklist status; raw value preserved.")
                ChecklistItemModel(item.id, item.title.orEmpty(), item.status == policy.completedStatus)
            }
            val parent = lists.projectOwners[t.id]?.let { byId[it] }
            t.id to TaskRowModel(
                id = t.id, title = t.title.orEmpty(), subtitle = parent?.title ?: t.areaId?.let { areasById[it]?.title },
                checked = t.status == policy.completedStatus, isRecurring = !t.repeatingTemplateId.isNullOrEmpty(),
                isRepeatingTemplate = t.id in templateIds, hasReminder = t.reminderTime != null,
                hasTag = taskTags.isNotEmpty(), hasNote = !t.notes.isNullOrBlank(), hasChecklist = items.isNotEmpty(),
                isToday = t.id in todayIds,
                details = TaskDetailsModel(notes = t.notes.orEmpty(), checklist = items, schedule = schedule(t),
                    deadline = deadline(t), repeat = null, tags = taskTags),
            )
        }
        val projects = lists.projectSections.mapValues { (id, section) ->
            val p = byId.getValue(id)
            val headings = snapshot.headingsInProject(id).filter { it.trashed == 0L && it.status == policy.openStatus }
            val headingIds = headings.map { it.id }.toSet()
            val total = section.active.size + section.completed.size
            ProjectUiState(
                id = id, title = p.title.orEmpty(), notes = p.notes.orEmpty(),
                progress = if (total == 0) 0f else section.completed.size.toFloat() / total,
                tasks = section.active.filter { it.headingId !in headingIds }.mapNotNull { models[it.id] },
                headings = headings.map { h -> ProjectHeadingModel(h.id, h.title.orEmpty(), section.active.filter { it.headingId == h.id }.mapNotNull { models[it.id] }) },
                laterTasks = section.later.mapNotNull { models[it.id] },
                loggedTasks = section.completed.map { t -> LoggedTaskModel(t.id, t.title.orEmpty(), completion(t),
                    !t.repeatingTemplateId.isNullOrEmpty(), checklist[t.id].orEmpty().isNotEmpty()) },
                tags = tags(id), schedule = schedule(p), deadline = deadline(p), repeat = null,
            )
        }
        fun homeProject(p: ProjectUiState) = Project(p.id, p.title, p.progress)
        val visibleProjects = projects.filterKeys { it in lists.activeProjectIds }
        val home = HomeUiState(
            inboxCount = lists.inbox.count { it.knownType == DbTaskType.Todo },
            todayCount = (lists.today + lists.evening).filter { it.knownType == DbTaskType.Todo }.map { it.id }.distinct().size,
            todayBadge = 0,
            looseProjects = snapshot.projectsInArea(null).mapNotNull { visibleProjects[it.id] }.map(::homeProject),
            areas = snapshot.areas.map { a -> Area(a.id, a.title.orEmpty(), snapshot.projectsInArea(a.id).mapNotNull { visibleProjects[it.id] }.map(::homeProject)) },
        )
        snapshot.taskTags.forEach { link ->
            if (link.taskId !in byId || snapshot.tags.none { it.id == link.tagId }) warn(link.taskId, "tags", "Tag link has a missing endpoint.")
        }
        snapshot.areaTags.forEach { link ->
            if (link.areaId !in areasById || snapshot.tags.none { it.id == link.tagId }) warn(link.areaId, "tags", "Area tag link has a missing endpoint.")
        }
        snapshot.checklist.forEach { if (it.taskId !in byId) warn(it.id, "task", "Checklist item points to a missing task.") }
        snapshot.tasks.forEach {
            if (it.knownType == null) warn(it.id, "type", "Unknown entity type; retained in raw snapshot.")
            it.areaId?.let { area -> if (area !in areasById) warn(it.id, "area", "Missing area reference.") }
        }
        return ThingsUiRead(snapshot, home, projects, models, warnings.toList(), lists)
    }
}

suspend fun ThingsReadRepository.readUi(reader: ThingsUiReader = ThingsUiReader()): ThingsUiRead {
    val snapshot = readSnapshot()
    return withContext(Dispatchers.Default) { reader.map(snapshot) }
}
