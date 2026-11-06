import { describe, expect, it } from 'vitest';
import {
    base58Decode,
    base58Encode,
    generateThingsId,
    isValidThingsId,
} from './things-id';
import {
    mapThingsAreaToEntity,
    mapThingsChecklistToEntity,
    mapThingsTaskToEntity,
    mindwtrProjectToThingsWire,
    mindwtrTaskToThingsWire,
} from './things-cloud-mapper';
import {
    ThingsItemAction,
    ThingsTaskSchedule,
    ThingsTaskStatus,
    ThingsTaskType,
} from './things-cloud-types';
import type { Task, Project } from '../types';

describe('Things Cloud Base58 and ID Generator', () => {
    it('generates valid Things IDs', () => {
        const id1 = generateThingsId();
        const id2 = generateThingsId();

        expect(id1).toBeTruthy();
        expect(id2).toBeTruthy();
        expect(id1).not.toBe(id2);
        expect(isValidThingsId(id1)).toBe(true);
        expect(isValidThingsId(id2)).toBe(true);
    });

    it('roundtrips base58 encode and decode', () => {
        const testBytes = new Uint8Array([0, 1, 2, 100, 255, 42, 13, 99]);
        const encoded = base58Encode(testBytes);
        const decoded = base58Decode(encoded);

        expect(decoded).toEqual(testBytes);
    });
});

describe('Things Cloud Mapper', () => {
    it('correctly maps a Things Todo wire item to a Mindwtr Task', () => {
        const result = mapThingsTaskToEntity('task-uuid-1', {
            tt: 'Buy Groceries',
            nt: { text: 'Milk and Eggs' },
            tp: ThingsTaskType.Todo,
            ss: ThingsTaskStatus.Incomplete,
            st: ThingsTaskSchedule.Inbox,
            ix: 1,
            tg: ['shopping'],
        }, ThingsItemAction.Created);

        expect(result.task).toBeDefined();
        expect(result.task?.id).toBe('task-uuid-1');
        expect(result.task?.title).toBe('Buy Groceries');
        expect(result.task?.description).toBe('Milk and Eggs');
        expect(result.task?.status).toBe('inbox');
        expect(result.task?.tags).toEqual(['shopping']);
    });

    it('correctly maps a Things Project wire item to a Mindwtr Project', () => {
        const result = mapThingsTaskToEntity('proj-uuid-1', {
            tt: 'Build Android Things App',
            nt: 'Important project',
            tp: ThingsTaskType.Project,
            ss: ThingsTaskStatus.Incomplete,
            st: ThingsTaskSchedule.Anytime,
            tg: ['dev'],
            ix: 0,
        }, ThingsItemAction.Created);

        expect(result.project).toBeDefined();
        expect(result.project?.id).toBe('proj-uuid-1');
        expect(result.project?.title).toBe('Build Android Things App');
        expect(result.project?.status).toBe('active');
        expect(result.project?.supportNotes).toBe('Important project');
    });

    it('correctly maps a Things Heading wire item to a Mindwtr Section', () => {
        const result = mapThingsTaskToEntity('heading-uuid-1', {
            tt: 'Phase 1: Setup',
            tp: ThingsTaskType.Heading,
            pr: ['proj-uuid-1'],
            ix: 2,
        }, ThingsItemAction.Created);

        expect(result.section).toBeDefined();
        expect(result.section?.id).toBe('heading-uuid-1');
        expect(result.section?.title).toBe('Phase 1: Setup');
        expect(result.section?.projectId).toBe('proj-uuid-1');
    });

    it('converts a Mindwtr Task to a Things Wire item', () => {
        const task: Task = {
            id: 'mindwtr-task-1',
            title: 'Call Doctor',
            description: 'Appointment at 4 PM',
            status: 'done',
            isFocusedToday: true,
            completedAt: '2026-10-30T10:00:00.000Z',
            tags: ['health'],
            contexts: [],
            createdAt: '2026-10-30T09:00:00.000Z',
            updatedAt: '2026-10-30T10:00:00.000Z',
        };

        const wire = mindwtrTaskToThingsWire(task, true);
        expect(wire.e).toBe('Task7');
        expect(wire.t).toBe(ThingsItemAction.Modified);
        expect(wire.p.tt).toBe('Call Doctor');
        expect(wire.p.ss).toBe(ThingsTaskStatus.Completed);
        expect(wire.p.sp).toBeDefined();
        expect(wire.p.tg).toEqual(['health']);
    });

    it('converts a Mindwtr Project to a Things Project Wire item', () => {
        const project: Project = {
            id: 'mindwtr-proj-1',
            title: 'House Renovation',
            status: 'active',
            color: '#2080f0',
            order: 5,
            tagIds: ['home'],
            createdAt: '2026-10-30T09:00:00.000Z',
            updatedAt: '2026-10-30T10:00:00.000Z',
        };

        const wire = mindwtrProjectToThingsWire(project, false);
        expect(wire.e).toBe('Task7');
        expect(wire.t).toBe(ThingsItemAction.Created);
        expect(wire.p.tp).toBe(ThingsTaskType.Project);
        expect(wire.p.tt).toBe('House Renovation');
        expect(wire.p.ss).toBe(ThingsTaskStatus.Incomplete);
    });
});
