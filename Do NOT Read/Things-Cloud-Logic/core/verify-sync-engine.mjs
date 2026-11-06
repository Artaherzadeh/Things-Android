import { ThingsCloudSyncEngine } from './things-cloud-sync.ts';
import { ThingsCloudClient } from './things-cloud-client.ts';
import { ThingsItemAction, ThingsTaskSchedule, ThingsTaskStatus, ThingsTaskType } from './things-cloud-types.ts';
import { generateThingsId } from './things-id.ts';

// Mock client subclass to test engine end-to-end without network
class MockThingsCloudClient extends ThingsCloudClient {
    mockPages = [];
    committedChanges = [];

    constructor() {
        super('test@example.com', 'password123', 'mock-history-key', 10);
    }

    async getItemsPage(startIndex) {
        return this.mockPages.shift() || {
            items: [],
            'latest-total-content-size': 100,
            'start-total-content-size': 100,
            'end-total-content-size': 100,
            'current-item-index': startIndex,
            schema: 301,
        };
    }

    async commit(changes, ancestorIndex) {
        this.committedChanges.push({ changes, ancestorIndex });
        return (ancestorIndex || 0) + Object.keys(changes).length;
    }
}

async function runTest() {
    console.log('--- Testing ThingsCloudSyncEngine ---');
    const mockClient = new MockThingsCloudClient();
    const engine = new ThingsCloudSyncEngine(mockClient);

    const initialData = {
        tasks: [],
        projects: [],
        sections: [],
        areas: [],
        settings: {},
    };

    // 1. Prepare simulated remote items from Things Cloud (iPad added these)
    const ipadTaskId = generateThingsId();
    mockClient.mockPages.push({
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
    console.log('Pulled tasks count:', pullResult.data.tasks.length);
    console.log('Pulled task title:', pullResult.data.tasks[0]?.title);
    if (pullResult.data.tasks[0]?.title !== 'Task from iPad') {
        throw new Error('Pull failed to receive iPad task');
    }

    // 2. Local Android adds a new task and completes the iPad task
    const androidTaskId = generateThingsId();
    const updatedData = {
        ...pullResult.data,
        tasks: [
            {
                ...pullResult.data.tasks[0],
                status: 'done',
                completedAt: new Date().toISOString(),
            },
            {
                id: androidTaskId,
                title: 'Task created on Android',
                status: 'inbox',
                tags: ['android'],
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
            },
        ],
    };

    const pushResult = await engine.push(updatedData, pullResult.data, pullResult.newServerIndex);
    console.log('Pushed count:', pushResult.pushedCount);
    console.log('Committed batches:', mockClient.committedChanges.length);
    const lastCommit = mockClient.committedChanges[0].changes;
    console.log('Committed items:', Object.keys(lastCommit));
    console.log('Completed iPad task on server status:', lastCommit[ipadTaskId].p.ss, '(3 = Completed)');
    console.log('Android task on server title:', lastCommit[androidTaskId].p.tt);

    if (lastCommit[ipadTaskId].p.ss !== ThingsTaskStatus.Completed) {
        throw new Error('iPad task completion not pushed properly');
    }
    if (lastCommit[androidTaskId].p.tt !== 'Task created on Android') {
        throw new Error('Android task not pushed properly');
    }

    console.log('\n>>> SYNC ENGINE END-TO-END TEST PASSED! <<<');
}

runTest().catch((err) => {
    console.error('Test failed:', err);
    process.exit(1);
});
