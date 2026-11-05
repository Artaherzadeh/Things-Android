import {
    base58Decode,
    base58Encode,
    generateThingsId,
    isValidThingsId,
} from './things-id.ts';

import {
    ThingsItemAction,
    ThingsTaskSchedule,
    ThingsTaskStatus,
    ThingsTaskType,
} from './things-cloud-types.ts';

import {
    mapThingsAreaToEntity,
    mapThingsChecklistToEntity,
    mapThingsTaskToEntity,
    mindwtrProjectToThingsWire,
    mindwtrTaskToThingsWire,
} from './things-cloud-mapper.ts';

console.log('--- 1. Testing Things ID & Base58 ---');
const id1 = generateThingsId();
const id2 = generateThingsId();
console.log('ID 1:', id1, 'Valid:', isValidThingsId(id1));
console.log('ID 2:', id2, 'Valid:', isValidThingsId(id2));
if (!isValidThingsId(id1) || id1 === id2) throw new Error('ID validation failed');

const sampleBytes = new Uint8Array([1, 2, 3, 50, 100, 200, 255]);
const encoded = base58Encode(sampleBytes);
const decoded = base58Decode(encoded);
console.log('Base58 roundtrip:', encoded, '-> equal:', sampleBytes.every((v, i) => v === decoded[i]));

console.log('\n--- 2. Testing Things Todo -> Mindwtr Task Mapping ---');
const todoMapped = mapThingsTaskToEntity('item-123', {
    tt: 'Read Things 3 documentation',
    nt: { text: 'Focus on cloud sync protocol' },
    tp: ThingsTaskType.Todo,
    ss: ThingsTaskStatus.Incomplete,
    st: ThingsTaskSchedule.Inbox,
    tg: ['research'],
}, ThingsItemAction.Created);

console.log('Task Title:', todoMapped.task?.title);
console.log('Task Status:', todoMapped.task?.status);
console.log('Task Tags:', todoMapped.task?.tags);
if (todoMapped.task?.title !== 'Read Things 3 documentation') throw new Error('Todo title mismatch');
if (todoMapped.task?.status !== 'inbox') throw new Error('Todo status mismatch');

console.log('\n--- 3. Testing Things Project -> Mindwtr Project Mapping ---');
const projMapped = mapThingsTaskToEntity('proj-456', {
    tt: 'Things 3 Android Port',
    nt: 'Full bi-directional sync',
    tp: ThingsTaskType.Project,
    ss: ThingsTaskStatus.Incomplete,
    st: ThingsTaskSchedule.Anytime,
    tg: ['android'],
}, ThingsItemAction.Created);

console.log('Project Title:', projMapped.project?.title);
console.log('Project Status:', projMapped.project?.status);
if (projMapped.project?.title !== 'Things 3 Android Port') throw new Error('Project title mismatch');

console.log('\n--- 4. Testing Mindwtr Task -> Things Wire Conversion ---');
const localTask = {
    id: id1,
    title: 'Sync completed task to iPad',
    description: 'Verify instant sync on iPad',
    status: 'done',
    isFocusedToday: true,
    completedAt: new Date().toISOString(),
    tags: ['testing'],
    contexts: [],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
};

const wireTask = mindwtrTaskToThingsWire(localTask, true);
console.log('Wire Entity:', wireTask.e);
console.log('Wire Action (1=Modified):', wireTask.t);
console.log('Wire Status (3=Completed):', wireTask.p.ss);
console.log('Wire Title:', wireTask.p.tt);
if (wireTask.e !== 'Task7') throw new Error('Wire entity should be Task7');
if (wireTask.p.ss !== ThingsTaskStatus.Completed) throw new Error('Wire status should be Completed');

console.log('\n>>> ALL THINGS CLOUD LOGIC TESTS PASSED SUCCESSFULLY! <<<');
