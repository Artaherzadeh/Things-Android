package com.things.app.data.db

/** Opaque database payloads are retained verbatim, not treated as decoded rules. */
data class StoredRepeatPayload(
    val legacyRule: DbValue.Blob?,
    val repeater: DbValue.Blob?,
) {
    val hasRuleBytes: Boolean
        get() = legacyRule?.bytes()?.isNotEmpty() == true ||
            repeater?.bytes()?.isNotEmpty() == true
}

data class StoredRepeatTemplate(
    val task: TaskRecord,
    val payload: StoredRepeatPayload,
    val instanceCreationStartDate: Long?,
    val instanceCreationPaused: Long?,
    val instanceCreationCount: Long?,
    val afterCompletionReferenceDate: Long?,
    val nextInstanceStartDate: Long?,
    val migrationDate: Double?,
)

data class StoredRepeatInstance(
    val task: TaskRecord,
    val templateId: String,
)

data class RepeatStorageState(
    val templates: Map<String, StoredRepeatTemplate>,
    val instances: List<StoredRepeatInstance>,
    val unresolvedInstances: List<StoredRepeatInstance>,
    val warnings: List<ReadWarning>,
) {
    /** Read order only. This does not infer recurrence dates from creation timestamps. */
    fun instancesOf(templateId: String): List<StoredRepeatInstance> =
        instances.filter { it.templateId == templateId }
            .sortedWith(compareBy<StoredRepeatInstance> { it.task.creationDate }.thenBy { it.task.id })
}

/**
 * Audits recurrence storage without decoding undocumented plist fields.
 * Includes trashed and completed rows: UI visibility must not erase history.
 * Does not write the database, generate copies, or infer a recurrence mode.
 */
class RepeatStorageReader {
    fun read(snapshot: ThingsSnapshot): RepeatStorageState {
        val byId = snapshot.tasks.associateBy { it.id }
        val warnings = linkedSetOf<ReadWarning>()
        val templates = linkedMapOf<String, StoredRepeatTemplate>()
        val instances = mutableListOf<StoredRepeatInstance>()
        val unresolved = mutableListOf<StoredRepeatInstance>()
        snapshot.tasks.forEach { task ->
            val legacy = task.row.blob("rt1_recurrenceRule")
            val modern = task.row.blob("repeater")
            val payload = StoredRepeatPayload(legacy?.let(DbValue::Blob), modern?.let(DbValue::Blob))
            if (legacy != null || modern != null) {
                templates[task.id] = StoredRepeatTemplate(
                    task = task,
                    payload = payload,
                    instanceCreationStartDate = task.row.integer("rt1_instanceCreationStartDate"),
                    instanceCreationPaused = task.row.integer("rt1_instanceCreationPaused"),
                    instanceCreationCount = task.row.integer("rt1_instanceCreationCount"),
                    afterCompletionReferenceDate = task.row.integer("rt1_afterCompletionReferenceDate"),
                    nextInstanceStartDate = task.row.integer("rt1_nextInstanceStartDate"),
                    migrationDate = task.row.real("repeaterMigrationDate"),
                )
                if (!payload.hasRuleBytes) {
                    warnings += ReadWarning(task.id, "repeat", "Empty recurrence payload; not a usable decoded rule.")
                }
                if (task.knownType != DbTaskType.Todo && task.knownType != DbTaskType.Project) {
                    warnings += ReadWarning(task.id, "type", "Recurrence payload is attached to an unsupported entity type.")
                }
            }
            val reference = task.repeatingTemplateId
            if (reference != null) {
                if (reference.isEmpty()) {
                    warnings += ReadWarning(task.id, "rt1_repeatingTemplate", "Empty template reference is preserved in the raw row.")
                } else {
                    instances += StoredRepeatInstance(task, reference)
                }
            }
        }
        instances.forEach { instance ->
            val parent = byId[instance.templateId]
            val stored = templates[instance.templateId]
            val reason = when {
                instance.templateId == instance.task.id -> "Instance references itself."
                parent == null -> "Referenced template is missing."
                stored == null || !stored.payload.hasRuleBytes -> "Referenced row has no usable recurrence payload."
                parent.knownType != DbTaskType.Todo && parent.knownType != DbTaskType.Project -> "Referenced template type is unsupported."
                instance.task.knownType != parent.knownType -> "Instance and template entity types do not match."
                else -> null
            }
            if (reason != null) {
                unresolved += instance
                warnings += ReadWarning(instance.task.id, "rt1_repeatingTemplate", reason)
            }
        }
        return RepeatStorageState(templates.toMap(), instances.toList(), unresolved.toList(), warnings.toList())
    }
}
