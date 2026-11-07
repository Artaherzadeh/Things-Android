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
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor

/** Local conventions, not a claim that all source database enums are verified. */
data class LocalReadPolicy(
    val openStatus: Long = 0,
    val completedStatus: Long = 3,
    val inboxStart: Long = 0,
    val somedayStart: Long = 2,
    val eveningBucket: Long = 1,
)
data class ReadWarning(val entityId: String, val field: String, val message: String)
data class ThingsUiRead(
    val snapshot: ThingsSnapshot,
    val home: HomeUiState,
    val projects: Map<String, ProjectUiState>,
    val tasks: Map<String, TaskRowModel>,
    val warnings: List<ReadWarning>,
)

class ThingsUiReader(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = clock.zone,
    private val locale: Locale = Locale.getDefault(),
    private val policy: LocalReadPolicy = LocalReadPolicy(),
) {
    fun map(snapshot: ThingsSnapshot): ThingsUiRead =
        Mapping(snapshot, clock, zone, locale, policy).read()
}

private class Mapping(
    private val data: ThingsSnapshot,
    clock: Clock,
    private val zone: ZoneId,
    locale: Locale,
    private val policy: LocalReadPolicy,
) {
    private val today = LocalDate.now(clock.withZone(zone))
    private val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d", locale)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val completionFormat = DateTimeFormatter.ofPattern("M/d", locale)
    private val warnings = linkedSetOf<ReadWarning>()
    private val byId = data.tasks.associateBy { it.id }
    private val areasById = data.areas.associateBy { it.id }
    private val tagsById = data.tags.associateBy { it.id }
    private val checklistByTask = data.checklist.groupBy { it.taskId }
    private val dateCache = mutableMapOf<String, LocalDate?>()
    private fun warn(id: String, field: String, message: String) {
        warnings += ReadWarning(id, field, message)
    }
    private fun visible(t: TaskRecord): Boolean = when (t.trashed) {
        0L -> true
        1L -> false
        else -> {
            warn(t.id, "trashed", "Unknown trash state; omitted from visible UI.")
            false
        }
    }
    private fun template(t: TaskRecord) =
        t.recurrenceRule?.isNotEmpty() == true || t.row.blob("repeater")?.isNotEmpty() == true

    private fun date(t: TaskRecord, field: String, raw: Long?): LocalDate? =
        dateCache.getOrPut("${t.id}:$field") {
            if (raw == null || raw == 0L) null else {
                try {
                    require(raw in Int.MIN_VALUE.toLong()..0xFFFFFFFFL)
                    ThingsDate.unpackDate(raw.toInt())
                } catch (_: IllegalArgumentException) {
                    warn(t.id, field, "Invalid packed date; raw value preserved.")
                    null
                } catch (_: DateTimeException) {
                    warn(t.id, field, "Invalid calendar date; raw value preserved.")
                    null
                }
            }
        }
    private fun start(t: TaskRecord) = date(t, "startDate", t.startDate)
    private fun reminder(t: TaskRecord): LocalTime? {
        val raw = t.reminderTime ?: return null
        return try {
            require(raw in 0L..Int.MAX_VALUE.toLong())
            ThingsDate.unpackTime(raw.toInt())
        } catch (_: IllegalArgumentException) {
            warn(t.id, "reminderTime", "Invalid packed reminder; raw value preserved.")
            null
        } catch (_: DateTimeException) {
            warn(t.id, "reminderTime", "Invalid reminder time; raw value preserved.")
            null
        }
    }
    private fun today(t: TaskRecord): Boolean =
        t.start != policy.somedayStart && start(t)?.let { !it.isAfter(today) } == true
    private fun later(t: TaskRecord) = template(t) || start(t)?.isAfter(today) == true
    private fun schedule(t: TaskRecord): ScheduleModel? {
        val day = start(t)
        val time = reminder(t)?.format(timeFormat)
        if (t.start == policy.somedayStart) return ScheduleModel(ScheduleKind.Someday, "Someday", time)
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
    private fun deadline(t: TaskRecord): DeadlineModel? {
        val day = date(t, "deadline", t.deadline) ?: return null
        val days = ChronoUnit.DAYS.between(today, day)
        val left = when {
            days == 0L -> "Today"
            days == 1L -> "1 day left"
            days > 1L -> "$days days left"
            days == -1L -> "1 day overdue"
            else -> "${-days} days overdue"
        }
        return DeadlineModel(day.format(dateFormat), left)
    }
    private fun subtitle(t: TaskRecord): String? {
        val p = t.projectId?.let { byId[it] }
            ?: t.headingId?.let { byId[it]?.projectId }?.let { byId[it] }
        return p?.takeIf { it.knownType == DbTaskType.Project }?.title
            ?: t.areaId?.let { areasById[it]?.title }
    }
    private fun tags(id: String) = data.tagsForTask(id).mapNotNull { it.title }
    private fun task(t: TaskRecord): TaskRowModel {
        val tags = tags(t.id)
        val items = checklistByTask[t.id].orEmpty().map {
            if (it.status != policy.openStatus && it.status != policy.completedStatus) {
                warn(it.id, "status", "Unknown checklist status; raw value preserved.")
            }
            ChecklistItemModel(it.id, it.title.orEmpty(), it.status == policy.completedStatus)
        }
        if (template(t)) warn(t.id, "repeat", "Recurrence blob preserved; description not decoded.")
        return TaskRowModel(
            id = t.id, title = t.title.orEmpty(), subtitle = subtitle(t),
            checked = t.status == policy.completedStatus,
            isRecurring = !t.repeatingTemplateId.isNullOrEmpty(),
            isRepeatingTemplate = template(t), hasReminder = t.reminderTime != null,
            hasTag = tags.isNotEmpty(), hasNote = !t.notes.isNullOrBlank(),
            hasChecklist = items.isNotEmpty(), isToday = today(t),
            details = TaskDetailsModel(
                notes = t.notes.orEmpty(), checklist = items, schedule = schedule(t),
                deadline = deadline(t), repeat = null, tags = tags,
            ),
        )
    }
    private fun completion(t: TaskRecord): String {
        val raw = t.stopDate
        if (raw == null || !raw.isFinite()) {
            warn(t.id, "stopDate", "Completed task has no valid completion timestamp.")
            return ""
        }
        return try {
            val seconds = floor(raw)
            require(seconds >= -62135596800.0 && seconds <= 253402300799.0)
            val nanos = ((raw - seconds) * 1_000_000_000.0).toLong().coerceIn(0L, 999_999_999L)
            Instant.ofEpochSecond(seconds.toLong(), nanos).atZone(zone).toLocalDate().format(completionFormat)
        } catch (_: IllegalArgumentException) {
            warn(t.id, "stopDate", "Invalid completion timestamp; raw value preserved.")
            ""
        } catch (_: DateTimeException) {
            warn(t.id, "stopDate", "Unreadable completion timestamp; raw value preserved.")
            ""
        }
    }
    private fun project(p: TaskRecord, models: Map<String, TaskRowModel>): ProjectUiState {
        val headings = data.headingsInProject(p.id).filter { visible(it) && it.status == policy.openStatus }
        val headingIds = headings.map { it.id }.toSet()
        val children = data.tasksInProject(p.id).filter(::visible)
        val open = children.filter { it.status == policy.openStatus }
        val active = open.filterNot(::later)
        val done = children.filter { it.status == policy.completedStatus }
        val total = active.size + done.size
        if (template(p)) warn(p.id, "repeat", "Project recurrence blob preserved; description not decoded.")
        return ProjectUiState(
            id = p.id, title = p.title.orEmpty(),
            progress = if (total == 0) 0f else done.size.toFloat() / total,
            notes = p.notes.orEmpty(),
            tasks = active.filter { it.headingId !in headingIds }.mapNotNull { models[it.id] },
            headings = headings.map { h ->
                ProjectHeadingModel(h.id, h.title.orEmpty(), active.filter { it.headingId == h.id }.mapNotNull { models[it.id] })
            },
            laterTasks = open.filter(::later).mapNotNull { models[it.id] },
            loggedTasks = done.sortedWith(compareByDescending<TaskRecord> { it.stopDate }.thenBy { it.id }).map {
                LoggedTaskModel(it.id, it.title.orEmpty(), completion(it),
                    !it.repeatingTemplateId.isNullOrEmpty(), checklistByTask[it.id].orEmpty().isNotEmpty())
            },
            tags = tags(p.id), schedule = schedule(p), deadline = deadline(p), repeat = null,
        )
    }
    private fun validate() {
        data.tasks.forEach { t ->
            if (t.knownType == null) warn(t.id, "type", "Unknown entity type; retained in raw snapshot.")
            if (t.status != policy.openStatus && t.status != policy.completedStatus) warn(t.id, "status", "Unknown status; not classified as open or completed.")
            t.projectId?.let {
                if (byId[it]?.knownType != DbTaskType.Project) warn(t.id, "project", "Missing or invalid project reference.")
            }
            t.headingId?.let {
                val h = byId[it]
                if (h?.knownType != DbTaskType.Heading) warn(t.id, "heading", "Missing or invalid heading reference.")
                else if (t.projectId != null && h.projectId != t.projectId) warn(t.id, "heading", "Heading belongs to another project.")
            }
            t.areaId?.let { if (it !in areasById) warn(t.id, "area", "Missing area reference.") }
        }
        data.taskTags.forEach {
            if (it.taskId !in byId || it.tagId !in tagsById) warn(it.taskId, "tags", "Tag link has a missing endpoint.")
        }
        data.areaTags.forEach {
            if (it.areaId !in areasById || it.tagId !in tagsById) warn(it.areaId, "tags", "Area tag link has a missing endpoint.")
        }
        data.checklist.forEach {
            if (it.taskId !in byId) warn(it.id, "task", "Checklist item points to a missing task.")
        }
    }
    fun read(): ThingsUiRead {
        validate()
        val models = data.tasks.filter { it.knownType == DbTaskType.Todo && visible(it) }.associate { it.id to task(it) }
        val projects = data.tasks.filter {
            it.knownType == DbTaskType.Project && visible(it) && it.status == policy.openStatus
        }.associate { it.id to project(it, models) }
        fun homeProject(p: ProjectUiState) = Project(p.id, p.title, p.progress)
        val areas = data.areas.map { a ->
            Area(a.id, a.title.orEmpty(), data.projectsInArea(a.id).mapNotNull { projects[it.id] }.map(::homeProject))
        }
        val open = data.tasks.filter {
            it.knownType == DbTaskType.Todo && visible(it) && it.status == policy.openStatus && !template(it)
        }
        val home = HomeUiState(
            inboxCount = open.count { it.start == policy.inboxStart },
            todayCount = open.count { today(it) }, todayBadge = 0,
            looseProjects = data.projectsInArea(null).mapNotNull { projects[it.id] }.map(::homeProject),
            areas = areas,
        )
        return ThingsUiRead(data, home, projects, models, warnings.toList())
    }
}

suspend fun ThingsReadRepository.readUi(reader: ThingsUiReader = ThingsUiReader()): ThingsUiRead {
    val snapshot = readSnapshot()
    return withContext(Dispatchers.Default) { reader.map(snapshot) }
}
