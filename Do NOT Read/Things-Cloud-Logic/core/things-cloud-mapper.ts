import type { Area, ChecklistItem, Project, Section, Task } from '../types.ts';
import {
    ThingsItemAction,
    ThingsTaskSchedule,
    ThingsTaskStatus,
    ThingsTaskType,
    type ThingsAreaWireProps,
    type ThingsChecklistWireProps,
    type ThingsItemKind,
    type ThingsTagWireProps,
    type ThingsTaskWireProps,
    type ThingsWireObject,
} from './things-cloud-types.ts';

// ---------------------------------------------------------------------------
// Dates
//
// Things stores a "day" (start date, deadline, today marker) as the epoch
// seconds of UTC midnight of that calendar date: a floating date with no time
// zone. So a day must be taken from the *calendar date* the user sees, not from
// the UTC date of an instant, and must come back as a calendar date, not as a
// midnight instant that shifts a day in time zones west of UTC.
// ---------------------------------------------------------------------------

const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * Converts a Mindwtr date into a Things day (epoch seconds, UTC midnight).
 * - "YYYY-MM-DD" is that calendar date.
 * - A date-time, or a Date/number, is the calendar date in the device's local
 *   time zone.
 */
export function toDayEpochSeconds(dateInput?: string | number | Date | null): number | null {
    if (!dateInput) return null;
    if (typeof dateInput === 'string') {
        const match = DATE_ONLY.exec(dateInput.trim());
        if (match) {
            const year = Number(match[1]);
            const month = Number(match[2]);
            const day = Number(match[3]);
            const ms = Date.UTC(year, month - 1, day);
            const check = new Date(ms);
            const valid =
                check.getUTCFullYear() === year && check.getUTCMonth() === month - 1 && check.getUTCDate() === day;
            return valid ? ms / 1000 : null;
        }
    }
    const d = new Date(dateInput);
    if (isNaN(d.getTime())) return null;
    return Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 1000;
}

/**
 * Returns the Things day for today in the device's local time zone.
 */
export function getTodayEpochSeconds(now: Date = new Date()): number {
    return Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()) / 1000;
}

export function epochToIsoString(epochSeconds?: number | null): string | undefined {
    if (!epochSeconds) return undefined;
    return new Date(epochSeconds * 1000).toISOString();
}

/** Things day -> "YYYY-MM-DD" (the calendar date it stands for). */
export function epochToDayString(epochSeconds?: number | null): string | undefined {
    if (!epochSeconds) return undefined;
    return new Date(epochSeconds * 1000).toISOString().slice(0, 10);
}

/** True when both values fall on the same calendar day (or both are empty). */
export function sameDay(
    a?: string | number | Date | null,
    b?: string | number | Date | null
): boolean {
    return toDayEpochSeconds(a) === toDayEpochSeconds(b);
}

function isFutureDay(value: string | undefined, today: number): boolean {
    const day = toDayEpochSeconds(value);
    return day !== null && day > today;
}

function isoToEpochSeconds(iso?: string | null): number | undefined {
    if (!iso) return undefined;
    const ms = Date.parse(iso);
    return Number.isNaN(ms) ? undefined : Math.floor(ms / 1000);
}

// ---------------------------------------------------------------------------
// Status
//
// Mindwtr has more statuses than Things (waiting, reference, ...). Both sides
// are reduced to a "Things state"; two statuses with the same state are the
// same thing as far as Things is concerned, so a pull must not replace one with
// the other (waiting -> next) and a push need not report the change.
// ---------------------------------------------------------------------------

export type ThingsStateKey = 'inbox' | 'anytime' | 'scheduled' | 'someday' | 'completed' | 'canceled';

/**
 * The state a task has in Things:
 * - done, or archived after completing -> completed; archived after cancelling -> canceled
 * - inbox -> inbox
 * - a start date in the future -> scheduled (Things: "Someday" plus a start date)
 * - someday -> someday
 * - next, waiting, reference -> anytime
 */
export function thingsStateKeyForTask(
    task: Pick<Task, 'status' | 'startTime' | 'cancelledAt'>,
    today: number = getTodayEpochSeconds()
): ThingsStateKey {
    switch (task.status) {
        case 'done':
            return 'completed';
        case 'archived':
            return task.cancelledAt ? 'canceled' : 'completed';
        case 'inbox':
            return 'inbox';
        case 'someday':
            return isFutureDay(task.startTime, today) ? 'scheduled' : 'someday';
        default:
            return isFutureDay(task.startTime, today) ? 'scheduled' : 'anytime';
    }
}

export function thingsStateKeyForProject(
    project: Pick<Project, 'status' | 'cancelledAt'>
): ThingsStateKey {
    if (project.status === 'archived') return project.cancelledAt ? 'canceled' : 'completed';
    return project.status === 'someday' ? 'someday' : 'anytime';
}

// ---------------------------------------------------------------------------
// Things -> Mindwtr
// ---------------------------------------------------------------------------

/**
 * Maps a Things Task wire item to a Mindwtr Task, Project, or Section.
 * `props` must describe the complete item: see thingsTaskPropsWithExisting for
 * patches.
 */
export function mapThingsTaskToEntity(
    id: string,
    props: ThingsTaskWireProps,
    action: ThingsItemAction
): {
    task?: Task;
    project?: Project;
    section?: Section;
    isDeleted?: boolean;
} {
    if (action === ThingsItemAction.Deleted) {
        return { isDeleted: true };
    }

    const type = props.tp ?? ThingsTaskType.Todo;
    const nowIso = new Date().toISOString();
    const createdIso = epochToIsoString(props.cd) || nowIso;
    const updatedIso = epochToIsoString(props.md) || createdIso;
    const title = props.tt || '';
    const noteText = typeof props.nt === 'string' ? props.nt : props.nt?.text || '';

    if (type === ThingsTaskType.Project) {
        const isArchived = props.ss === ThingsTaskStatus.Completed || props.ss === ThingsTaskStatus.Canceled;
        const project: Project = {
            id,
            title,
            status: isArchived ? 'archived' : (props.st === ThingsTaskSchedule.Someday ? 'someday' : 'active'),
            color: '#2080f0',
            order: props.ix || 0,
            tagIds: props.tg || [],
            supportNotes: noteText,
            startDate: epochToDayString(props.sr),
            dueDate: epochToDayString(props.dd),
            cancelledAt: props.ss === ThingsTaskStatus.Canceled ? epochToIsoString(props.sp) : undefined,
            areaId: props.ar?.[0],
            createdAt: createdIso,
            updatedAt: updatedIso,
            ...(props.tr ? { deletedAt: updatedIso } : {}),
        };
        return { project };
    }

    if (type === ThingsTaskType.Heading) {
        const section: Section = {
            id,
            projectId: props.pr?.[0] || '',
            title,
            description: noteText,
            order: props.ix || 0,
            createdAt: createdIso,
            updatedAt: updatedIso,
            ...(props.tr ? { deletedAt: updatedIso } : {}),
        };
        return { section };
    }

    // Otherwise it's a regular Task (Todo)
    let status: Task['status'] = 'inbox';
    let isFocusedToday = false;

    if (props.ss === ThingsTaskStatus.Completed) {
        status = 'done';
    } else if (props.ss === ThingsTaskStatus.Canceled) {
        status = 'archived';
    } else {
        const todayEpoch = getTodayEpochSeconds();
        if (props.st === ThingsTaskSchedule.Inbox) {
            status = 'inbox';
        } else if (props.st === ThingsTaskSchedule.Someday) {
            if (props.sr && props.sr > todayEpoch) {
                status = 'next';
            } else {
                status = 'someday';
            }
        } else {
            // Anytime
            status = 'next';
            if (props.tir === todayEpoch || props.sr === todayEpoch) {
                isFocusedToday = true;
            }
        }
    }

    const task: Task = {
        id,
        title,
        description: noteText,
        status,
        isFocusedToday,
        startTime: epochToDayString(props.sr),
        dueDate: epochToDayString(props.dd),
        projectId: props.pr?.[0],
        sectionId: props.agr?.[0],
        areaId: props.ar?.[0],
        tags: props.tg || [],
        contexts: [],
        completedAt: props.ss === ThingsTaskStatus.Completed ? epochToIsoString(props.sp) : undefined,
        cancelledAt: props.ss === ThingsTaskStatus.Canceled ? epochToIsoString(props.sp) : undefined,
        createdAt: createdIso,
        updatedAt: updatedIso,
        order: props.ix || 0,
        ...(props.tr ? { deletedAt: updatedIso } : {}),
    };

    return { task };
}

/**
 * Things "Modified" entries are patches: they may carry only the properties that
 * changed (a completion arrives as just the status and stop date). Fill in the
 * missing ones from the item we already have, so the mapper sees the full state
 * instead of defaults (empty title, no tags, Todo type, ...).
 *
 * Tags are left out: they are resolved separately from the raw `tg` list.
 */
export function thingsTaskPropsWithExisting(
    props: ThingsTaskWireProps,
    existing: { task?: Task; project?: Project; section?: Section }
): ThingsTaskWireProps {
    let base: ThingsTaskWireProps | undefined;
    let updatedAt: string | undefined;

    if (existing.project) {
        base = mindwtrProjectToThingsWire(existing.project, false).p as ThingsTaskWireProps;
        updatedAt = existing.project.updatedAt;
    } else if (existing.section) {
        const section = existing.section;
        base = {
            tp: ThingsTaskType.Heading,
            tt: section.title,
            nt: section.description ? { text: section.description } : undefined,
            pr: section.projectId ? [section.projectId] : [],
            ix: section.order,
            tr: Boolean(section.deletedAt),
            cd: isoToEpochSeconds(section.createdAt),
        };
        updatedAt = section.updatedAt;
    } else if (existing.task) {
        base = mindwtrTaskToThingsWire(existing.task, false).p as ThingsTaskWireProps;
        updatedAt = existing.task.updatedAt;
    }

    if (!base) return props;

    const rest: ThingsTaskWireProps = { ...base };
    delete rest.tg;
    const md = isoToEpochSeconds(updatedAt);
    return { ...rest, ...(md !== undefined ? { md } : {}), ...props };
}

/**
 * Applies a task mapped from Things to the task we already have. Only fields
 * Things knows about change; everything Mindwtr-only (priority, contexts,
 * recurrence, attachments, estimates, checklist, ...) stays as it was.
 */
export function mergeThingsTaskIntoExisting(existing: Task, mapped: Task): Task {
    const keepStatus = thingsStateKeyForTask(existing) === thingsStateKeyForTask(mapped);
    const key = thingsStateKeyForTask(mapped);
    const finished = keepStatus && (key === 'completed' || key === 'canceled');
    return {
        ...existing,
        title: mapped.title,
        description: (mapped.description ?? '') === (existing.description ?? '') ? existing.description : mapped.description,
        status: keepStatus ? existing.status : mapped.status,
        isFocusedToday: mapped.isFocusedToday,
        // Things only has days; keep a time of day the user set locally.
        startTime: sameDay(existing.startTime, mapped.startTime) ? existing.startTime : mapped.startTime,
        dueDate: sameDay(existing.dueDate, mapped.dueDate) ? existing.dueDate : mapped.dueDate,
        projectId: mapped.projectId,
        sectionId: mapped.sectionId,
        areaId: mapped.areaId,
        tags: mapped.tags,
        completedAt: finished ? (existing.completedAt ?? mapped.completedAt) : mapped.completedAt,
        cancelledAt: finished ? (existing.cancelledAt ?? mapped.cancelledAt) : mapped.cancelledAt,
        createdAt: mapped.createdAt,
        updatedAt: mapped.updatedAt,
        order: mapped.order,
        deletedAt: mapped.deletedAt,
        purgedAt: undefined,
    };
}

/** Project counterpart of mergeThingsTaskIntoExisting (keeps colour, sequencing, ...). */
export function mergeThingsProjectIntoExisting(existing: Project, mapped: Project): Project {
    const keepStatus = thingsStateKeyForProject(existing) === thingsStateKeyForProject(mapped);
    const key = thingsStateKeyForProject(mapped);
    const finished = keepStatus && (key === 'completed' || key === 'canceled');
    return {
        ...existing,
        title: mapped.title,
        status: keepStatus ? existing.status : mapped.status,
        order: mapped.order,
        tagIds: mapped.tagIds,
        supportNotes: (mapped.supportNotes ?? '') === (existing.supportNotes ?? '') ? existing.supportNotes : mapped.supportNotes,
        startDate: sameDay(existing.startDate, mapped.startDate) ? existing.startDate : mapped.startDate,
        dueDate: sameDay(existing.dueDate, mapped.dueDate) ? existing.dueDate : mapped.dueDate,
        cancelledAt: finished ? (existing.cancelledAt ?? mapped.cancelledAt) : mapped.cancelledAt,
        areaId: mapped.areaId,
        createdAt: mapped.createdAt,
        updatedAt: mapped.updatedAt,
        deletedAt: mapped.deletedAt,
        purgedAt: undefined,
    };
}

/** Area counterpart: Things only knows the name and order, so colour and icon stay. */
export function mergeThingsAreaIntoExisting(existing: Area, mapped: Area): Area {
    return {
        ...existing,
        name: mapped.name,
        order: mapped.order,
        updatedAt: mapped.updatedAt,
        deletedAt: undefined,
    };
}

/**
 * Maps a Things Area wire item to a Mindwtr Area.
 */
export function mapThingsAreaToEntity(
    id: string,
    props: ThingsAreaWireProps,
    action: ThingsItemAction
): { area?: Area; isDeleted?: boolean } {
    if (action === ThingsItemAction.Deleted) {
        return { isDeleted: true };
    }

    const nowIso = new Date().toISOString();
    return {
        area: {
            id,
            name: props.tt || 'Untitled Area',
            order: props.ix || 0,
            color: '#2080f0',
            createdAt: epochToIsoString(props.cd) || nowIso,
            updatedAt: epochToIsoString(props.md) || nowIso,
        },
    };
}

/**
 * Maps a Things Checklist item to a Mindwtr ChecklistItem.
 */
export function mapThingsChecklistToEntity(
    id: string,
    props: ThingsChecklistWireProps,
    action: ThingsItemAction
): { checklistItem?: ChecklistItem; parentTaskId?: string; isDeleted?: boolean } {
    if (action === ThingsItemAction.Deleted) {
        return { isDeleted: true, parentTaskId: props.ts?.[0] };
    }

    return {
        checklistItem: {
            id,
            title: props.tt || '',
            isCompleted: props.ss === ThingsTaskStatus.Completed,
        },
        parentTaskId: props.ts?.[0],
    };
}

// ---------------------------------------------------------------------------
// Mindwtr -> Things
// ---------------------------------------------------------------------------

/**
 * Generates a Things Cloud Task wire object (Task7) from a Mindwtr Task.
 *
 * `tagUuids` are the Things tag uuids for `task.tags` (see ThingsTagRegistry).
 * Without it `tg` falls back to the raw `task.tags` strings, which Things
 * would not recognise as tags, so the sync engine always supplies it.
 */
export function mindwtrTaskToThingsWire(
    task: Task,
    isUpdate: boolean = false,
    tagUuids?: string[]
): ThingsWireObject {
    let ss: ThingsTaskStatus = ThingsTaskStatus.Incomplete;
    let sp: number | null = null;
    let st: ThingsTaskSchedule = ThingsTaskSchedule.Anytime;

    switch (thingsStateKeyForTask(task)) {
        case 'completed':
            ss = ThingsTaskStatus.Completed;
            sp = isoToEpochSeconds(task.completedAt) ?? Math.floor(Date.now() / 1000);
            break;
        case 'canceled':
            ss = ThingsTaskStatus.Canceled;
            sp = isoToEpochSeconds(task.cancelledAt) ?? Math.floor(Date.now() / 1000);
            break;
        case 'inbox':
            st = ThingsTaskSchedule.Inbox;
            break;
        case 'someday':
        case 'scheduled':
            // Things shows a task with a future start date as "Someday + date".
            st = ThingsTaskSchedule.Someday;
            break;
        default:
            st = ThingsTaskSchedule.Anytime;
    }

    const tir = task.isFocusedToday ? getTodayEpochSeconds() : null;
    const sr = toDayEpochSeconds(task.startTime);
    const dd = toDayEpochSeconds(task.dueDate);

    const props: ThingsTaskWireProps = {
        tt: task.title,
        nt: task.description ? { text: task.description } : undefined,
        tp: ThingsTaskType.Todo,
        ss,
        sp,
        st,
        sr,
        tir,
        dd,
        pr: task.projectId ? [task.projectId] : [],
        agr: task.sectionId ? [task.sectionId] : [],
        ar: task.areaId ? [task.areaId] : [],
        tg: tagUuids ?? (task.tags || []),
        ix: task.order ?? 0,
        ti: task.focusOrder ?? 0,
        tr: Boolean(task.deletedAt),
        cd: Math.floor(new Date(task.createdAt || Date.now()).getTime() / 1000),
        md: isUpdate ? Math.floor(Date.now() / 1000) : null,
    };

    return {
        t: isUpdate ? ThingsItemAction.Modified : ThingsItemAction.Created,
        e: 'Task7',
        p: props as Record<string, unknown>,
    };
}

/**
 * Generates a Things Cloud Project wire object from a Mindwtr Project.
 * `tagUuids` works as in mindwtrTaskToThingsWire.
 */
export function mindwtrProjectToThingsWire(
    project: Project,
    isUpdate: boolean = false,
    tagUuids?: string[]
): ThingsWireObject {
    const isCompleted = project.status === 'archived' && !project.cancelledAt;
    const isCanceled = !!project.cancelledAt;

    const props: ThingsTaskWireProps = {
        tt: project.title,
        nt: project.supportNotes ? { text: project.supportNotes } : undefined,
        tp: ThingsTaskType.Project,
        ss: isCompleted ? ThingsTaskStatus.Completed : (isCanceled ? ThingsTaskStatus.Canceled : ThingsTaskStatus.Incomplete),
        st: project.status === 'someday' ? ThingsTaskSchedule.Someday : ThingsTaskSchedule.Anytime,
        sr: toDayEpochSeconds(project.startDate),
        dd: toDayEpochSeconds(project.dueDate),
        ar: project.areaId ? [project.areaId] : [],
        tg: tagUuids ?? (project.tagIds || []),
        ix: project.order ?? 0,
        tr: Boolean(project.deletedAt),
        cd: Math.floor(new Date(project.createdAt || Date.now()).getTime() / 1000),
        md: isUpdate ? Math.floor(Date.now() / 1000) : null,
    };

    return {
        t: isUpdate ? ThingsItemAction.Modified : ThingsItemAction.Created,
        e: 'Task7',
        p: props as Record<string, unknown>,
    };
}

/** Things headings are Task entities with type=Heading and a project parent. */
export function mindwtrSectionToThingsWire(section: Section, isUpdate = false): ThingsWireObject {
    const props: ThingsTaskWireProps = {
        tt: section.title,
        nt: section.description ? { text: section.description } : undefined,
        tp: ThingsTaskType.Heading,
        pr: section.projectId ? [section.projectId] : [],
        ix: section.order ?? 0,
        tr: Boolean(section.deletedAt),
        cd: Math.floor(new Date(section.createdAt || Date.now()).getTime() / 1000),
        md: isUpdate ? Math.floor(Date.now() / 1000) : null,
    };
    return {
        t: isUpdate ? ThingsItemAction.Modified : ThingsItemAction.Created,
        e: 'Task7',
        p: props as Record<string, unknown>,
    };
}

/**
 * Generates a Things Cloud Area wire object from a Mindwtr Area.
 * Things areas carry no colour/icon, so only name and order are sent.
 */
export function mindwtrAreaToThingsWire(
    area: Area,
    isUpdate: boolean = false
): ThingsWireObject {
    const props: ThingsAreaWireProps = {
        tt: area.name,
        ix: area.order ?? 0,
        cd: Math.floor(new Date(area.createdAt || Date.now()).getTime() / 1000),
        md: isUpdate ? Math.floor(Date.now() / 1000) : null,
    };

    return {
        t: isUpdate ? ThingsItemAction.Modified : ThingsItemAction.Created,
        e: 'Area3',
        p: props as Record<string, unknown>,
    };
}

/**
 * Wire object that creates a Things tag. Tag hierarchy (`pn`) and shortcuts
 * are not used. Entity name and fields come from reverse-engineered payloads
 * and are unverified against the live server.
 */
export function mindwtrTagToThingsWire(title: string, sortIndex: number = 0): ThingsWireObject {
    const props: ThingsTagWireProps = { tt: title, pn: [], ix: sortIndex };
    return {
        t: ThingsItemAction.Created,
        e: 'Tag4',
        p: props as Record<string, unknown>,
    };
}

/**
 * Wire object that permanently deletes an item. `kind` should match the entity
 * version used when the item is written (Task7, Area3, ChecklistItem3).
 */
export function thingsDeleteWire(kind: ThingsItemKind): ThingsWireObject {
    return { t: ThingsItemAction.Deleted, e: kind, p: {} };
}

/**
 * Generates a Things Cloud Checklist item wire object.
 */
export function mindwtrChecklistToThingsWire(
    item: ChecklistItem,
    parentTaskId: string,
    sortIndex: number = 0,
    isUpdate: boolean = false
): ThingsWireObject {
    const props: ThingsChecklistWireProps = {
        tt: item.title,
        ss: item.isCompleted ? ThingsTaskStatus.Completed : ThingsTaskStatus.Incomplete,
        sp: item.isCompleted ? Math.floor(Date.now() / 1000) : null,
        ts: [parentTaskId],
        ix: sortIndex,
        cd: Math.floor(Date.now() / 1000),
        md: isUpdate ? Math.floor(Date.now() / 1000) : null,
    };

    return {
        t: isUpdate ? ThingsItemAction.Modified : ThingsItemAction.Created,
        e: 'ChecklistItem3',
        p: props as Record<string, unknown>,
    };
}
