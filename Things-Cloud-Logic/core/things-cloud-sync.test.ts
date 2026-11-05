import { describe, expect, it } from 'vitest';
import type { AppData, Area, Project, Section, Task } from '../types';
import { ThingsCloudClient, ThingsCloudError, isThingsCloudConflictError } from './things-cloud-client';
import { ThingsCloudSyncEngine, historyBatchConfirmsChanges } from './things-cloud-sync';
import { ThingsTagRegistry, dedupeTagNames, sameTagSet, tagKey } from './things-tags';
import {
    ThingsItemAction,
    ThingsTaskSchedule,
    ThingsTaskStatus,
    ThingsTaskType,
    type ThingsItemsPageResponse,
    type ThingsWireObject,
} from './things-cloud-types';

type Commit = { changes: Record<string, ThingsWireObject>; ancestorIndex?: number };

function emptyPage(startIndex: number): ThingsItemsPageResponse {
    return {
        items: [],
        'latest-total-content-size': 0,
        'start-total-content-size': 0,
        'end-total-content-size': 0,
        'current-item-index': startIndex,
        schema: 301,
    };
}

function remoteTaskPage(uuid: string, title: string): ThingsItemsPageResponse {
    return {
        items: [
            {
                [uuid]: {
                    t: ThingsItemAction.Created,
                    e: 'Task7',
                    p: {
                        tt: title,
                        tp: ThingsTaskType.Todo,
                        ss: ThingsTaskStatus.Incomplete,
                        st: ThingsTaskSchedule.Inbox,
                        ix: 0,
                    },
                },
            },
        ],
        'latest-total-content-size': 10,
        'start-total-content-size': 0,
        'end-total-content-size': 10,
        'current-item-index': 1,
        schema: 301,
    };
}

/** Client with scripted network responses; records every call. */
class ScriptedClient extends ThingsCloudClient {
    pages: ThingsItemsPageResponse[] = [];
    commitResults: Array<number | Error> = [];
    pageRequests: number[] = [];
    commits: Commit[] = [];

    constructor() {
        super('test@example.com', 'password', 'history-key', 0);
    }

    async getItemsPage(startIndex: number): Promise<ThingsItemsPageResponse> {
        this.pageRequests.push(startIndex);
        return this.pages.shift() ?? emptyPage(startIndex);
    }

    async commit(changes: Record<string, ThingsWireObject>, ancestorIndex?: number): Promise<number> {
        this.commits.push({ changes, ancestorIndex });
        const next = this.commitResults.shift();
        if (next instanceof Error) throw next;
        return next ?? (ancestorIndex ?? 0) + Object.keys(changes).length;
    }
}

const NOW = '2026-11-02T10:00:00.000Z';

function dataWithLocalTask(): AppData {
    return {
        tasks: [
            {
                id: 'local-1',
                title: 'Created locally',
                status: 'inbox',
                tags: [],
                contexts: [],
                createdAt: NOW,
                updatedAt: NOW,
            },
        ],
        projects: [],
        sections: [],
        areas: [],
        settings: {},
    } as unknown as AppData;
}

function emptyData(): AppData {
    return { tasks: [], projects: [], sections: [], areas: [], settings: {} } as unknown as AppData;
}

const conflict = () => new ThingsCloudError('Commit failed with status 409', 409, 'stale ancestor');

describe('isThingsCloudConflictError', () => {
    it('recognises stale-ancestor statuses only', () => {
        expect(isThingsCloudConflictError(new ThingsCloudError('x', 409))).toBe(true);
        expect(isThingsCloudConflictError(new ThingsCloudError('x', 412))).toBe(false);
        expect(isThingsCloudConflictError(new ThingsCloudError('x', 500))).toBe(false);
        expect(isThingsCloudConflictError(new ThingsCloudError('x'))).toBe(false);
        expect(isThingsCloudConflictError(new Error('409'))).toBe(false);
        expect(isThingsCloudConflictError(null)).toBe(false);
    });
});

describe('historyBatchConfirmsChanges', () => {
    it('accepts server-added fields only when one commit contains all expected operations', () => {
        const changes: Record<string, ThingsWireObject> = {
            task1: { t: ThingsItemAction.Modified, e: 'Task7', p: { tt: 'Edited', nt: { text: 'note' } } },
            tag1: { t: ThingsItemAction.Created, e: 'Tag4', p: { tt: 'Work' } },
        };
        expect(historyBatchConfirmsChanges(changes, {
            task1: { ...changes.task1, p: { tt: 'Edited', nt: { text: 'note' }, serverDefault: true } },
            tag1: changes.tag1,
        })).toBe(true);
        expect(historyBatchConfirmsChanges(changes, { task1: changes.task1 })).toBe(false);
        expect(historyBatchConfirmsChanges(changes, {
            task1: changes.task1,
            tag1: { ...changes.tag1, p: { tt: 'Other' } },
        })).toBe(false);
    });

    it('recognizes the server tombstone form of a submitted permanent delete', () => {
        expect(historyBatchConfirmsChanges({
            task1: { t: ThingsItemAction.Deleted, e: 'Task7', p: {} },
        }, {
            generatedTombstone: { t: ThingsItemAction.Created, e: 'Tombstone2', p: { dloid: 'task1', dld: 1 } },
        })).toBe(true);
    });
});

describe('ThingsCloudSyncEngine.sync conflict handling', () => {
    it('re-pulls and retries the push after a stale-ancestor conflict', async () => {
        const client = new ScriptedClient();
        client.pages = [remoteTaskPage('remote-a', 'Remote A'), remoteTaskPage('remote-b', 'Remote B')];
        client.commitResults = [conflict(), 12];
        const engine = new ThingsCloudSyncEngine(client);

        const result = await engine.sync(dataWithLocalTask(), null, 0);

        // Second pull starts where the first one ended.
        expect(client.pageRequests).toEqual([0, 1]);
        // Both commits were made against the index reached by the latest pull.
        expect(client.commits.map((c) => c.ancestorIndex)).toEqual([1, 2]);
        expect(result.conflictRetries).toBe(1);
        expect(result.newServerIndex).toBe(12);
        expect(result.pulledChangesCount).toBe(2);
        expect(result.pushedChangesCount).toBeGreaterThan(0);
        const titles = result.data.tasks.map((t) => t.title).sort();
        expect(titles).toEqual(['Created locally', 'Remote A', 'Remote B']);
    });

    it('clears the prepared outbox only after a definite stale-ancestor rejection', async () => {
        const client = new ScriptedClient();
        client.commitResults = [conflict(), 12];
        let prepared = 0;
        let rejected = 0;
        await new ThingsCloudSyncEngine(client).sync(dataWithLocalTask(), null, 0, {
            onCommitPrepared: async () => { prepared += 1; },
            onCommitRejected: async () => { rejected += 1; },
        });
        expect(prepared).toBe(2);
        expect(rejected).toBe(1);
    });

    it('does not retry errors that are not conflicts', async () => {
        const client = new ScriptedClient();
        client.commitResults = [new ThingsCloudError('Commit failed with status 500', 500)];
        const engine = new ThingsCloudSyncEngine(client);

        await expect(engine.sync(dataWithLocalTask(), null, 0)).rejects.toMatchObject({
            statusCode: 500,
        });
        expect(client.commits).toHaveLength(1);
        expect(client.pageRequests).toHaveLength(1);
    });

    it('gives up after maxConflictRetries and keeps the status code', async () => {
        const client = new ScriptedClient();
        client.commitResults = [conflict(), conflict(), conflict(), conflict()];
        const engine = new ThingsCloudSyncEngine(client);

        const promise = engine.sync(dataWithLocalTask(), null, 0, { maxConflictRetries: 2 });

        await expect(promise).rejects.toBeInstanceOf(ThingsCloudError);
        await expect(promise).rejects.toMatchObject({ statusCode: 409 });
        await expect(promise).rejects.toThrow(/3 attempt/);
        // 1 initial attempt + 2 retries
        expect(client.commits).toHaveLength(3);
    });

    it('with maxConflictRetries = 0 fails on the first conflict', async () => {
        const client = new ScriptedClient();
        client.commitResults = [conflict()];
        const engine = new ThingsCloudSyncEngine(client);

        await expect(
            engine.sync(dataWithLocalTask(), null, 0, { maxConflictRetries: 0 })
        ).rejects.toThrow(/after 1 attempt/);
        expect(client.commits).toHaveLength(1);
    });

    it('skips the commit entirely when there is nothing to push', async () => {
        const client = new ScriptedClient();
        const engine = new ThingsCloudSyncEngine(client);

        const result = await engine.sync(emptyData(), emptyData(), 5);

        expect(client.commits).toHaveLength(0);
        expect(result.conflictRetries).toBe(0);
        expect(result.pushedChangesCount).toBe(0);
        expect(result.newServerIndex).toBe(5);
    });
});

describe('ThingsCloudSyncEngine.pull paging', () => {
    const withoutSizes = (page: ThingsItemsPageResponse): ThingsItemsPageResponse => {
        const copy = { ...page } as Partial<ThingsItemsPageResponse>;
        delete copy['latest-total-content-size'];
        delete copy['start-total-content-size'];
        delete copy['end-total-content-size'];
        return copy as ThingsItemsPageResponse;
    };
    const withSizes = (page: ThingsItemsPageResponse, end: number, latest: number): ThingsItemsPageResponse => ({
        ...page,
        'end-total-content-size': end,
        'latest-total-content-size': latest,
    });

    it('keeps paging when the server omits size fields, until an empty page', async () => {
        const client = new ScriptedClient();
        client.pages = [
            withoutSizes(remoteTaskPage('a', 'A')),
            withoutSizes(remoteTaskPage('b', 'B')),
            // then the default empty page
        ];
        const engine = new ThingsCloudSyncEngine(client);

        const result = await engine.pull(emptyData(), 0);

        expect(client.pageRequests).toEqual([0, 1, 2]);
        expect(result.data.tasks.map((t) => t.title).sort()).toEqual(['A', 'B']);
        expect(result.newServerIndex).toBe(2);
        expect(result.changeCount).toBe(2);
    });

    it('stops early when the server says this page reaches the end', async () => {
        const client = new ScriptedClient();
        client.pages = [withSizes(remoteTaskPage('a', 'A'), 10, 10)];
        const engine = new ThingsCloudSyncEngine(client);

        const result = await engine.pull(emptyData(), 7);

        expect(client.pageRequests).toEqual([7]);
        expect(result.newServerIndex).toBe(8);
    });

    it('continues while end-total-content-size is still below latest', async () => {
        const client = new ScriptedClient();
        client.pages = [
            withSizes(remoteTaskPage('a', 'A'), 5, 10),
            withSizes(remoteTaskPage('b', 'B'), 10, 10),
        ];
        const engine = new ThingsCloudSyncEngine(client);

        const result = await engine.pull(emptyData(), 0);

        expect(client.pageRequests).toEqual([0, 1]);
        expect(result.data.tasks).toHaveLength(2);
        expect(result.newServerIndex).toBe(2);
    });

    it('advances by outer commit entries, not the number of entity UUIDs in those entries', async () => {
        const client = new ScriptedClient();
        client.pages = [
            {
                items: [{
                    a: { t: ThingsItemAction.Created, e: 'Task7', p: { tt: 'A' } },
                    b: { t: ThingsItemAction.Created, e: 'Task7', p: { tt: 'B' } },
                }],
                'latest-total-content-size': 10,
                'start-total-content-size': 0,
                'end-total-content-size': 5,
                'current-item-index': 2,
                schema: 301,
            },
            {
                items: [{ c: { t: ThingsItemAction.Created, e: 'Task7', p: { tt: 'C' } } }],
                'latest-total-content-size': 10,
                'start-total-content-size': 5,
                'end-total-content-size': 10,
                'current-item-index': 2,
                schema: 301,
            },
        ];
        const result = await new ThingsCloudSyncEngine(client).pull(emptyData(), 0);
        expect(client.pageRequests).toEqual([0, 1]);
        expect(result.newServerIndex).toBe(2);
        expect(result.data.tasks.map((task) => task.id).sort()).toEqual(['a', 'b', 'c']);
        expect(result.receivedBatches).toHaveLength(2);
    });

    it('throws instead of looping forever when the server never stops returning items', async () => {
        class EndlessClient extends ScriptedClient {
            async getItemsPage(startIndex: number): Promise<ThingsItemsPageResponse> {
                this.pageRequests.push(startIndex);
                return withoutSizes(remoteTaskPage(`task-${startIndex}`, `Task ${startIndex}`));
            }
        }
        const client = new EndlessClient();
        const engine = new ThingsCloudSyncEngine(client);

        await expect(engine.pull(emptyData(), 0, { maxPages: 3 })).rejects.toThrow(/did not finish after 3 pages/);
        expect(client.pageRequests).toEqual([0, 1, 2]);
    });

    it('passes maxPullPages from sync() down to pull()', async () => {
        class EndlessClient extends ScriptedClient {
            async getItemsPage(startIndex: number): Promise<ThingsItemsPageResponse> {
                this.pageRequests.push(startIndex);
                return withoutSizes(remoteTaskPage(`task-${startIndex}`, `Task ${startIndex}`));
            }
        }
        const client = new EndlessClient();
        const engine = new ThingsCloudSyncEngine(client);

        await expect(engine.sync(emptyData(), emptyData(), 0, { maxPullPages: 2 })).rejects.toThrow(
            /did not finish after 2 pages/
        );
        expect(client.commits).toHaveLength(0);
    });

    it('returns exact received wire records for interrupted-commit reconciliation', async () => {
        const client = new ScriptedClient();
        client.pages = [remoteTaskPage('remote-a', 'A')];
        const result = await new ThingsCloudSyncEngine(client).pull(emptyData(), 0);
        expect(result.receivedChanges['remote-a']).toEqual({
            t: ThingsItemAction.Created,
            e: 'Task7',
            p: { tt: 'A', tp: ThingsTaskType.Todo, ss: ThingsTaskStatus.Incomplete, st: ThingsTaskSchedule.Inbox, ix: 0 },
        });
    });

    it('does not advance over an unknown entity kind', async () => {
        const client = new ScriptedClient();
        client.pages = [{
            ...remoteTaskPage('future', 'Future'),
            items: [{ future: { t: ThingsItemAction.Created, e: 'Task8' as never, p: { tt: 'Future' } } }],
        }];
        await expect(new ThingsCloudSyncEngine(client).pull(emptyData(), 0)).rejects.toThrow(/unsupported history item/);
    });

    it('keeps local data and refuses to overwrite a same-entity concurrent edit', async () => {
        const client = new ScriptedClient();
        client.pages = [remoteTaskPage('t1', 'Remote title')];
        const baseline = mkData({ tasks: [mkTask('t1', { title: 'Baseline' })] });
        const local = mkData({ tasks: [mkTask('t1', { title: 'Local title' })] });
        await expect(new ThingsCloudSyncEngine(client).sync(local, baseline, 0)).rejects.toMatchObject({
            name: 'ThingsCloudConcurrentEditError',
            entityIds: ['t1'],
            conflicts: [{ id: 't1', local: { title: 'Local title' }, remote: { title: 'Remote title' } }],
            newServerIndex: 1,
        });
        expect(client.commits).toHaveLength(0);
    });

    it('maps a Things Tombstone2 to a durable local deletion marker', async () => {
        const client = new ScriptedClient();
        client.pages = [{
            ...remoteTaskPage('target', 'Deleted'),
            items: [
                { target: { t: ThingsItemAction.Created, e: 'Task7', p: { tt: 'Deleted', tp: ThingsTaskType.Todo, ss: ThingsTaskStatus.Incomplete, st: ThingsTaskSchedule.Inbox } } },
                { tombstone: { t: ThingsItemAction.Created, e: 'Tombstone2', p: { dloid: 'target', dld: 1_798_790_400 } } },
            ],
            'latest-total-content-size': 20,
            'end-total-content-size': 20,
            'current-item-index': 2,
        }];
        const result = await new ThingsCloudSyncEngine(client).pull(emptyData(), 0);
        expect(result.newServerIndex).toBe(2);
        expect(result.data.tasks[0]).toMatchObject({ deletedAt: expect.any(String), purgedAt: expect.any(String) });
    });

    it('does not advance over malformed or unresolvable tombstones', async () => {
        for (const props of [{}, { dloid: 'missing-entity' }]) {
            const client = new ScriptedClient();
            client.pages = [{
                ...remoteTaskPage('tombstone', 'Tombstone'),
                items: [{ tombstone: { t: ThingsItemAction.Created, e: 'Tombstone2', p: props } }],
            }];
            await expect(new ThingsCloudSyncEngine(client).pull(emptyData(), 0)).rejects.toThrow(/tombstone|unknown entity/i);
        }
    });
});

// ---------------------------------------------------------------------------
// Deletions, areas and checklist removal
// ---------------------------------------------------------------------------

const mkTask = (id: string, extra: Partial<Task> = {}): Task =>
    ({ id, title: id, status: 'next', tags: [], contexts: [], createdAt: NOW, updatedAt: NOW, ...extra }) as Task;
const mkProject = (id: string, extra: Partial<Project> = {}): Project =>
    ({ id, title: id, status: 'active', color: '#000000', order: 0, tagIds: [], createdAt: NOW, updatedAt: NOW, ...extra }) as Project;
const mkArea = (id: string, extra: Partial<Area> = {}): Area =>
    ({ id, name: id, order: 0, createdAt: NOW, updatedAt: NOW, ...extra }) as Area;
const mkSection = (id: string, extra: Partial<Section> = {}): Section =>
    ({ id, projectId: 'p1', title: id, order: 0, createdAt: NOW, updatedAt: NOW, ...extra }) as Section;
const mkData = (parts: { tasks?: Task[]; projects?: Project[]; areas?: Area[]; sections?: Section[] }): AppData =>
    ({ tasks: [], projects: [], sections: [], areas: [], settings: {}, ...parts }) as unknown as AppData;

const lastChanges = (client: ScriptedClient): Record<string, ThingsWireObject> =>
    client.commits[client.commits.length - 1]?.changes ?? {};

describe('ThingsCloudSyncEngine.push deletions', () => {
    const setup = () => {
        const client = new ScriptedClient();
        return { client, engine: new ThingsCloudSyncEngine(client) };
    };

    it('sends a trashed task to the Things Trash (tr: true) instead of deleting it', async () => {
        const { client, engine } = setup();
        await engine.push(
            mkData({ tasks: [mkTask('t1', { deletedAt: NOW })] }),
            mkData({ tasks: [mkTask('t1')] }),
            5
        );
        const change = lastChanges(client).t1;
        expect(change.t).toBe(ThingsItemAction.Modified);
        expect(change.p.tr).toBe(true);
    });

    it('sends tr: false when a trashed task is restored', async () => {
        const { client, engine } = setup();
        await engine.push(
            mkData({ tasks: [mkTask('t1')] }),
            mkData({ tasks: [mkTask('t1', { deletedAt: NOW })] }),
            5
        );
        const change = lastChanges(client).t1;
        expect(change.t).toBe(ThingsItemAction.Modified);
        expect(change.p.tr).toBe(false);
    });

    it('sends nothing for a task that was trashed before it was ever synced', async () => {
        const { client, engine } = setup();
        await engine.push(mkData({ tasks: [mkTask('t1', { deletedAt: NOW })] }), mkData({}), 5);
        expect(client.commits).toHaveLength(0);
    });

    it('permanently deletes a task that was already in the trash and is now purged', async () => {
        const { client, engine } = setup();
        await engine.push(
            mkData({ tasks: [mkTask('t1', { deletedAt: NOW, purgedAt: NOW })] }),
            mkData({ tasks: [mkTask('t1', { deletedAt: NOW })] }),
            5
        );
        const change = lastChanges(client).t1;
        expect(change.t).toBe(ThingsItemAction.Deleted);
        expect(change.e).toBe('Task7');
    });

    it('does not delete on Things when the task was purged without ever being trashed (archive retention)', async () => {
        const { client, engine } = setup();
        await engine.push(
            mkData({ tasks: [mkTask('t1', { status: 'archived', purgedAt: NOW })] }),
            mkData({ tasks: [mkTask('t1', { status: 'archived' })] }),
            5
        );
        expect(client.commits).toHaveLength(0);
    });

    it('trashes (not deletes) when a task is deleted and purged within one sync cycle', async () => {
        const { client, engine } = setup();
        await engine.push(
            mkData({ tasks: [mkTask('t1', { deletedAt: NOW, purgedAt: NOW })] }),
            mkData({ tasks: [mkTask('t1')] }),
            5
        );
        const change = lastChanges(client).t1;
        expect(change.t).toBe(ThingsItemAction.Modified);
        expect(change.p.tr).toBe(true);
    });

    it('deletes a trashed task that vanished from the data, but not an untrashed one', async () => {
        const { client, engine } = setup();
        await engine.push(
            mkData({}),
            mkData({ tasks: [mkTask('trashed', { deletedAt: NOW }), mkTask('plain')] }),
            5
        );
        const changes = lastChanges(client);
        expect(Object.keys(changes)).toEqual(['trashed']);
        expect(changes.trashed.t).toBe(ThingsItemAction.Deleted);
    });

    it('does not echo deletions for ids the pull just touched', async () => {
        const { client, engine } = setup();
        const skipIds = new Set(['t1', 'p1', 'a1']);
        await engine.push(
            mkData({ areas: [mkArea('a1', { deletedAt: NOW })] }),
            mkData({
                tasks: [mkTask('t1', { deletedAt: NOW })],
                projects: [mkProject('p1', { deletedAt: NOW })],
                areas: [mkArea('a1')],
            }),
            5,
            { skipIds }
        );
        expect(client.commits).toHaveLength(0);
    });

    it('deletes checklist items the user removed', async () => {
        const { client, engine } = setup();
        const item = (id: string) => ({ id, title: id, isCompleted: false });
        await engine.push(
            mkData({ tasks: [mkTask('t1', { checklist: [item('c1')] })] }),
            mkData({ tasks: [mkTask('t1', { checklist: [item('c1'), item('c2')] })] }),
            5
        );
        const changes = lastChanges(client);
        expect(Object.keys(changes)).toEqual(['c2']);
        expect(changes.c2.t).toBe(ThingsItemAction.Deleted);
        expect(changes.c2.e).toBe('ChecklistItem3');
    });

    it('applies the same trash / purge rules to projects', async () => {
        const { client, engine } = setup();
        await engine.push(
            mkData({ projects: [mkProject('p1', { deletedAt: NOW }), mkProject('p2', { deletedAt: NOW, purgedAt: NOW })] }),
            mkData({ projects: [mkProject('p1'), mkProject('p2', { deletedAt: NOW })] }),
            5
        );
        const changes = lastChanges(client);
        expect(changes.p1.t).toBe(ThingsItemAction.Modified);
        expect(changes.p1.p.tr).toBe(true);
        expect(changes.p2.t).toBe(ThingsItemAction.Deleted);
    });
});

describe('ThingsCloudSyncEngine.push areas', () => {
    const setup = () => {
        const client = new ScriptedClient();
        return { client, engine: new ThingsCloudSyncEngine(client) };
    };

    it('creates new areas', async () => {
        const { client, engine } = setup();
        await engine.push(mkData({ areas: [mkArea('a1', { name: 'Home', order: 3 })] }), mkData({}), 5);
        const change = lastChanges(client).a1;
        expect(change.t).toBe(ThingsItemAction.Created);
        expect(change.e).toBe('Area3');
        expect(change.p.tt).toBe('Home');
        expect(change.p.ix).toBe(3);
    });

    it('updates renamed or reordered areas and leaves unchanged ones alone', async () => {
        const { client, engine } = setup();
        await engine.push(
            mkData({ areas: [mkArea('a1', { name: 'Renamed' }), mkArea('a2', { order: 4 }), mkArea('a3')] }),
            mkData({ areas: [mkArea('a1'), mkArea('a2'), mkArea('a3')] }),
            5
        );
        const changes = lastChanges(client);
        expect(Object.keys(changes).sort()).toEqual(['a1', 'a2']);
        expect(changes.a1.t).toBe(ThingsItemAction.Modified);
        expect(changes.a1.p.tt).toBe('Renamed');
    });

    it('deletes a deleted area outright, since Things has no area trash', async () => {
        const { client, engine } = setup();
        await engine.push(
            mkData({ areas: [mkArea('a1', { deletedAt: NOW })] }),
            mkData({ areas: [mkArea('a1')] }),
            5
        );
        const change = lastChanges(client).a1;
        expect(change.t).toBe(ThingsItemAction.Deleted);
        expect(change.e).toBe('Area3');
    });

    it('ignores an area that was created and deleted before the first sync', async () => {
        const { client, engine } = setup();
        await engine.push(mkData({ areas: [mkArea('a1', { deletedAt: NOW })] }), mkData({}), 5);
        expect(client.commits).toHaveLength(0);
    });

    it('does not repeat an area delete that was already synced', async () => {
        const { client, engine } = setup();
        await engine.push(
            mkData({ areas: [mkArea('a1', { deletedAt: NOW })] }),
            mkData({ areas: [mkArea('a1', { deletedAt: NOW })] }),
            5
        );
        expect(client.commits).toHaveLength(0);
    });

    it('recreates an area that was restored after its delete was synced', async () => {
        const { client, engine } = setup();
        await engine.push(
            mkData({ areas: [mkArea('a1')] }),
            mkData({ areas: [mkArea('a1', { deletedAt: NOW })] }),
            5
        );
        expect(lastChanges(client).a1.t).toBe(ThingsItemAction.Created);
    });
});

describe('ThingsCloudSyncEngine.push headings', () => {
    it('creates, modifies, trashes, and deletes Things heading entities', async () => {
        const client = new ScriptedClient();
        const engine = new ThingsCloudSyncEngine(client);
        const created = mkSection('s1', { title: 'Planning', description: 'Notes', order: 4 });
        await engine.push(mkData({ sections: [created] }), mkData({}), 0);
        expect(lastChanges(client).s1).toMatchObject({ t: ThingsItemAction.Created, e: 'Task7', p: { tp: ThingsTaskType.Heading, tt: 'Planning', nt: { text: 'Notes' }, pr: ['p1'], ix: 4 } });

        await engine.push(mkData({ sections: [{ ...created, title: 'Plan', order: 5 }] }), mkData({ sections: [created] }), 1);
        expect(lastChanges(client).s1).toMatchObject({ t: ThingsItemAction.Modified, e: 'Task7', p: { tp: ThingsTaskType.Heading, tt: 'Plan', ix: 5 } });

        await engine.push(mkData({ sections: [{ ...created, deletedAt: NOW }] }), mkData({ sections: [created] }), 2);
        expect(lastChanges(client).s1).toMatchObject({ t: ThingsItemAction.Modified, p: { tr: true } });

        await engine.push(mkData({ sections: [] }), mkData({ sections: [{ ...created, deletedAt: NOW }] }), 3);
        expect(lastChanges(client).s1).toMatchObject({ t: ThingsItemAction.Deleted, e: 'Task7' });
    });

    it('durably records the outgoing commit plan before the client sends it', async () => {
        const client = new ScriptedClient();
        const engine = new ThingsCloudSyncEngine(client);
        let prepared = false;
        await engine.sync(mkData({ tasks: [mkTask('t1')] }), mkData({}), 0, {
            onCommitPrepared: async (changes, ancestorIndex) => {
                expect(changes.t1.p.tt).toBe('t1');
                expect(ancestorIndex).toBe(0);
                prepared = true;
            },
        });
        expect(prepared).toBe(true);
    });

    it('preserves remote fields Mindwtr does not model when sending a supported task edit', async () => {
        const client = new ScriptedClient();
        const engine = new ThingsCloudSyncEngine(client, null, {
            t1: { t: ThingsItemAction.Modified, e: 'Task6', p: { tt: 'Old', rp: { interval: 2 }, rmd: 2_000_000_000, x_future: 'keep' } },
        });
        const before = mkTask('t1', { title: 'Old' });
        const after = mkTask('t1', { title: 'New' });
        await engine.push(mkData({ tasks: [after] }), mkData({ tasks: [before] }), 5);
        expect(lastChanges(client).t1.p).toMatchObject({ tt: 'New', rp: { interval: 2 }, rmd: 2_000_000_000, x_future: 'keep' });
    });
});

describe('ThingsCloudSyncEngine.pull deletions and checklists', () => {
    const entry = (uuid: string, wire: ThingsWireObject) => ({ [uuid]: wire });
    const page = (...items: Array<Record<string, ThingsWireObject>>): ThingsItemsPageResponse => ({
        items,
        'latest-total-content-size': 1,
        'start-total-content-size': 0,
        'end-total-content-size': 1,
        'current-item-index': items.length,
        schema: 301,
    });
    const item = (id: string, title: string) => ({ id, title, isCompleted: false });

    it('maps a trashed task (tr: true) to a locally deleted task', async () => {
        const client = new ScriptedClient();
        client.pages = [
            page(
                entry('t1', {
                    t: ThingsItemAction.Created,
                    e: 'Task7',
                    p: { tt: 'Gone', tp: ThingsTaskType.Todo, ss: ThingsTaskStatus.Incomplete, st: ThingsTaskSchedule.Anytime, tr: true },
                })
            ),
        ];
        const result = await new ThingsCloudSyncEngine(client).pull(mkData({}), 0);
        expect(result.data.tasks).toHaveLength(1);
        expect(Boolean(result.data.tasks[0].deletedAt)).toBe(true);
    });

    it('does not mark ordinary tasks as deleted', async () => {
        const client = new ScriptedClient();
        client.pages = [page(...remoteTaskPage('t1', 'Alive').items)];
        const result = await new ThingsCloudSyncEngine(client).pull(mkData({}), 0);
        expect(result.data.tasks[0].deletedAt).toBe(undefined);
    });

    it('reports every uuid the server mentioned in touchedIds', async () => {
        const client = new ScriptedClient();
        client.pages = [
            page(
                entry('t1', { t: ThingsItemAction.Deleted, e: 'Task7', p: {} }),
                entry('a1', { t: ThingsItemAction.Deleted, e: 'Area3', p: {} })
            ),
        ];
        const result = await new ThingsCloudSyncEngine(client).pull(
            mkData({ tasks: [mkTask('t1')], areas: [mkArea('a1')] }),
            0
        );
        expect([...result.touchedIds].sort()).toEqual(['a1', 't1']);
        expect(result.data.tasks[0]).toMatchObject({ deletedAt: expect.any(String), purgedAt: expect.any(String) });
        expect(result.data.areas[0]).toMatchObject({ deletedAt: expect.any(String) });
    });

    it('keeps existing checklist items when only one item changes', async () => {
        const client = new ScriptedClient();
        client.pages = [
            page(
                entry('c1', { t: ThingsItemAction.Modified, e: 'ChecklistItem3', p: { tt: 'c1 renamed', ts: ['t1'] } })
            ),
        ];
        const result = await new ThingsCloudSyncEngine(client).pull(
            mkData({ tasks: [mkTask('t1', { checklist: [item('c1', 'c1'), item('c2', 'c2')] })] }),
            0
        );
        const checklist = result.data.tasks[0].checklist ?? [];
        expect(checklist.map((c) => c.id).sort()).toEqual(['c1', 'c2']);
        expect(checklist.find((c) => c.id === 'c1')?.title).toBe('c1 renamed');
    });

    it('removes a checklist item the server deleted, even though the delete entry has no props', async () => {
        const client = new ScriptedClient();
        client.pages = [page(entry('c2', { t: ThingsItemAction.Deleted, e: 'ChecklistItem3', p: {} }))];
        const result = await new ThingsCloudSyncEngine(client).pull(
            mkData({ tasks: [mkTask('t1', { checklist: [item('c1', 'c1'), item('c2', 'c2')] })] }),
            0
        );
        expect((result.data.tasks[0].checklist ?? []).map((c) => c.id)).toEqual(['c1']);
    });

    it('sync() does not push a delete back for something the server just deleted', async () => {
        const client = new ScriptedClient();
        client.pages = [page(entry('t1', { t: ThingsItemAction.Deleted, e: 'Task7', p: {} }))];
        const live = mkData({ tasks: [mkTask('t1')] });
        const result = await new ThingsCloudSyncEngine(client).sync(live, live, 0);
        expect(client.commits).toHaveLength(0);
        expect(result.pushedChangesCount).toBe(0);
        expect(result.data.tasks[0]).toMatchObject({ deletedAt: expect.any(String), purgedAt: expect.any(String) });
    });

    it('a later non-trashed task update restores the local tombstone state', async () => {
        const client = new ScriptedClient();
        client.pages = [
            page(entry('t1', { t: ThingsItemAction.Modified, e: 'Task7', p: {
                tt: 'Restored', tp: ThingsTaskType.Todo, ss: ThingsTaskStatus.Incomplete,
                st: ThingsTaskSchedule.Anytime, tr: false,
            } })),
        ];
        const result = await new ThingsCloudSyncEngine(client).pull(
            mkData({ tasks: [mkTask('t1', { deletedAt: NOW, purgedAt: NOW })] }), 0);
        expect(result.data.tasks[0]).toMatchObject({ title: 'Restored', deletedAt: undefined, purgedAt: undefined });
    });

    it('sync() does not undo a checklist deletion the server just made', async () => {
        const client = new ScriptedClient();
        client.pages = [page(entry('c2', { t: ThingsItemAction.Deleted, e: 'ChecklistItem3', p: {} }))];
        const before = mkData({ tasks: [mkTask('t1', { checklist: [item('c1', 'c1'), item('c2', 'c2')] })] });
        await new ThingsCloudSyncEngine(client).sync(before, before, 0);
        expect(client.commits).toHaveLength(0);
    });
});

// ---------------------------------------------------------------------------
// Tags
// ---------------------------------------------------------------------------

describe('tag helpers and registry', () => {
    it('matches tags ignoring case, # prefix and order', () => {
        expect(tagKey(' #Work ')).toBe('work');
        expect(sameTagSet(['#Work', '#home'], ['Home', '#work'])).toBe(true);
        expect(sameTagSet(['#a'], ['#a', '#b'])).toBe(false);
        expect(sameTagSet(undefined, [])).toBe(true);
        expect(dedupeTagNames(['#Work', 'work', '', '#Home'])).toEqual(['#Work', '#Home']);
    });

    it('reuses a known tag and mints a pending one for an unknown name', () => {
        const registry = new ThingsTagRegistry({ titles: { tagA: 'Work' }, orphans: {} });
        const uuids = registry.uuidsFor('t1', ['#work', '#Errands'], () => 'minted-1');
        expect(uuids).toEqual(['tagA', 'minted-1']);
        expect(registry.pendingTags()).toEqual([{ uuid: 'minted-1', title: 'Errands' }]);
        // Unconfirmed tags are not persisted
        expect(registry.snapshot().titles).toEqual({ tagA: 'Work' });
        registry.confirmPending();
        expect(registry.snapshot().titles).toEqual({ tagA: 'Work', 'minted-1': 'Errands' });
    });

    it('drops its own unsent tag when the server creates one with the same title', () => {
        const registry = new ThingsTagRegistry();
        registry.uuidsFor('t1', ['#Errands'], () => 'mine');
        registry.set('theirs', 'errands');
        expect(registry.pendingTags()).toEqual([]);
        expect(registry.uuidsFor('t1', ['#Errands'], () => 'unused')).toEqual(['theirs']);
    });

    it('ignores corrupt saved state', () => {
        const registry = new ThingsTagRegistry({ titles: { a: 5 as unknown as string, b: '  ' }, orphans: { t1: 'x' as unknown as string[] } });
        expect(registry.snapshot()).toEqual({ titles: {}, orphans: {} });
    });
});

describe('ThingsCloudSyncEngine tags: pull', () => {
    const entry = (uuid: string, wire: ThingsWireObject) => ({ [uuid]: wire });
    const page = (...items: Array<Record<string, ThingsWireObject>>): ThingsItemsPageResponse => ({
        items,
        'latest-total-content-size': 1,
        'start-total-content-size': 0,
        'end-total-content-size': 1,
        'current-item-index': items.length,
        schema: 301,
    });
    const tagEvent = (title: string, action: number = ThingsItemAction.Created): ThingsWireObject => ({
        t: action as ThingsWireObject['t'],
        e: 'Tag4',
        p: action === ThingsItemAction.Deleted ? {} : { tt: title },
    });
    const taskEvent = (title: string, tg?: string[]): ThingsWireObject => ({
        t: ThingsItemAction.Modified,
        e: 'Task7',
        p: {
            tt: title,
            tp: ThingsTaskType.Todo,
            ss: ThingsTaskStatus.Incomplete,
            st: ThingsTaskSchedule.Anytime,
            ...(tg ? { tg } : {}),
        },
    });

    it('resolves tag uuids to #names, even when the tag entity arrives after the task', async () => {
        const client = new ScriptedClient();
        client.pages = [page(entry('t1', taskEvent('Task', ['tagA'])), entry('tagA', tagEvent('Work')))];
        const engine = new ThingsCloudSyncEngine(client);
        const result = await engine.pull(mkData({}), 0);
        expect(result.data.tasks[0].tags).toEqual(['#Work']);
        expect(engine.tagState.titles).toEqual({ tagA: 'Work' });
    });

    it('uses tag names remembered from an earlier sync', async () => {
        const client = new ScriptedClient();
        client.pages = [page(entry('t1', taskEvent('Task', ['tagA'])))];
        const engine = new ThingsCloudSyncEngine(client, { titles: { tagA: 'Work' }, orphans: {} });
        const result = await engine.pull(mkData({}), 5);
        expect(result.data.tasks[0].tags).toEqual(['#Work']);
    });

    it('resolves project tags the same way', async () => {
        const client = new ScriptedClient();
        client.pages = [
            page(
                entry('p1', {
                    t: ThingsItemAction.Created,
                    e: 'Task7',
                    p: { tt: 'Proj', tp: ThingsTaskType.Project, ss: ThingsTaskStatus.Incomplete, st: ThingsTaskSchedule.Anytime, tg: ['tagA'] },
                })
            ),
        ];
        const engine = new ThingsCloudSyncEngine(client, { titles: { tagA: 'Work' }, orphans: {} });
        const result = await engine.pull(mkData({}), 0);
        expect(result.data.projects[0].tagIds).toEqual(['#Work']);
    });

    it('never leaks a raw uuid into the tags, and keeps it for the next push', async () => {
        const client = new ScriptedClient();
        client.pages = [page(entry('t1', taskEvent('Task', ['unknownX', 'tagA'])))];
        const engine = new ThingsCloudSyncEngine(client, { titles: { tagA: 'Work' }, orphans: {} });
        const pulled = (await engine.pull(mkData({}), 0)).data;
        expect(pulled.tasks[0].tags).toEqual(['#Work']);
        expect(engine.tagState.orphans).toEqual({ t1: ['unknownX'] });

        // An unrelated edit must not strip the tag we could not name.
        const edited = { ...pulled, tasks: [{ ...pulled.tasks[0], title: 'Renamed' }] };
        await engine.push(edited, pulled, 1);
        expect(lastChanges(client).t1.p.tg).toEqual(['tagA', 'unknownX']);
    });

    it('keeps local tags when a task event carries no tag list', async () => {
        const client = new ScriptedClient();
        client.pages = [page(entry('t1', taskEvent('Edited elsewhere')))];
        const engine = new ThingsCloudSyncEngine(client, { titles: { tagA: 'Work' }, orphans: {} });
        const result = await engine.pull(mkData({ tasks: [mkTask('t1', { tags: ['#Work'] })] }), 0);
        expect(result.data.tasks[0].title).toBe('Edited elsewhere');
        expect(result.data.tasks[0].tags).toEqual(['#Work']);
    });

    it('applies a server-side tag rename to tasks the pull did not otherwise touch', async () => {
        const client = new ScriptedClient();
        client.pages = [page(entry('tagA', tagEvent('Job', ThingsItemAction.Modified)))];
        const engine = new ThingsCloudSyncEngine(client, { titles: { tagA: 'Work' }, orphans: {} });
        const result = await engine.pull(
            mkData({
                tasks: [mkTask('t1', { tags: ['#Work', '#Home'] })],
                projects: [mkProject('p1', { tagIds: ['#work'] })],
            }),
            0
        );
        expect(result.data.tasks[0].tags).toEqual(['#Job', '#Home']);
        expect(result.data.projects[0].tagIds).toEqual(['#Job']);
        expect(engine.tagState.titles).toEqual({ tagA: 'Job' });
    });

    it('removes a tag the server deleted from local tasks', async () => {
        const client = new ScriptedClient();
        client.pages = [page(entry('tagA', tagEvent('', ThingsItemAction.Deleted)))];
        const engine = new ThingsCloudSyncEngine(client, { titles: { tagA: 'Work' }, orphans: {} });
        const result = await engine.pull(mkData({ tasks: [mkTask('t1', { tags: ['#Work', '#Home'] })] }), 0);
        expect(result.data.tasks[0].tags).toEqual(['#Home']);
        expect(engine.tagState.titles).toEqual({});
    });

    it('does not re-attach a deleted tag to a task that still listed it', async () => {
        const client = new ScriptedClient();
        client.pages = [
            page(entry('t1', taskEvent('Task', ['tagA'])), entry('tagA', tagEvent('', ThingsItemAction.Deleted))),
        ];
        const engine = new ThingsCloudSyncEngine(client, { titles: { tagA: 'Work' }, orphans: {} });
        const result = await engine.pull(mkData({}), 0);
        expect(result.data.tasks[0].tags).toEqual([]);
        expect(engine.tagState.orphans).toEqual({});
    });

    it('loadTagRegistry reads tags from the full history, and repairLegacyTagIds fixes old raw uuids', async () => {
        const client = new ScriptedClient();
        client.pages = [
            withSizesFor(page(entry('t1', taskEvent('Old')), entry('tagA', tagEvent('Work')))),
            withSizesFor(page(entry('tagB', tagEvent('Home')))),
        ];
        const engine = new ThingsCloudSyncEngine(client);
        await engine.loadTagRegistry();
        expect(client.pageRequests).toEqual([0, 2, 3]);
        expect(engine.tagState.titles).toEqual({ tagA: 'Work', tagB: 'Home' });

        const repaired = engine.repairLegacyTagIds(
            mkData({
                tasks: [mkTask('t1', { tags: ['tagA', '#Keep', 'tagB'] }), mkTask('t2', { tags: ['#Plain'] })],
                projects: [mkProject('p1', { tagIds: ['tagB'] })],
            })
        );
        expect(repaired.tasks[0].tags).toEqual(['#Work', '#Keep', '#Home']);
        expect(repaired.tasks[1].tags).toEqual(['#Plain']);
        expect(repaired.projects[0].tagIds).toEqual(['#Home']);
    });

    // Pages from loadTagRegistry must not claim to be the last page.
    function withSizesFor(p: ThingsItemsPageResponse): ThingsItemsPageResponse {
        return { ...p, 'end-total-content-size': 0, 'latest-total-content-size': 99 };
    }
});

describe('ThingsCloudSyncEngine tags: push', () => {
    const setup = (state?: { titles: Record<string, string>; orphans: Record<string, string[]> }) => {
        const client = new ScriptedClient();
        return { client, engine: new ThingsCloudSyncEngine(client, state) };
    };
    const tagChanges = (changes: Record<string, ThingsWireObject>) =>
        Object.entries(changes).filter(([, wire]) => wire.e === 'Tag4');

    it('sends the uuid of an existing tag, matching the name case-insensitively', async () => {
        const { client, engine } = setup({ titles: { tagA: 'Work' }, orphans: {} });
        await engine.push(mkData({ tasks: [mkTask('t1', { tags: ['#work'] })] }), mkData({}), 5);
        const changes = lastChanges(client);
        expect(changes.t1.p.tg).toEqual(['tagA']);
        expect(tagChanges(changes)).toHaveLength(0);
    });

    it('creates a tag that does not exist yet, in the same commit', async () => {
        const { client, engine } = setup();
        await engine.push(mkData({ tasks: [mkTask('t1', { tags: ['#Errands'] })] }), mkData({}), 5);
        const changes = lastChanges(client);
        const created = tagChanges(changes);
        expect(created).toHaveLength(1);
        const [uuid, wire] = created[0];
        expect(wire.t).toBe(ThingsItemAction.Created);
        expect(wire.p.tt).toBe('Errands');
        expect(changes.t1.p.tg).toEqual([uuid]);
        expect(engine.tagState.titles[uuid]).toBe('Errands');
    });

    it('creates a shared new tag only once', async () => {
        const { client, engine } = setup();
        await engine.push(
            mkData({
                tasks: [mkTask('t1', { tags: ['#Errands'] }), mkTask('t2', { tags: ['errands'] })],
                projects: [mkProject('p1', { tagIds: ['#ERRANDS'] })],
            }),
            mkData({}),
            5
        );
        const changes = lastChanges(client);
        expect(tagChanges(changes)).toHaveLength(1);
        expect(changes.t1.p.tg).toEqual(changes.t2.p.tg);
        expect(changes.t1.p.tg).toEqual(changes.p1.p.tg);
    });

    it('detects a change that only touches tags', async () => {
        const { client, engine } = setup({ titles: { tagA: 'Work' }, orphans: {} });
        await engine.push(
            mkData({ tasks: [mkTask('t1', { tags: ['#Work'] })] }),
            mkData({ tasks: [mkTask('t1', { tags: [] })] }),
            5
        );
        expect(lastChanges(client).t1.t).toBe(ThingsItemAction.Modified);
        expect(lastChanges(client).t1.p.tg).toEqual(['tagA']);
    });

    it('does not treat reordered or re-cased tags as a change', async () => {
        const { client, engine } = setup({ titles: { tagA: 'Work' }, orphans: {} });
        await engine.push(
            mkData({ tasks: [mkTask('t1', { tags: ['#home', '#work'] })] }),
            mkData({ tasks: [mkTask('t1', { tags: ['#Work', '#Home'] })] }),
            5
        );
        expect(client.commits).toHaveLength(0);
    });

    it('re-sends the tag creation, with the same uuid, after a failed commit', async () => {
        const { client, engine } = setup();
        client.commitResults = [new Error('boom')];
        const current = mkData({ tasks: [mkTask('t1', { tags: ['#Errands'] })] });

        await expect(engine.push(current, mkData({}), 5)).rejects.toMatchObject({ message: 'boom' });
        // The failed attempt must not be saved as known.
        expect(engine.tagState.titles).toEqual({});

        await engine.push(current, mkData({}), 5);
        const [first, second] = client.commits;
        const firstTag = Object.entries(first.changes).find(([, w]) => w.e === 'Tag4');
        const secondTag = Object.entries(second.changes).find(([, w]) => w.e === 'Tag4');
        expect(firstTag?.[0]).toBe(secondTag?.[0]);
        expect(Object.keys(engine.tagState.titles)).toEqual([secondTag?.[0]]);
    });

    it('sync() sends a tag-only change back to the server with the tag created', async () => {
        const client = new ScriptedClient();
        const engine = new ThingsCloudSyncEngine(client);
        const baseline = mkData({ tasks: [mkTask('t1')] });
        const current = mkData({ tasks: [mkTask('t1', { tags: ['#Errands'] })] });
        const result = await engine.sync(current, baseline, 0);
        expect(result.pushedChangesCount).toBe(2); // the task and the new tag
        expect(tagChanges(lastChanges(client))).toHaveLength(1);
    });
});
