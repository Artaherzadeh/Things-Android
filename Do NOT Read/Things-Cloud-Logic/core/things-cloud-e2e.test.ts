import { describe, expect, it } from 'vitest';
import type { AppData, Task } from '../types';
import { ThingsCloudClient } from './things-cloud-client';
import { ThingsCloudSyncEngine } from './things-cloud-sync';
import {
    ThingsItemAction,
    ThingsTaskSchedule,
    ThingsTaskStatus,
    ThingsTaskType,
    type ThingsItemsPageResponse,
    type ThingsWireObject,
} from './things-cloud-types';
import { generateThingsId, isValidThingsId } from './things-id';

/**
 * End-to-end scenario (formerly verify-sync-engine.mjs): a task is created on
 * another device, pulled, completed locally together with a new local task, and
 * pushed back.
 */
class MockThingsCloudClient extends ThingsCloudClient {
    mockPages: ThingsItemsPageResponse[] = [];
    committedChanges: Array<{ changes: Record<string, ThingsWireObject>; ancestorIndex?: number }> = [];

    constructor() {
        super('test@example.com', 'password123', 'mock-history-key', 10);
    }

    async getItemsPage(startIndex: number): Promise<ThingsItemsPageResponse> {
        return (
            this.mockPages.shift() ?? {
                items: [],
                'latest-total-content-size': 100,
                'start-total-content-size': 100,
                'end-total-content-size': 100,
                'current-item-index': startIndex,
                schema: 301,
            }
        );
    }

    async commit(changes: Record<string, ThingsWireObject>, ancestorIndex?: number): Promise<number> {
        this.committedChanges.push({ changes, ancestorIndex });
        return (ancestorIndex || 0) + Object.keys(changes).length;
    }
}

describe('Things Cloud sync: pull from another device, edit locally, push back', () => {
    it('round-trips an iPad task and a new Android task', async () => {
        const client = new MockThingsCloudClient();
        const engine = new ThingsCloudSyncEngine(client);
        const initialData = { tasks: [], projects: [], sections: [], areas: [], settings: {} } as unknown as AppData;

        // 1. The iPad added a task.
        const ipadTaskId = generateThingsId();
        expect(isValidThingsId(ipadTaskId)).toBe(true);
        client.mockPages.push({
            items: [
                {
                    [ipadTaskId]: {
                        t: ThingsItemAction.Created,
                        e: 'Task7',
                        p: {
                            tt: 'Task from iPad',
                            nt: { text: 'Synced via Things Cloud' },
                            tp: ThingsTaskType.Todo,
                            ss: ThingsTaskStatus.Incomplete,
                            st: ThingsTaskSchedule.Inbox,
                            ix: 0,
                        },
                    },
                },
            ],
            'latest-total-content-size': 100,
            'start-total-content-size': 0,
            'end-total-content-size': 100,
            'current-item-index': 1,
            schema: 301,
        });

        const pullResult = await engine.pull(initialData, 0);
        expect(pullResult.data.tasks).toHaveLength(1);
        expect(pullResult.data.tasks[0].title).toBe('Task from iPad');
        expect(pullResult.data.tasks[0].description).toBe('Synced via Things Cloud');
        expect(pullResult.data.tasks[0].status).toBe('inbox');
        expect(pullResult.newServerIndex).toBe(1);

        // 2. Locally: complete the iPad task and create a new one.
        const androidTaskId = generateThingsId();
        const now = new Date().toISOString();
        const updatedData: AppData = {
            ...pullResult.data,
            tasks: [
                { ...pullResult.data.tasks[0], status: 'done', completedAt: now },
                {
                    id: androidTaskId,
                    title: 'Task created on Android',
                    status: 'inbox',
                    tags: ['#android'],
                    createdAt: now,
                    updatedAt: now,
                } as unknown as Task,
            ],
        };

        const pushResult = await engine.push(updatedData, pullResult.data, pullResult.newServerIndex);

        expect(client.committedChanges).toHaveLength(1);
        const { changes, ancestorIndex } = client.committedChanges[0];
        expect(ancestorIndex).toBe(1);

        // The completed iPad task is sent as an update, the Android task as a creation.
        expect(changes[ipadTaskId].t).toBe(ThingsItemAction.Modified);
        expect(changes[ipadTaskId].p.ss).toBe(ThingsTaskStatus.Completed);
        expect(changes[androidTaskId].t).toBe(ThingsItemAction.Created);
        expect(changes[androidTaskId].p.tt).toBe('Task created on Android');

        // Its tag does not exist on the server yet, so it is created in the same commit
        // and the task refers to it by uuid, not by name.
        const tagEntries = Object.entries(changes).filter(([, wire]) => wire.e === 'Tag4');
        expect(tagEntries).toHaveLength(1);
        expect(tagEntries[0][1].p.tt).toBe('android');
        expect(changes[androidTaskId].p.tg).toEqual([tagEntries[0][0]]);

        // Two tasks and one tag.
        expect(pushResult.pushedCount).toBe(3);
        expect(pushResult.newServerIndex).toBe(4);
    });

    it('a full sync() does the same in one call', async () => {
        const client = new MockThingsCloudClient();
        const engine = new ThingsCloudSyncEngine(client);
        const id = generateThingsId();
        const now = new Date().toISOString();
        const local = {
            tasks: [{ id, title: 'Local only', status: 'next', tags: [], contexts: [], createdAt: now, updatedAt: now }],
            projects: [],
            sections: [],
            areas: [],
            settings: {},
        } as unknown as AppData;

        const result = await engine.sync(local, null, 10);

        expect(result.pulledChangesCount).toBe(0);
        expect(result.pushedChangesCount).toBe(1);
        expect(result.conflictRetries).toBe(0);
        expect(client.committedChanges[0].changes[id].p.tt).toBe('Local only');
    });
});
