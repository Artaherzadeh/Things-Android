import type { AppData, Area, ChecklistItem, Project, Section, Task } from '../types.ts';
import {
    ThingsCloudClient,
    ThingsCloudError,
    isThingsCloudConflictError,
} from './things-cloud-client.ts';
import {
    mapThingsAreaToEntity,
    mapThingsChecklistToEntity,
    mapThingsTaskToEntity,
    mergeThingsAreaIntoExisting,
    mergeThingsProjectIntoExisting,
    mergeThingsTaskIntoExisting,
    mindwtrAreaToThingsWire,
    mindwtrChecklistToThingsWire,
    mindwtrProjectToThingsWire,
    mindwtrSectionToThingsWire,
    mindwtrTagToThingsWire,
    mindwtrTaskToThingsWire,
    sameDay,
    thingsDeleteWire,
    thingsStateKeyForProject,
    thingsStateKeyForTask,
    thingsTaskPropsWithExisting,
} from './things-cloud-mapper.ts';
import {
    ThingsItemAction,
    type ThingsAreaWireProps,
    type ThingsChecklistWireProps,
    type ThingsItemsPageResponse,
    type ThingsTagWireProps,
    type ThingsTaskWireProps,
    type ThingsWireObject,
} from './things-cloud-types.ts';
import { generateThingsId } from './things-id.ts';
import {
    ThingsTagRegistry,
    dedupeTagNames,
    sameTagSet,
    tagKey,
    tagNameToTitle,
    tagTitleToName,
    type ThingsTagState,
} from './things-tags.ts';

export interface ThingsSyncResult {
    data: AppData;
    newServerIndex: number;
    pulledChangesCount: number;
    pushedChangesCount: number;
    /** How many times the push was rejected as stale and redone after a fresh pull. */
    conflictRetries: number;
    /** Most recent wire record seen per id. Used to reconcile an interrupted commit without replaying it blindly. */
    receivedChanges: Record<string, ThingsWireObject>;
    /** Original history commits, preserving the outer commit boundary for uncertain-write reconciliation. */
    receivedBatches: Array<Record<string, ThingsWireObject>>;
    wireRecords: Record<string, ThingsWireObject>;
}

/** A same-entity edit on both sides must not be silently resolved by pull order. */
export type ThingsCloudConflictEntity = Task | Project | Section | Area;
export type ThingsCloudEntityCollection = 'tasks' | 'projects' | 'sections' | 'areas';
export interface ThingsCloudConcurrentEntityConflict {
    id: string;
    collection: ThingsCloudEntityCollection;
    local: ThingsCloudConflictEntity | null;
    remote: ThingsCloudConflictEntity | null;
}

export class ThingsCloudConcurrentEditError extends ThingsCloudError {
    readonly entityIds: string[];
    readonly conflicts: ThingsCloudConcurrentEntityConflict[];
    readonly remoteData: AppData;
    readonly newServerIndex: number;
    readonly wireRecords: Record<string, ThingsWireObject>;
    readonly receivedChanges: Record<string, ThingsWireObject>;
    readonly receivedBatches: Array<Record<string, ThingsWireObject>>;

    constructor(input: {
        conflicts: ThingsCloudConcurrentEntityConflict[];
        remoteData: AppData;
        newServerIndex: number;
        wireRecords: Record<string, ThingsWireObject>;
        receivedChanges: Record<string, ThingsWireObject>;
        receivedBatches: Array<Record<string, ThingsWireObject>>;
    }) {
        super('The same item changed locally and in Things Cloud. Both versions were saved; resolve the conflict before syncing again.');
        this.name = 'ThingsCloudConcurrentEditError';
        this.conflicts = input.conflicts;
        this.entityIds = input.conflicts.map((conflict) => conflict.id);
        this.remoteData = input.remoteData;
        this.newServerIndex = input.newServerIndex;
        this.wireRecords = input.wireRecords;
        this.receivedChanges = input.receivedChanges;
        this.receivedBatches = input.receivedBatches;
    }
}

export interface ThingsSyncOptions {
    /** Extra pull+push attempts allowed after a commit conflict. Default 3. */
    maxConflictRetries?: number;
    /** Safety cap on pages fetched per pull. Default 10,000. */
    maxPullPages?: number;
    onCommitPrepared?: (changes: Record<string, ThingsWireObject>, ancestorIndex: number) => Promise<void>;
    onCommitRejected?: (changes: Record<string, ThingsWireObject>, ancestorIndex: number) => Promise<void>;
}

export interface ThingsPushOptions {
    /**
     * Ids the latest pull just wrote locally. Deletion-related changes (trash,
     * restore, permanent delete) are not sent back for these ids: the server
     * already has that state.
     */
    skipIds?: ReadonlySet<string>;
    onCommitPrepared?: (changes: Record<string, ThingsWireObject>, ancestorIndex: number) => Promise<void>;
    onCommitRejected?: (changes: Record<string, ThingsWireObject>, ancestorIndex: number) => Promise<void>;
}

export const DEFAULT_MAX_CONFLICT_RETRIES = 3;
export const DEFAULT_MAX_PULL_PAGES = 10_000;

const NO_IDS: ReadonlySet<string> = new Set();

const wireValueMatches = (expected: unknown, observed: unknown): boolean => {
    if (expected === null || typeof expected !== 'object') return stableValue(expected) === stableValue(observed);
    if (Array.isArray(expected)) return Array.isArray(observed) && stableValue(expected) === stableValue(observed);
    if (!observed || typeof observed !== 'object' || Array.isArray(observed)) return false;
    return Object.entries(expected as Record<string, unknown>).every(([key, value]) =>
        Object.prototype.hasOwnProperty.call(observed, key)
        && wireValueMatches(value, (observed as Record<string, unknown>)[key]));
};

const wireFamilyMatches = (expected: string, observed: string): boolean => expected === observed
    || (expected.startsWith('Task') && observed.startsWith('Task'))
    || (expected.startsWith('Area') && observed.startsWith('Area'))
    || (expected.startsWith('Tag') && observed.startsWith('Tag'))
    || (expected.startsWith('ChecklistItem') && observed.startsWith('ChecklistItem'));

/** True when one complete history commit confirms every prepared operation. */
export function historyBatchConfirmsChanges(
    changes: Record<string, ThingsWireObject>,
    batch: Record<string, ThingsWireObject>,
): boolean {
    return Object.entries(changes).every(([id, expected]) => {
        const observed = batch[id];
        if (observed && expected.t === observed.t && wireFamilyMatches(expected.e, observed.e)
            && wireValueMatches(expected.p, observed.p)) return true;
        if (expected.t === ThingsItemAction.Deleted) {
            return Object.values(batch).some((item) =>
                (item.e === 'Tombstone' || item.e === 'Tombstone2') && item.p.dloid === id);
        }
        return false;
    });
}

function stableValue(value: unknown): string {
    if (Array.isArray(value)) return `[${value.map(stableValue).join(',')}]`;
    if (value && typeof value === 'object') {
        const entries = Object.entries(value as Record<string, unknown>).sort(([a], [b]) => a.localeCompare(b));
        return `{${entries.map(([key, item]) => `${JSON.stringify(key)}:${stableValue(item)}`).join(',')}}`;
    }
    return JSON.stringify(value);
}

function entitiesById(data: AppData | null): Map<string, { collection: ThingsCloudEntityCollection; entity: ThingsCloudConflictEntity }> {
    const entities = new Map<string, { collection: ThingsCloudEntityCollection; entity: ThingsCloudConflictEntity }>();
    if (!data) return entities;
    const collections: Array<[ThingsCloudEntityCollection, Array<Task | Project | Section | Area>]> = [
        ['tasks', data.tasks],
        ['projects', data.projects],
        ['sections', data.sections],
        ['areas', data.areas],
    ];
    for (const [collection, rows] of collections) {
        for (const entity of rows ?? []) entities.set(entity.id, { collection, entity });
    }
    return entities;
}

function localChangesSince(data: AppData, baseline: AppData | null): Set<string> {
    if (!baseline) return NO_IDS as Set<string>;
    const current = entitiesById(data);
    const previous = entitiesById(baseline);
    const ids = new Set([...current.keys(), ...previous.keys()]);
    return new Set([...ids].filter((id) => stableValue(current.get(id)) !== stableValue(previous.get(id))));
}

/**
 * True only when the server explicitly says this page reaches the end of the
 * history. Missing or non-numeric size fields mean "unknown", not "done":
 * the caller then keeps paging until the server returns an empty page.
 */
function reportsCaughtUp(page: ThingsItemsPageResponse): boolean {
    const end = page['end-total-content-size'];
    const latest = page['latest-total-content-size'];
    return (
        typeof end === 'number'
        && typeof latest === 'number'
        && Number.isFinite(end)
        && Number.isFinite(latest)
        && end >= latest
    );
}

/**
 * Things Cloud Sync Engine:
 * Implements direct incremental synchronization with Things Cloud, combining:
 * 1. Direct HTTPS client protocol from things3-cloud.
 * 2. Semantic change detection, incremental paging, and local SQLite/State mirroring from things-cloud-mcp.
 */
export class ThingsCloudSyncEngine {
    private client: ThingsCloudClient;
    private tags: ThingsTagRegistry;
    private wireRecords: Record<string, ThingsWireObject>;

    /**
     * @param tagState Tag registry saved by a previous sync (see `tagState`). Without
     * it, tags created before this sync cannot be resolved to names.
     */
    constructor(
        client: ThingsCloudClient,
        tagState?: ThingsTagState | null,
        wireRecords?: Record<string, ThingsWireObject> | null,
    ) {
        this.client = client;
        this.tags = new ThingsTagRegistry(tagState);
        this.wireRecords = { ...(wireRecords ?? {}) };
    }

    /** Tag uuid <-> title memory to persist after a successful sync. */
    get tagState(): ThingsTagState {
        return this.tags.snapshot();
    }

    private tagUuidsFor(ownerId: string, tags: readonly string[] | undefined): string[] {
        return this.tags.uuidsFor(ownerId, tags ?? [], generateThingsId);
    }

    /**
     * Pulls latest incremental changes from Things Cloud and merges them into local data.
     *
     * `touchedIds` lists every uuid the server mentioned (created, modified or
     * deleted); push uses it to avoid echoing server-side deletions back.
     */
    async pull(
        currentData: AppData,
        startIndex: number = 0,
        options: { maxPages?: number } = {}
    ): Promise<{
        data: AppData;
        newServerIndex: number;
        changeCount: number;
        touchedIds: Set<string>;
        receivedChanges: Record<string, ThingsWireObject>;
                receivedBatches: Array<Record<string, ThingsWireObject>>;
        wireRecords: Record<string, ThingsWireObject>;
    }> {
        // Clone current state maps for fast lookup and mutation
        const taskMap = new Map<string, Task>(currentData.tasks.map(t => [t.id, { ...t }]));
        const projectMap = new Map<string, Project>(currentData.projects.map(p => [p.id, { ...p }]));
        const sectionMap = new Map<string, Section>(currentData.sections.map(s => [s.id, { ...s }]));
        const areaMap = new Map<string, Area>(currentData.areas.map(a => [a.id, { ...a }]));
        const pendingChecklists = new Map<string, Map<string, ChecklistItem>>();
        const touchedIds = new Set<string>();
        const receivedChanges: Record<string, ThingsWireObject> = {};
        const receivedBatches: Array<Record<string, ThingsWireObject>> = [];
        const wireRecords = { ...this.wireRecords };
        // Raw Things tag uuids (`tg`) of every task/project event in this pull;
        // undefined means the event carried no `tg`. Resolved to names after the
        // loop, once every tag entity of this pull is known.
        const rawTagIds = new Map<string, string[] | undefined>();
        const tagRenames: Array<[string, string]> = [];
        const removedTagTitles: string[] = [];

        // A checklist event only describes one item, so start from the task's
        // existing items; otherwise one event would replace the whole checklist.
        const getPendingChecklist = (taskId: string): Map<string, ChecklistItem> => {
            let list = pendingChecklists.get(taskId);
            if (!list) {
                list = new Map<string, ChecklistItem>(
                    (taskMap.get(taskId)?.checklist ?? []).map((item): [string, ChecklistItem] => [item.id, item])
                );
                pendingChecklists.set(taskId, list);
            }
            return list;
        };
        // A delete entry carries no props, so the owning task has to be looked up.
        const findChecklistOwner = (itemId: string): string | undefined => {
            for (const [taskId, list] of pendingChecklists) {
                if (list.has(itemId)) return taskId;
            }
            for (const [taskId, task] of taskMap) {
                if (task.checklist?.some(item => item.id === itemId)) return taskId;
            }
            return undefined;
        };

        let currentIndex = startIndex;
        let totalProcessed = 0;
        let pagesFetched = 0;
        const maxPages = Math.max(1, Math.floor(options.maxPages ?? DEFAULT_MAX_PULL_PAGES));

        while (true) {
            if (pagesFetched >= maxPages) {
                // A server that never returns an empty page would otherwise loop forever.
                // Throw instead of returning a partial pull with an advanced index.
                throw new ThingsCloudError(
                    `Things Cloud pull did not finish after ${maxPages} pages (stopped at index ${currentIndex}).`
                );
            }
            pagesFetched++;
            const page = await this.client.getItemsPage(currentIndex);
            const items = page.items || [];
            if (!Array.isArray(items)) throw new ThingsCloudError('Things Cloud returned an invalid history page.');
            if (items.length === 0) {
                break;
            }

            // `items` is an array of commit entries; each entry may contain
            // multiple entity UUIDs. The reverse-engineered SDK assigns one
            // history index per outer entry and advances by entries.length.
            // Keep this batch intact for commit-outcome reconciliation.
            receivedBatches.push(...items);

            for (const itemEntry of items) {
                if (!itemEntry || typeof itemEntry !== 'object' || Array.isArray(itemEntry)) {
                    throw new ThingsCloudError('Things Cloud returned an invalid history entry; the checkpoint was not advanced.');
                }
                for (const [uuid, wireObj] of Object.entries(itemEntry)) {
                    if (!wireObj || typeof wireObj !== 'object' || ![0, 1, 2].includes(wireObj.t)
                        || !wireObj.p || typeof wireObj.p !== 'object' || Array.isArray(wireObj.p)
                        || !['Task', 'Task3', 'Task4', 'Task6', 'Task7', 'ChecklistItem', 'ChecklistItem2', 'ChecklistItem3',
                            'Area', 'Area2', 'Area3', 'Tag', 'Tag3', 'Tag4', 'Tombstone', 'Tombstone2',
                            'Settings3', 'Settings4', 'Settings5', 'Command'].includes(String(wireObj.e))) {
                        throw new ThingsCloudError('Things Cloud returned an unsupported history item; the checkpoint was not advanced.');
                    }
                    receivedChanges[uuid] = wireObj;
                    const previousWire = wireRecords[uuid];
                    wireRecords[uuid] = {
                        t: wireObj.t,
                        e: wireObj.e,
                        // History modifications may be partial. Keep the latest
                        // raw property bag so fields unknown to Mindwtr survive
                        // a later edit made on Android.
                        p: { ...(previousWire?.p ?? {}), ...(wireObj.p ?? {}) },
                    };
                    totalProcessed++;
                    touchedIds.add(uuid);
                    const action = wireObj.t;
                    const kind = wireObj.e;
                    const props = wireObj.p || {};

                    if (kind === 'Task' || kind === 'Task3' || kind === 'Task4' || kind === 'Task6' || kind === 'Task7') {
                        const taskProps = props as ThingsTaskWireProps;
                        const existingTask = taskMap.get(uuid);
                        const existingProject = projectMap.get(uuid);
                        const existingSection = sectionMap.get(uuid);
                        // "Modified" entries can be patches; complete them from what we have.
                        const effectiveProps = action === ThingsItemAction.Deleted
                            ? taskProps
                            : thingsTaskPropsWithExisting(taskProps, {
                                task: existingTask,
                                project: existingProject,
                                section: existingSection,
                            });
                        const result = mapThingsTaskToEntity(uuid, effectiveProps, action);
                        const rawTg = taskProps.tg;
                        if (result.isDeleted) {
                            const deletedAt = typeof taskProps.md === 'number' && Number.isFinite(taskProps.md)
                                ? new Date(taskProps.md * 1000).toISOString()
                                : new Date().toISOString();
                            const task = taskMap.get(uuid);
                            const project = projectMap.get(uuid);
                            const section = sectionMap.get(uuid);
                            if (task) taskMap.set(uuid, { ...task, deletedAt, purgedAt: deletedAt });
                            if (project) projectMap.set(uuid, { ...project, deletedAt, purgedAt: deletedAt });
                            if (section) sectionMap.set(uuid, { ...section, deletedAt });
                            rawTagIds.delete(uuid);
                            this.tags.clearOwner(uuid);
                        } else if (result.project) {
                            projectMap.set(
                                uuid,
                                existingProject
                                    ? mergeThingsProjectIntoExisting(existingProject, result.project)
                                    : result.project
                            );
                            rawTagIds.set(uuid, Array.isArray(rawTg) ? rawTg : undefined);
                        } else if (result.section) {
                            sectionMap.set(
                                uuid,
                                existingSection
                                    ? { ...existingSection, ...result.section, deletedAt: result.section.deletedAt }
                                    : result.section
                            );
                        } else if (result.task) {
                            rawTagIds.set(uuid, Array.isArray(rawTg) ? rawTg : undefined);
                            // Keep everything Things does not know about (checklist, priority, ...)
                            taskMap.set(
                                uuid,
                                existingTask ? mergeThingsTaskIntoExisting(existingTask, result.task) : result.task
                            );
                        }
                    } else if (kind === 'Tag' || kind === 'Tag3' || kind === 'Tag4') {
                        if (action === ThingsItemAction.Deleted) {
                            const removed = this.tags.remove(uuid);
                            if (removed) removedTagTitles.push(removed);
                        } else {
                            const title = (props as ThingsTagWireProps).tt;
                            if (typeof title === 'string' && tagNameToTitle(title)) {
                                const { previousTitle } = this.tags.set(uuid, title);
                                if (previousTitle && tagKey(previousTitle) !== tagKey(title)) {
                                    tagRenames.push([previousTitle, tagNameToTitle(title)]);
                                }
                            }
                        }
                    } else if (kind === 'Area' || kind === 'Area2' || kind === 'Area3') {
                        const result = mapThingsAreaToEntity(uuid, props as ThingsAreaWireProps, action);
                        if (result.isDeleted) {
                            const existingArea = areaMap.get(uuid);
                            if (existingArea) {
                                areaMap.set(uuid, { ...existingArea, deletedAt: new Date().toISOString() });
                            }
                        } else if (result.area) {
                            const existingArea = areaMap.get(uuid);
                            areaMap.set(
                                uuid,
                                existingArea ? mergeThingsAreaIntoExisting(existingArea, result.area) : result.area
                            );
                        }
                    } else if (kind === 'ChecklistItem' || kind === 'ChecklistItem2' || kind === 'ChecklistItem3') {
                        const result = mapThingsChecklistToEntity(uuid, props as ThingsChecklistWireProps, action);
                        const ownerId = result.parentTaskId ?? (result.isDeleted ? findChecklistOwner(uuid) : undefined);
                        if (ownerId) {
                            const taskChecklists = getPendingChecklist(ownerId);
                            if (result.isDeleted) {
                                taskChecklists.delete(uuid);
                            } else if (result.checklistItem) {
                                taskChecklists.set(uuid, result.checklistItem);
                            }
                        }
                    } else if (kind === 'Tombstone' || kind === 'Tombstone2') {
                        const targetId = props.dloid;
                        if (typeof targetId !== 'string' || targetId.length === 0
                            || (props.dld !== undefined && (typeof props.dld !== 'number' || !Number.isFinite(props.dld)))) {
                            throw new ThingsCloudError('Things Cloud returned a malformed tombstone; the checkpoint was not advanced.');
                        }
                        const deletedAt = typeof props.dld === 'number'
                            ? new Date(props.dld * 1000).toISOString()
                            : new Date().toISOString();
                        let found = false;
                        const task = taskMap.get(targetId);
                        if (task) {
                            taskMap.set(targetId, { ...task, deletedAt, purgedAt: deletedAt });
                            rawTagIds.delete(targetId);
                            this.tags.clearOwner(targetId);
                            found = true;
                        }
                        const project = projectMap.get(targetId);
                        if (project) {
                            projectMap.set(targetId, { ...project, deletedAt, purgedAt: deletedAt });
                            rawTagIds.delete(targetId);
                            this.tags.clearOwner(targetId);
                            found = true;
                        }
                        const section = sectionMap.get(targetId);
                        if (section) {
                            sectionMap.set(targetId, { ...section, deletedAt });
                            found = true;
                        }
                        const area = areaMap.get(targetId);
                        if (area) {
                            areaMap.set(targetId, { ...area, deletedAt });
                            found = true;
                        }
                        const removedTag = this.tags.remove(targetId);
                        if (removedTag) removedTagTitles.push(removedTag);
                        if (removedTag) found = true;
                        const checklistOwner = findChecklistOwner(targetId);
                        if (checklistOwner) {
                            getPendingChecklist(checklistOwner).delete(targetId);
                            found = true;
                        }
                        const priorWire = wireRecords[targetId];
                        if (priorWire && ['Task', 'Task3', 'Task4', 'Task6', 'Task7', 'Area', 'Area2', 'Area3',
                            'Tag', 'Tag3', 'Tag4', 'ChecklistItem', 'ChecklistItem2', 'ChecklistItem3'].includes(priorWire.e)) {
                            found = true;
                        }
                        if (!found) {
                            throw new ThingsCloudError('Things Cloud tombstone refers to an unknown entity; the checkpoint was not advanced.');
                        }
                        touchedIds.add(targetId);
                    }
                }
            }

            // An outer `items` element is one history commit, even if it carries
            // several UUIDs. This matches the SDK's LoadedServerIndex calculation;
            // `current-item-index` is the reported head, not this page's offset.
            currentIndex += items.length;
            if (reportsCaughtUp(page)) {
                break;
            }
        }

        // Turn Things tag uuids into Mindwtr #names for every task/project the
        // server touched. If an event had no `tg`, keep the local tags.
        const localTaskTags = new Map<string, string[]>(currentData.tasks.map(t => [t.id, t.tags]));
        const localProjectTags = new Map<string, string[]>(currentData.projects.map(p => [p.id, p.tagIds]));
        for (const [id, raw] of rawTagIds) {
            const task = taskMap.get(id);
            if (task) {
                task.tags = raw ? this.tags.namesFor(id, raw) : (localTaskTags.get(id) ?? []);
                continue;
            }
            const project = projectMap.get(id);
            if (project) {
                project.tagIds = raw ? this.tags.namesFor(id, raw) : (localProjectTags.get(id) ?? []);
            }
        }

        // A tag renamed or deleted on the server must also change on items this
        // pull did not otherwise touch (they still carry the old name).
        if (tagRenames.length > 0 || removedTagTitles.length > 0) {
            const rewrite = (tags: readonly string[] | undefined): string[] => {
                let next = [...(tags ?? [])];
                for (const [oldTitle, newTitle] of tagRenames) {
                    const oldKey = tagKey(oldTitle);
                    next = next.map(tag => (tagKey(tag) === oldKey ? tagTitleToName(newTitle) : tag));
                }
                for (const removed of removedTagTitles) {
                    // A tag re-created under the same title in this pull is still in use.
                    if (this.tags.uuidForName(removed)) continue;
                    const key = tagKey(removed);
                    next = next.filter(tag => tagKey(tag) !== key);
                }
                return dedupeTagNames(next);
            };
            for (const task of taskMap.values()) {
                if (!rawTagIds.has(task.id)) task.tags = rewrite(task.tags);
            }
            for (const project of projectMap.values()) {
                if (!rawTagIds.has(project.id)) project.tagIds = rewrite(project.tagIds);
            }
        }

        // Apply updated checklists to their respective tasks
        for (const [taskId, checklistMap] of pendingChecklists.entries()) {
            const task = taskMap.get(taskId);
            if (task) {
                task.checklist = Array.from(checklistMap.values());
            }
        }

        const updatedData: AppData = {
            ...currentData,
            tasks: Array.from(taskMap.values()),
            projects: Array.from(projectMap.values()),
            sections: Array.from(sectionMap.values()),
            areas: Array.from(areaMap.values()),
        };

        this.wireRecords = wireRecords;

        return {
            data: updatedData,
            newServerIndex: currentIndex,
            changeCount: totalProcessed,
            touchedIds,
            receivedChanges,
            receivedBatches,
            wireRecords,
        };
    }

    /**
     * Reads the whole history from the start but keeps only the tag entities.
     * Use it once when upgrading an install that synced before tag support: its
     * saved server index is already past the tag entities, so an incremental pull
     * would never deliver them.
     */
    async loadTagRegistry(options: { maxPages?: number } = {}): Promise<void> {
        const maxPages = Math.max(1, Math.floor(options.maxPages ?? DEFAULT_MAX_PULL_PAGES));
        let index = 0;
        for (let pages = 0; ; pages++) {
            if (pages >= maxPages) {
                throw new ThingsCloudError(
                    `Things Cloud tag load did not finish after ${maxPages} pages (stopped at index ${index}).`
                );
            }
            const page = await this.client.getItemsPage(index);
            const items = page.items || [];
            if (items.length === 0) break;
            for (const itemEntry of items) {
                for (const [uuid, wireObj] of Object.entries(itemEntry)) {
                    const kind = wireObj.e;
                    if (kind !== 'Tag' && kind !== 'Tag3' && kind !== 'Tag4') continue;
                    if (wireObj.t === ThingsItemAction.Deleted) {
                        this.tags.remove(uuid);
                    } else {
                        const title = (wireObj.p as ThingsTagWireProps | undefined)?.tt;
                        if (typeof title === 'string') this.tags.set(uuid, title);
                    }
                }
            }
            index += items.length;
            if (reportsCaughtUp(page)) break;
        }
    }

    /**
     * Replaces raw Things tag uuids left on tasks/projects by earlier versions of
     * the sync (which copied `tg` verbatim) with the matching `#name`. Anything
     * that is not a known tag uuid is left alone.
     */
    repairLegacyTagIds(data: AppData): AppData {
        const fix = (tags: string[] | undefined): string[] | undefined => {
            if (!tags || tags.length === 0) return tags;
            let changed = false;
            const next = tags.map(tag => {
                const title = this.tags.titleOf(tag);
                if (!title) return tag;
                changed = true;
                return tagTitleToName(title);
            });
            return changed ? dedupeTagNames(next) : tags;
        };
        return {
            ...data,
            tasks: data.tasks.map(task => {
                const tags = fix(task.tags);
                return tags === task.tags ? task : { ...task, tags: tags ?? [] };
            }),
            projects: data.projects.map(project => {
                const tagIds = fix(project.tagIds);
                return tagIds === project.tagIds ? project : { ...project, tagIds: tagIds ?? [] };
            }),
        };
    }

    /**
     * Detects semantic changes made locally and pushes them to Things Cloud.
     *
     * Deletion policy (deliberately conservative, since Things is a separate
     * system and a permanent delete cannot be undone there):
     * - A Mindwtr trash (`deletedAt`) becomes Things' Trash (`tr: true`); restoring
     *   clears it again.
     * - A permanent delete is sent only for items that were already in the trash
     *   at the last sync. Items purged by archive retention were never trashed,
     *   so Things keeps them.
     * - Items that were never synced (created and deleted between two syncs) are
     *   not sent at all.
     * - Areas have no trash in Things: a deleted Mindwtr area is deleted outright.
     * - Removing a checklist item deletes it on the server.
     * - Ids listed in `options.skipIds` (just written by the pull) are never
     *   echoed back as deletion changes.
     */
    async push(
        currentData: AppData,
        baselineData: AppData | null,
        currentServerIndex: number,
        options: ThingsPushOptions = {}
    ): Promise<{ newServerIndex: number; pushedCount: number }> {
        const skipIds = options.skipIds ?? NO_IDS;
        const changes: Record<string, ThingsWireObject> = {};
        const oldTasks = new Map<string, Task>((baselineData?.tasks || []).map(t => [t.id, t]));
        const oldProjects = new Map<string, Project>((baselineData?.projects || []).map(p => [p.id, p]));
        const oldAreas = new Map<string, Area>((baselineData?.areas || []).map(a => [a.id, a]));
        const oldSections = new Map<string, Section>((baselineData?.sections || []).map(s => [s.id, s]));

        // 1. Detect task creations, updates, trash/restore and permanent deletes
        const currentTaskIds = new Set<string>();
        for (const task of currentData.tasks) {
            currentTaskIds.add(task.id);
            const old = oldTasks.get(task.id);
            if (!old) {
                // Trashed or purged before it ever reached Things: nothing to tell the server.
                if (task.deletedAt || task.purgedAt) continue;

                // New task created
                changes[task.id] = mindwtrTaskToThingsWire(task, false, this.tagUuidsFor(task.id, task.tags));

                // Add checklist items if present
                if (task.checklist && task.checklist.length > 0) {
                    task.checklist.forEach((item, idx) => {
                        changes[item.id] = mindwtrChecklistToThingsWire(item, task.id, idx, false);
                    });
                }
                continue;
            }

            const skipDeletion = skipIds.has(task.id);
            const trashChanged = !skipDeletion && Boolean(old.deletedAt) !== Boolean(task.deletedAt);

            if (!skipDeletion && old.deletedAt && task.purgedAt && !old.purgedAt) {
                // Was in the trash at the last sync and has now been emptied from it.
                changes[task.id] = thingsDeleteWire('Task7');
                continue;
            }
            if (task.purgedAt) {
                // Purged without having been trashed at the last sync (e.g. archive
                // retention): never delete on Things. Still report a fresh trash.
                if (trashChanged) {
                    changes[task.id] = mindwtrTaskToThingsWire(task, true, this.tagUuidsFor(task.id, task.tags));
                }
                continue;
            }

            // Check if semantically modified
            const isChanged =
                trashChanged ||
                !sameTagSet(old.tags, task.tags) ||
                old.title !== task.title ||
                thingsStateKeyForTask(old) !== thingsStateKeyForTask(task) ||
                (old.description ?? '') !== (task.description ?? '') ||
                Boolean(old.isFocusedToday) !== Boolean(task.isFocusedToday) ||
                !sameDay(old.startTime, task.startTime) ||
                !sameDay(old.dueDate, task.dueDate) ||
                old.projectId !== task.projectId ||
                old.sectionId !== task.sectionId ||
                old.areaId !== task.areaId;

            if (isChanged) {
                changes[task.id] = mindwtrTaskToThingsWire(task, true, this.tagUuidsFor(task.id, task.tags));
            }

            // Check for checklist modifications
            const oldChecklist = new Map<string, ChecklistItem>((old.checklist || []).map(c => [c.id, c]));
            const currentChecklist = task.checklist || [];
            const currentChecklistIds = new Set<string>();

            for (let i = 0; i < currentChecklist.length; i++) {
                const item = currentChecklist[i];
                currentChecklistIds.add(item.id);
                const oldItem = oldChecklist.get(item.id);
                if (!oldItem) {
                    changes[item.id] = mindwtrChecklistToThingsWire(item, task.id, i, false);
                } else if (oldItem.title !== item.title || oldItem.isCompleted !== item.isCompleted) {
                    changes[item.id] = mindwtrChecklistToThingsWire(item, task.id, i, true);
                }
            }

            // Checklist items the user removed
            for (const oldItemId of oldChecklist.keys()) {
                if (!currentChecklistIds.has(oldItemId) && !skipIds.has(oldItemId)) {
                    changes[oldItemId] = thingsDeleteWire('ChecklistItem3');
                }
            }
        }

        // Tasks that disappeared from the data entirely: delete only if they were trashed.
        for (const old of oldTasks.values()) {
            if (currentTaskIds.has(old.id) || skipIds.has(old.id)) continue;
            if (old.deletedAt && !old.purgedAt) {
                changes[old.id] = thingsDeleteWire('Task7');
            }
        }

        // 2. Detect project creations, updates, trash/restore and permanent deletes
        const currentProjectIds = new Set<string>();
        for (const project of currentData.projects) {
            currentProjectIds.add(project.id);
            const old = oldProjects.get(project.id);
            if (!old) {
                if (project.deletedAt || project.purgedAt) continue;
                changes[project.id] = mindwtrProjectToThingsWire(project, false, this.tagUuidsFor(project.id, project.tagIds));
                continue;
            }

            const skipDeletion = skipIds.has(project.id);
            const trashChanged = !skipDeletion && Boolean(old.deletedAt) !== Boolean(project.deletedAt);

            if (!skipDeletion && old.deletedAt && project.purgedAt && !old.purgedAt) {
                changes[project.id] = thingsDeleteWire('Task7');
                continue;
            }
            if (project.purgedAt) {
                if (trashChanged) {
                    changes[project.id] = mindwtrProjectToThingsWire(project, true, this.tagUuidsFor(project.id, project.tagIds));
                }
                continue;
            }

            if (
                trashChanged ||
                !sameTagSet(old.tagIds, project.tagIds) ||
                old.title !== project.title ||
                thingsStateKeyForProject(old) !== thingsStateKeyForProject(project) ||
                (old.supportNotes ?? '') !== (project.supportNotes ?? '') ||
                !sameDay(old.startDate, project.startDate) ||
                !sameDay(old.dueDate, project.dueDate) ||
                old.areaId !== project.areaId
            ) {
                changes[project.id] = mindwtrProjectToThingsWire(project, true, this.tagUuidsFor(project.id, project.tagIds));
            }
        }

        for (const old of oldProjects.values()) {
            if (currentProjectIds.has(old.id) || skipIds.has(old.id)) continue;
            if (old.deletedAt && !old.purgedAt) {
                changes[old.id] = thingsDeleteWire('Task7');
            }
        }

        // 2b. Headings are Things Task entities and must be created/updated before
        // task references are committed. They share the same commit so a task
        // cannot be sent with a dangling action-group UUID.
        const currentSectionIds = new Set<string>();
        for (const section of currentData.sections || []) {
            currentSectionIds.add(section.id);
            const old = oldSections.get(section.id);
            if (!old) {
                if (!section.deletedAt) changes[section.id] = mindwtrSectionToThingsWire(section, false);
                continue;
            }
            const skipDeletion = skipIds.has(section.id);
            if (section.deletedAt) {
                if (!old.deletedAt && !skipDeletion) changes[section.id] = mindwtrSectionToThingsWire(section, true);
                continue;
            }
            if (old.deletedAt || old.title !== section.title || old.projectId !== section.projectId
                || old.order !== section.order || (old.description ?? '') !== (section.description ?? '')) {
                if (!skipDeletion) changes[section.id] = mindwtrSectionToThingsWire(section, true);
            }
        }
        for (const old of oldSections.values()) {
            if (!currentSectionIds.has(old.id) && old.deletedAt && !skipIds.has(old.id)) {
                changes[old.id] = thingsDeleteWire('Task7');
            }
        }

        // 3. Detect area creations, renames/reorders and deletions
        for (const area of currentData.areas || []) {
            const old = oldAreas.get(area.id);
            if (!old) {
                if (area.deletedAt) continue;
                changes[area.id] = mindwtrAreaToThingsWire(area, false);
                continue;
            }

            if (area.deletedAt) {
                // Things has no area trash: a deleted area is deleted outright.
                if (!old.deletedAt && !skipIds.has(area.id)) {
                    changes[area.id] = thingsDeleteWire('Area3');
                }
                continue;
            }

            if (old.deletedAt) {
                // Restored locally after the delete was synced: recreate it on the server.
                if (!skipIds.has(area.id)) {
                    changes[area.id] = mindwtrAreaToThingsWire(area, false);
                }
                continue;
            }

            if (old.name !== area.name || old.order !== area.order) {
                changes[area.id] = mindwtrAreaToThingsWire(area, true);
            }
        }

        if (Object.keys(changes).length === 0) {
            this.tags.discardPending();
            return { newServerIndex: currentServerIndex, pushedCount: 0 };
        }

        // Tags minted for this push must exist on the server in the same commit.
        const pendingTags = this.tags.pendingTags();
        const knownTagCount = this.tags.size - pendingTags.length;
        pendingTags.forEach(({ uuid, title }, offset) => {
            changes[uuid] = mindwtrTagToThingsWire(title, knownTagCount + offset);
        });

        const changeCount = Object.keys(changes).length;
        for (const [id, change] of Object.entries(changes)) {
            const previousWire = this.wireRecords[id];
            if (!previousWire || change.t === ThingsItemAction.Deleted) continue;
            const props = change.p as Record<string, unknown>;
            const previousProps = previousWire.p as Record<string, unknown>;
            for (const [key, value] of Object.entries(previousProps)) {
                if (!(key in props)) props[key] = value;
            }
            this.wireRecords[id] = { ...change, p: props };
        }
        await options.onCommitPrepared?.(changes, currentServerIndex);
        let newIndex: number;
        try {
            newIndex = await this.client.commit(changes, currentServerIndex);
        } catch (error) {
            if (isThingsCloudConflictError(error)) await options.onCommitRejected?.(changes, currentServerIndex);
            throw error;
        }
        this.tags.confirmPending();
        return { newServerIndex: newIndex, pushedCount: changeCount };
    }

    /**
     * Executes a full bi-directional sync cycle (Pull then Push).
     *
     * If another device commits between our pull and our push, the server
     * rejects the commit because its ancestor-index is stale. In that case we
     * pull again from where the last pull ended (merging the new remote changes
     * into the data we are about to push) and retry, up to
     * `maxConflictRetries` extra times. Any other error is thrown immediately.
     */
    async sync(
        currentData: AppData,
        lastKnownData: AppData | null,
        serverIndex: number,
        options: ThingsSyncOptions = {}
    ): Promise<ThingsSyncResult> {
        const maxRetries = Math.max(
            0,
            Math.floor(options.maxConflictRetries ?? DEFAULT_MAX_CONFLICT_RETRIES)
        );

        let data = currentData;
        let index = serverIndex;
        let pulledChangesCount = 0;
        const touchedIds = new Set<string>();
        const receivedChanges: Record<string, ThingsWireObject> = {};
        const receivedBatches: Array<Record<string, ThingsWireObject>> = [];
        const locallyChangedIds = localChangesSince(currentData, lastKnownData);

        for (let attempt = 0; ; attempt++) {
            // Step 1: Pull remote changes (on retries: only what arrived since the last pull)
            const pullResult = await this.pull(data, index, { maxPages: options.maxPullPages });
            const localEntities = entitiesById(currentData);
            const remoteEntities = entitiesById(pullResult.data);
            const conflicts = [...pullResult.touchedIds]
                .filter((id) => locallyChangedIds.has(id))
                .map((id): ThingsCloudConcurrentEntityConflict | null => {
                    const local = localEntities.get(id);
                    const remote = remoteEntities.get(id);
                    if (stableValue(local) === stableValue(remote)) return null;
                    const collection = local?.collection ?? remote?.collection;
                    if (!collection) return null;
                    return {
                        id,
                        collection,
                        local: local?.entity ?? null,
                        remote: remote?.entity ?? null,
                    };
                })
                .filter((conflict): conflict is ThingsCloudConcurrentEntityConflict => conflict !== null);
            if (conflicts.length > 0) {
                throw new ThingsCloudConcurrentEditError({
                    conflicts,
                    remoteData: pullResult.data,
                    newServerIndex: pullResult.newServerIndex,
                    wireRecords: pullResult.wireRecords,
                    receivedChanges: pullResult.receivedChanges,
                    receivedBatches: pullResult.receivedBatches,
                });
            }
            data = pullResult.data;
            index = pullResult.newServerIndex;
            pulledChangesCount += pullResult.changeCount;
            for (const id of pullResult.touchedIds) touchedIds.add(id);
            Object.assign(receivedChanges, pullResult.receivedChanges);
            receivedBatches.push(...pullResult.receivedBatches);

            // Step 2: Push local changes made since last sync
            try {
                const pushResult = await this.push(data, lastKnownData, index, {
                    skipIds: touchedIds,
                    onCommitPrepared: options.onCommitPrepared,
                    onCommitRejected: options.onCommitRejected,
                });
                return {
                    data,
                    newServerIndex: pushResult.newServerIndex,
                    pulledChangesCount,
                    pushedChangesCount: pushResult.pushedCount,
                    conflictRetries: attempt,
                    receivedChanges,
                    receivedBatches,
                    wireRecords: this.wireRecords,
                };
            } catch (error) {
                if (!isThingsCloudConflictError(error)) {
                    throw error;
                }
                if (attempt >= maxRetries) {
                    throw new ThingsCloudError(
                        `Things Cloud kept changing during sync; gave up after ${attempt + 1} attempt(s). Try again.`,
                        error.statusCode,
                        error.responseBody
                    );
                }
            }
        }
    }
}
