import { describe, expect, it } from 'vitest';
import type { AppData, Area, Project, Task } from '../types';
import { ThingsCloudClient } from './things-cloud-client';
import {
    epochToDayString,
    getTodayEpochSeconds,
    mapThingsTaskToEntity,
    mindwtrProjectToThingsWire,
    mindwtrTaskToThingsWire,
    sameDay,
    toDayEpochSeconds,
} from './things-cloud-mapper';
import { ThingsCloudSyncEngine } from './things-cloud-sync';
import {
    ThingsItemAction,
    ThingsTaskSchedule,
    ThingsTaskStatus,
    ThingsTaskType,
    type ThingsItemsPageResponse,
    type ThingsWireObject,
} from './things-cloud-types';

const NOW = '2026-11-02T10:00:00.000Z';

/** Runs `fn` with the process time zone set to `tz`, then restores it. */
function withTimezone<T>(tz: string, fn: () => T): T {
    const previous = process.env.TZ;
    process.env.TZ = tz;
    try {
        return fn();
    } finally {
        if (previous === undefined) delete process.env.TZ;
        else process.env.TZ = previous;
    }
}

const utcDay = (y: number, m: number, d: number) => Date.UTC(y, m - 1, d) / 1000;

/** A date-only string `days` days from now (always strictly in the future for days >= 2). */
const daysFromNow = (days: number) => new Date(Date.now() + days * 86_400_000).toISOString().slice(0, 10);

describe('Things days and time zones', () => {
    const zones = ['UTC', 'Asia/Tehran', 'America/Los_Angeles', 'Pacific/Auckland'];

    it('reads a date-only string as that calendar date in every time zone', () => {
        for (const tz of zones) {
            withTimezone(tz, () => {
                expect(toDayEpochSeconds('2026-11-03')).toBe(utcDay(2026, 11, 3));
            });
        }
    });

    it('takes the local calendar date of a date-time (Tehran, just after midnight)', () => {
        withTimezone('Asia/Tehran', () => {
            // 00:30 on Nov 3 in Tehran is still Nov 2 in UTC; the user means Nov 3.
            expect(toDayEpochSeconds('2026-11-03T00:30:00+03:30')).toBe(utcDay(2026, 11, 3));
        });
    });

    it('takes the local calendar date of a date-time (Los Angeles, evening before)', () => {
        withTimezone('America/Los_Angeles', () => {
            // 00:30Z on Nov 3 is the evening of Nov 2 in Los Angeles.
            expect(toDayEpochSeconds('2026-11-03T00:30:00Z')).toBe(utcDay(2026, 11, 2));
        });
    });

    it('rejects empty and invalid input', () => {
        expect(toDayEpochSeconds(undefined)).toBe(null);
        expect(toDayEpochSeconds('')).toBe(null);
        expect(toDayEpochSeconds('garbage')).toBe(null);
        expect(toDayEpochSeconds('2026-02-31')).toBe(null);
    });

    it('computes today from the local calendar date', () => {
        const instant = new Date('2026-11-02T21:00:00Z');
        withTimezone('Asia/Tehran', () => {
            expect(getTodayEpochSeconds(instant)).toBe(utcDay(2026, 11, 3));
        });
        withTimezone('UTC', () => {
            expect(getTodayEpochSeconds(instant)).toBe(utcDay(2026, 11, 2));
        });
    });

    it('survives a pull -> push round trip in a time zone west of UTC', () => {
        const thingsDay = utcDay(2026, 11, 3);
        withTimezone('America/Los_Angeles', () => {
            const local = epochToDayString(thingsDay);
            expect(local).toBe('2026-11-03');
            expect(toDayEpochSeconds(local)).toBe(thingsDay);
        });
    });

    it('maps pulled start and due days to date-only strings', () => {
        const mapped = mapThingsTaskToEntity(
            't1',
            {
                tt: 'x',
                tp: ThingsTaskType.Todo,
                ss: ThingsTaskStatus.Incomplete,
                st: ThingsTaskSchedule.Anytime,
                sr: utcDay(2026, 11, 3),
                dd: utcDay(2026, 11, 9),
            },
            ThingsItemAction.Created
        );
        expect(mapped.task?.startTime).toBe('2026-11-03');
        expect(mapped.task?.dueDate).toBe('2026-11-09');
    });

    it('writes dueDate to Things as the same calendar day in any zone', () => {
        for (const tz of zones) {
            withTimezone(tz, () => {
                const wire = mindwtrTaskToThingsWire(task('t1', { dueDate: '2026-11-03' }), true);
                expect(wire.p.dd).toBe(utcDay(2026, 11, 3));
            });
        }
    });

    it('compares dates by calendar day', () => {
        expect(sameDay('2026-11-03T14:00:00', '2026-11-03')).toBe(true);
        expect(sameDay('2026-11-03', '2026-11-04')).toBe(false);
        expect(sameDay(undefined, '')).toBe(true);
        expect(sameDay(undefined, '2026-11-03')).toBe(false);
    });
});

const task = (id: string, extra: Partial<Task> = {}): Task =>
    ({ id, title: id, status: 'next', tags: [], contexts: [], createdAt: NOW, updatedAt: NOW, ...extra }) as Task;
const project = (id: string, extra: Partial<Project> = {}): Project =>
    ({ id, title: id, status: 'active', color: '#123456', order: 0, tagIds: [], createdAt: NOW, updatedAt: NOW, ...extra }) as Project;
const area = (id: string, extra: Partial<Area> = {}): Area =>
    ({ id, name: id, order: 0, color: '#abcdef', icon: 'home', createdAt: NOW, updatedAt: NOW, ...extra }) as Area;
const data = (parts: { tasks?: Task[]; projects?: Project[]; areas?: Area[] }): AppData =>
    ({ tasks: [], projects: [], sections: [], areas: [], settings: {}, ...parts }) as unknown as AppData;

describe('Mindwtr status -> Things', () => {
    const wire = (extra: Partial<Task>) => mindwtrTaskToThingsWire(task('t1', extra), true).p;

    it('sends next, waiting and reference as Anytime', () => {
        for (const status of ['next', 'waiting', 'reference'] as const) {
            expect(wire({ status }).st).toBe(ThingsTaskSchedule.Anytime);
            expect(wire({ status }).ss).toBe(ThingsTaskStatus.Incomplete);
        }
    });

    it('sends inbox as Inbox and someday as Someday', () => {
        expect(wire({ status: 'inbox' }).st).toBe(ThingsTaskSchedule.Inbox);
        expect(wire({ status: 'someday' }).st).toBe(ThingsTaskSchedule.Someday);
    });

    it('sends a task with a future start date as Someday plus the date, whatever its status', () => {
        const start = daysFromNow(10);
        for (const status of ['next', 'waiting', 'someday'] as const) {
            const p = wire({ status, startTime: start });
            expect(p.st).toBe(ThingsTaskSchedule.Someday);
            expect(p.sr).toBe(toDayEpochSeconds(start));
        }
    });

    it('keeps a past or today start date on an Anytime task', () => {
        const p = wire({ status: 'next', startTime: '2020-01-01' });
        expect(p.st).toBe(ThingsTaskSchedule.Anytime);
        expect(p.sr).toBe(utcDay(2020, 1, 1));
    });

    it('sends done as Completed with the completion time', () => {
        const p = wire({ status: 'done', completedAt: '2026-11-01T08:00:00.000Z' });
        expect(p.ss).toBe(ThingsTaskStatus.Completed);
        expect(p.sp).toBe(Date.parse('2026-11-01T08:00:00.000Z') / 1000);
    });

    it('sends an archived task as Completed, not as active (it used to come back to life)', () => {
        const p = wire({ status: 'archived', completedAt: '2026-11-01T08:00:00.000Z' });
        expect(p.ss).toBe(ThingsTaskStatus.Completed);
        expect(p.sp).toBe(Date.parse('2026-11-01T08:00:00.000Z') / 1000);
    });

    it('sends an archived task that was cancelled as Canceled', () => {
        const p = wire({ status: 'archived', cancelledAt: '2026-11-01T09:00:00.000Z' });
        expect(p.ss).toBe(ThingsTaskStatus.Canceled);
        expect(p.sp).toBe(Date.parse('2026-11-01T09:00:00.000Z') / 1000);
    });

    it('ignores a stale cancelledAt on a task that is not archived', () => {
        expect(wire({ status: 'next', cancelledAt: '2026-11-01T09:00:00.000Z' }).ss).toBe(ThingsTaskStatus.Incomplete);
    });

    it('maps projects the same way', () => {
        const p = (extra: Partial<Project>) => mindwtrProjectToThingsWire(project('p1', extra), true).p;
        expect(p({ status: 'waiting' }).st).toBe(ThingsTaskSchedule.Anytime);
        expect(p({ status: 'someday' }).st).toBe(ThingsTaskSchedule.Someday);
        expect(p({ status: 'archived' }).ss).toBe(ThingsTaskStatus.Completed);
        expect(p({ status: 'archived', cancelledAt: NOW }).ss).toBe(ThingsTaskStatus.Canceled);
    });
});

// ---------------------------------------------------------------------------
// Pull: patches and merging into existing items
// ---------------------------------------------------------------------------

class PagesClient extends ThingsCloudClient {
    pages: ThingsItemsPageResponse[] = [];
    commits: Array<{ changes: Record<string, ThingsWireObject>; ancestorIndex?: number }> = [];

    constructor() {
        super('test@example.com', 'password', 'history-key', 0);
    }

    async getItemsPage(startIndex: number): Promise<ThingsItemsPageResponse> {
        return (
            this.pages.shift() ?? {
                items: [],
                'latest-total-content-size': 0,
                'start-total-content-size': 0,
                'end-total-content-size': 0,
                'current-item-index': startIndex,
                schema: 301,
            }
        );
    }

    async commit(changes: Record<string, ThingsWireObject>, ancestorIndex?: number): Promise<number> {
        this.commits.push({ changes, ancestorIndex });
        return (ancestorIndex ?? 0) + Object.keys(changes).length;
    }
}

const entry = (
    uuid: string,
    p: Record<string, unknown>,
    action: number = ThingsItemAction.Modified,
    e: string = 'Task7'
): Record<string, ThingsWireObject> => ({ [uuid]: { t: action as ThingsWireObject['t'], e: e as ThingsWireObject['e'], p } });

const pageOf = (...items: Array<Record<string, ThingsWireObject>>): ThingsItemsPageResponse => ({
    items,
    'latest-total-content-size': 1,
    'start-total-content-size': 0,
    'end-total-content-size': 1,
    'current-item-index': items.length,
    schema: 301,
});

async function pull(local: AppData, ...items: Array<Record<string, ThingsWireObject>>) {
    const client = new PagesClient();
    client.pages = [pageOf(...items)];
    return new ThingsCloudSyncEngine(client).pull(local, 0);
}

describe('pull: patches are completed from the existing item', () => {
    it('a title-only patch keeps status, tags, dates and Mindwtr-only fields', async () => {
        const existing = task('t1', {
            status: 'waiting',
            tags: ['#Work'],
            contexts: ['@home'],
            dueDate: '2026-11-09',
            priority: 'high',
            checklist: [{ id: 'c1', title: 'c1', isCompleted: false }],
        } as Partial<Task>);
        const result = await pull(data({ tasks: [existing] }), entry('t1', { tt: 'Renamed' }));
        const merged = result.data.tasks[0] as Task & { priority?: string };
        expect(result.data.tasks).toHaveLength(1);
        expect(merged.title).toBe('Renamed');
        expect(merged.status).toBe('waiting');
        expect(merged.tags).toEqual(['#Work']);
        expect(merged.contexts).toEqual(['@home']);
        expect(merged.dueDate).toBe('2026-11-09');
        expect(merged.priority).toBe('high');
        expect(merged.checklist).toHaveLength(1);
    });

    it('a completion patch (status + stop date only) completes the task and keeps its title', async () => {
        const stop = utcDay(2026, 11, 2) + 3600;
        const result = await pull(
            data({ tasks: [task('t1', { title: 'Keep me' })] }),
            entry('t1', { ss: ThingsTaskStatus.Completed, sp: stop })
        );
        const merged = result.data.tasks[0];
        expect(merged.status).toBe('done');
        expect(merged.title).toBe('Keep me');
        expect(merged.completedAt).toBe(new Date(stop * 1000).toISOString());
    });

    it('moving a task from Someday to Anytime on the server makes it active locally', async () => {
        const result = await pull(
            data({ tasks: [task('t1', { status: 'someday' })] }),
            entry('t1', { st: ThingsTaskSchedule.Anytime })
        );
        expect(result.data.tasks[0].status).toBe('next');
    });

    it('does not turn reference or waiting into next when the Things state did not change', async () => {
        const result = await pull(
            data({ tasks: [task('t1', { status: 'reference' }), task('t2', { status: 'waiting' })] }),
            entry('t1', { tt: 'a' }),
            entry('t2', { st: ThingsTaskSchedule.Anytime, tt: 'b' })
        );
        expect(result.data.tasks.map((t) => t.status)).toEqual(['reference', 'waiting']);
    });

    it('keeps Someday for a task with a future start date when Things reports it as scheduled', async () => {
        const start = daysFromNow(10);
        const existing = task('t1', { status: 'someday', startTime: start });
        const result = await pull(
            data({ tasks: [existing] }),
            entry('t1', { st: ThingsTaskSchedule.Someday, sr: toDayEpochSeconds(start) })
        );
        expect(result.data.tasks[0].status).toBe('someday');
        expect(result.data.tasks[0].startTime).toBe(start);
    });

    it('keeps a local time of day when Things reports the same due day', async () => {
        const existing = task('t1', { dueDate: '2026-11-03T14:00:00' });
        const result = await pull(
            data({ tasks: [existing] }),
            entry('t1', { dd: utcDay(2026, 11, 3) })
        );
        expect(result.data.tasks[0].dueDate).toBe('2026-11-03T14:00:00');
    });

    it('takes the new day when Things reports a different due day', async () => {
        const existing = task('t1', { dueDate: '2026-11-03T14:00:00' });
        const result = await pull(data({ tasks: [existing] }), entry('t1', { dd: utcDay(2026, 11, 8) }));
        expect(result.data.tasks[0].dueDate).toBe('2026-11-08');
    });

    it('restoring from the Things Trash clears deletedAt', async () => {
        const result = await pull(
            data({ tasks: [task('t1', { deletedAt: NOW })] }),
            entry('t1', { tr: false })
        );
        expect(result.data.tasks[0].deletedAt).toBe(undefined);
    });

    it('a patch to a project stays a project (no duplicate task) and keeps its colour', async () => {
        const result = await pull(
            data({ projects: [project('p1', { status: 'waiting' })] }),
            entry('p1', { tt: 'Renamed project' })
        );
        expect(result.data.tasks).toHaveLength(0);
        expect(result.data.projects).toHaveLength(1);
        expect(result.data.projects[0].title).toBe('Renamed project');
        expect(result.data.projects[0].status).toBe('waiting');
        expect(result.data.projects[0].color).toBe('#123456');
    });

    it('a rename of an area keeps its colour and icon', async () => {
        const result = await pull(
            data({ areas: [area('a1')] }),
            entry('a1', { tt: 'Renamed area', ix: 4 }, ThingsItemAction.Modified, 'Area3')
        );
        const merged = result.data.areas[0] as Area & { icon?: string };
        expect(merged.name).toBe('Renamed area');
        expect(merged.order).toBe(4);
        expect(merged.color).toBe('#abcdef');
        expect(merged.icon).toBe('home');
    });

    it('a full Created entry for a task we already have still keeps Mindwtr-only fields', async () => {
        const existing = task('t1', { contexts: ['@office'], priority: 'urgent' } as Partial<Task>);
        const result = await pull(
            data({ tasks: [existing] }),
            entry(
                't1',
                { tt: 'From server', tp: ThingsTaskType.Todo, ss: ThingsTaskStatus.Incomplete, st: ThingsTaskSchedule.Anytime },
                ThingsItemAction.Created
            )
        );
        const merged = result.data.tasks[0] as Task & { priority?: string };
        expect(merged.title).toBe('From server');
        expect(merged.contexts).toEqual(['@office']);
        expect(merged.priority).toBe('urgent');
    });
});

// ---------------------------------------------------------------------------
// Push: no spurious changes
// ---------------------------------------------------------------------------

describe('push: only changes Things can represent are sent', () => {
    const push = async (current: AppData, baseline: AppData) => {
        const client = new PagesClient();
        await new ThingsCloudSyncEngine(client).push(current, baseline, 5);
        return client.commits;
    };

    it('does not send waiting -> next or reference -> next (same Things state)', async () => {
        const commits = await push(
            data({ tasks: [task('t1', { status: 'next' }), task('t2', { status: 'next' })] }),
            data({ tasks: [task('t1', { status: 'waiting' }), task('t2', { status: 'reference' })] })
        );
        expect(commits).toHaveLength(0);
    });

    it('does not send done -> archived (still completed in Things)', async () => {
        const commits = await push(
            data({ tasks: [task('t1', { status: 'archived' })] }),
            data({ tasks: [task('t1', { status: 'done' })] })
        );
        expect(commits).toHaveLength(0);
    });

    it('sends next -> someday and next -> done', async () => {
        const commits = await push(
            data({ tasks: [task('t1', { status: 'someday' }), task('t2', { status: 'done', completedAt: NOW })] }),
            data({ tasks: [task('t1', { status: 'next' }), task('t2', { status: 'next' })] })
        );
        expect(Object.keys(commits[0].changes).sort()).toEqual(['t1', 't2']);
    });

    it('does not send a change of time of day within the same due day', async () => {
        const commits = await push(
            data({ tasks: [task('t1', { dueDate: '2026-11-03T18:00:00' })] }),
            data({ tasks: [task('t1', { dueDate: '2026-11-03T09:00:00' })] })
        );
        expect(commits).toHaveLength(0);
    });

    it('sends a change of due day', async () => {
        const commits = await push(
            data({ tasks: [task('t1', { dueDate: '2026-11-04' })] }),
            data({ tasks: [task('t1', { dueDate: '2026-11-03' })] })
        );
        expect(commits[0].changes.t1.p.dd).toBe(utcDay(2026, 11, 4));
    });

    it('sends nothing right after a pull that only repeated what we already had', async () => {
        const local = data({ tasks: [task('t1', { dueDate: '2026-11-03T14:00:00' })] });
        const client = new PagesClient();
        client.pages = [pageOf(entry('t1', { dd: utcDay(2026, 11, 3) }))];
        const engine = new ThingsCloudSyncEngine(client);
        const pulled = await engine.pull(local, 0);
        await engine.push(pulled.data, local, pulled.newServerIndex);
        expect(client.commits).toHaveLength(0);
    });

    it('treats projects the same way: waiting -> active is not sent, active -> someday is', async () => {
        const commits = await push(
            data({ projects: [project('p1', { status: 'active' }), project('p2', { status: 'someday' })] }),
            data({ projects: [project('p1', { status: 'waiting' }), project('p2', { status: 'active' })] })
        );
        expect(Object.keys(commits[0].changes)).toEqual(['p2']);
    });
});
