package com.things.app.data.db

sealed interface DbValue {
    data object Null : DbValue
    data class Integer(val value: Long) : DbValue
    data class Real(val value: Double) : DbValue
    data class Text(val value: String) : DbValue
    class Blob(bytes: ByteArray) : DbValue {
        private val content = bytes.copyOf()
        fun bytes(): ByteArray = content.copyOf()
        override fun equals(other: Any?) = other is Blob && content.contentEquals(other.content)
        override fun hashCode() = content.contentHashCode()
        override fun toString() = "Blob(${content.size} bytes)"
    }
}

class DbRecord(values: Map<String, DbValue>) {
    val columns: Map<String, DbValue> = values.toMap()
    fun value(column: String): DbValue = columns[column] ?: error("Missing database column: $column")
    fun text(column: String): String? = when (val v = value(column)) {
        DbValue.Null -> null
        is DbValue.Text -> v.value
        else -> error("Expected TEXT for $column")
    }
    fun integer(column: String): Long? = when (val v = value(column)) {
        DbValue.Null -> null
        is DbValue.Integer -> v.value
        else -> error("Expected INTEGER for $column")
    }
    fun real(column: String): Double? = when (val v = value(column)) {
        DbValue.Null -> null
        is DbValue.Real -> v.value
        is DbValue.Integer -> v.value.toDouble()
        else -> error("Expected numeric value for $column")
    }
    fun blob(column: String): ByteArray? = when (val v = value(column)) {
        DbValue.Null -> null
        is DbValue.Blob -> v.bytes()
        else -> error("Expected BLOB for $column")
    }
    fun requiredText(column: String): String = requireNotNull(text(column)) { "$column must not be NULL" }
}

enum class DbTaskType(val raw: Long) {
    Todo(0), Project(1), Heading(2);
    companion object {
        fun fromRaw(raw: Long?): DbTaskType? = entries.firstOrNull { it.raw == raw }
    }
}

data class TaskRecord(val row: DbRecord) {
    val id get() = row.requiredText("uuid")
    val type get() = row.integer("type")
    val knownType get() = DbTaskType.fromRaw(type)
    val status get() = row.integer("status")
    val trashed get() = row.integer("trashed")
    val title get() = row.text("title")
    val notes get() = row.text("notes")
    val creationDate get() = row.real("creationDate")
    val modificationDate get() = row.real("userModificationDate")
    val stopDate get() = row.real("stopDate")
    val start get() = row.integer("start")
    val startDate get() = row.integer("startDate")
    val startBucket get() = row.integer("startBucket")
    val reminderTime get() = row.integer("reminderTime")
    val deadline get() = row.integer("deadline")
    val position get() = row.integer("index")
    val todayPosition get() = row.integer("todayIndex")
    val areaId get() = row.text("area")
    val projectId get() = row.text("project")
    val headingId get() = row.text("heading")
    val repeatingTemplateId get() = row.text("rt1_repeatingTemplate")
    val recurrenceRule get() = row.blob("rt1_recurrenceRule")
}

data class AreaRecord(val row: DbRecord) {
    val id get() = row.requiredText("uuid")
    val title get() = row.text("title")
    val visible get() = row.integer("visible")
    val position get() = row.integer("index")
}

data class TagRecord(val row: DbRecord) {
    val id get() = row.requiredText("uuid")
    val title get() = row.text("title")
    val parentId get() = row.text("parent")
    val position get() = row.integer("index")
}

data class ChecklistRecord(val row: DbRecord) {
    val id get() = row.requiredText("uuid")
    val taskId get() = row.text("task")
    val title get() = row.text("title")
    val status get() = row.integer("status")
    val position get() = row.integer("index")
}

data class TaskTagLink(val taskId: String, val tagId: String)
data class AreaTagLink(val areaId: String, val tagId: String)

data class ThingsSnapshot(
    val tasks: List<TaskRecord>,
    val areas: List<AreaRecord>,
    val tags: List<TagRecord>,
    val checklist: List<ChecklistRecord>,
    val taskTags: List<TaskTagLink>,
    val areaTags: List<AreaTagLink>,
) {
    fun projectsInArea(areaId: String?) = tasks.filter {
        it.knownType == DbTaskType.Project && it.areaId == areaId
    }
    fun headingsInProject(projectId: String) = tasks.filter {
        it.knownType == DbTaskType.Heading && it.projectId == projectId
    }
    fun tasksInProject(projectId: String): List<TaskRecord> {
        val headingIds = headingsInProject(projectId).map { it.id }.toSet()
        return tasks.filter {
            it.knownType == DbTaskType.Todo &&
                (it.projectId == projectId || it.headingId in headingIds)
        }
    }
    fun tasksInHeading(headingId: String) = tasks.filter {
        it.knownType == DbTaskType.Todo && it.headingId == headingId
    }
    fun checklistFor(taskId: String) = checklist.filter { it.taskId == taskId }
    fun tagsForTask(taskId: String): List<TagRecord> {
        val ids = taskTags.filter { it.taskId == taskId }.map { it.tagId }.toSet()
        return tags.filter { it.id in ids }
    }
    fun tagsForArea(areaId: String): List<TagRecord> {
        val ids = areaTags.filter { it.areaId == areaId }.map { it.tagId }.toSet()
        return tags.filter { it.id in ids }
    }
}
