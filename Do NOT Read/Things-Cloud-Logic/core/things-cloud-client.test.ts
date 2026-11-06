import { describe, expect, it, vi } from 'vitest';
import {
    LEGACY_THINGS_MAC_IDENTITY,
    ThingsCloudClient,
    ThingsCloudError,
    generateAppInstanceId,
    isThingsCloudConflictError,
} from './things-cloud-client';
import { MINDWTR_THINGS_CLIENT_INFO, ThingsItemAction, type ThingsWireObject } from './things-cloud-types';
import { base64ToBytes } from '../base64-bytes';

type Call = { url: string; init: RequestInit };

function fakeResponse(status: number, body: unknown = {}): Response {
    return {
        ok: status >= 200 && status < 300,
        status,
        json: async () => body,
        text: async () => (typeof body === 'string' ? body : JSON.stringify(body)),
    } as unknown as Response;
}

/** Runs `fn` with fetch replaced by a queue of canned responses; records every call. */
async function withFetch<T>(queue: Response[], fn: (calls: Call[]) => Promise<T>): Promise<T> {
    const original = globalThis.fetch;
    const calls: Call[] = [];
    globalThis.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
        calls.push({ url: String(url), init: init ?? {} });
        const next = queue.shift();
        if (!next) throw new Error('unexpected fetch call');
        return next;
    }) as typeof fetch;
    try {
        return await fn(calls);
    } finally {
        globalThis.fetch = original;
    }
}

const headersOf = (call: Call) => call.init.headers as Record<string, string>;
const historyInfo = { 'latest-schema-version': 301, 'latest-total-content-size': 0, 'is-empty': true, 'latest-server-index': 0 };
const emptyHistoryPage = { items: [], 'latest-total-content-size': 0, 'start-total-content-size': 0, 'end-total-content-size': 0, 'current-item-index': 0, schema: 301 };
const decodeClientInfo = (call: Call) =>
    JSON.parse(new TextDecoder().decode(base64ToBytes(headersOf(call)['things-client-info'])));

describe('ThingsCloudClient identity', () => {
    it('identifies as Mindwtr by default', async () => {
        const client = new ThingsCloudClient('a@b.c', 'pw', 'key');
        await withFetch([fakeResponse(200, historyInfo)], async (calls) => {
            await client.getHistoryInfo();
            expect(headersOf(calls[0])['User-Agent'].startsWith('Mindwtr')).toBe(true);
            expect(decodeClientInfo(calls[0]).nn).toBe('Mindwtr');
        });
    });

    it('can still use the legacy Things for Mac identity', async () => {
        const client = new ThingsCloudClient('a@b.c', 'pw', 'key', 0, { identity: LEGACY_THINGS_MAC_IDENTITY });
        await withFetch([fakeResponse(200, historyInfo)], async (calls) => {
            await client.getHistoryInfo();
            expect(headersOf(calls[0])['User-Agent']).toBe('ThingsMac/32209501');
            expect(decodeClientInfo(calls[0]).nn).toBe('ThingsMac');
        });
    });

    it('encodes client identity as UTF-8 base64 without Node globals', async () => {
        const client = new ThingsCloudClient('a@b.c', 'pw', 'key', 0, {
            identity: {
                userAgent: 'Mindwtr test',
                clientInfo: { ...MINDWTR_THINGS_CLIENT_INFO, nn: 'Mindwtr \u{1F600}' },
            },
        });
        vi.stubGlobal('Buffer', undefined);
        vi.stubGlobal('btoa', undefined);
        try {
            await withFetch([fakeResponse(200, historyInfo)], async (calls) => {
                await client.getHistoryInfo();
                expect(decodeClientInfo(calls[0]).nn).toBe('Mindwtr \u{1F600}');
            });
        } finally {
            vi.unstubAllGlobals();
        }
    });

    it('generates a unique App-Instance-Id per client, never the old all-zero placeholder', async () => {
        const seen: string[] = [];
        for (let i = 0; i < 2; i++) {
            const client = new ThingsCloudClient('a@b.c', 'pw', 'key');
            await withFetch([fakeResponse(200, historyInfo)], async (calls) => {
                await client.getHistoryInfo();
                seen.push(headersOf(calls[0])['App-Instance-Id']);
            });
        }
        expect(seen[0]).not.toBe(seen[1]);
        for (const id of seen) {
            expect(/^[0-9a-f]{63}-com\.culturedcode\.ThingsMac-[0-9a-f]{63}$/.test(id)).toBe(true);
            expect(id.startsWith('0'.repeat(63))).toBe(false);
        }
    });

    it('uses the App-Instance-Id it is given, on every request', async () => {
        const id = generateAppInstanceId();
        const client = new ThingsCloudClient('a@b.c', 'pw', 'key', 0, { appInstanceId: id });
        await withFetch([fakeResponse(200, historyInfo), fakeResponse(200, emptyHistoryPage)], async (calls) => {
            await client.getHistoryInfo();
            await client.getItemsPage(0);
            expect(headersOf(calls[0])['App-Instance-Id']).toBe(id);
            expect(headersOf(calls[1])['App-Instance-Id']).toBe(id);
        });
    });

    it('generateAppInstanceId returns different values', () => {
        expect(generateAppInstanceId()).not.toBe(generateAppInstanceId());
    });
});

describe('ThingsCloudClient requests', () => {
    it('authenticates with an encoded email and password and stores the history key', async () => {
        const client = new ThingsCloudClient('me+tag@example.com', 'p@ss word');
        await withFetch([fakeResponse(200, { 'history-key': 'HK' })], async (calls) => {
            const key = await client.authenticate();
            expect(key).toBe('HK');
            expect(client.historyKey).toBe('HK');
            expect(calls[0].url).toBe('https://cloud.culturedcode.com/version/1/account/me%2Btag%40example.com');
            expect(headersOf(calls[0]).Authorization).toBe('Password p%40ss%20word');
        });
    });

    it('reports bad credentials as a 401 ThingsCloudError', async () => {
        const client = new ThingsCloudClient('a@b.c', 'wrong');
        await withFetch([fakeResponse(401, 'nope')], async () => {
            await expect(client.authenticate()).rejects.toMatchObject({ statusCode: 401, name: 'ThingsCloudError' });
        });
    });

    it('fails when the server returns no history key', async () => {
        const client = new ThingsCloudClient('a@b.c', 'pw');
        await withFetch([fakeResponse(200, {})], async () => {
            await expect(client.authenticate()).rejects.toBeInstanceOf(ThingsCloudError);
        });
    });

    it('authenticates first when it has no history key yet', async () => {
        const client = new ThingsCloudClient('a@b.c', 'pw');
        await withFetch(
            [fakeResponse(200, { 'history-key': 'HK' }), fakeResponse(200, { ...emptyHistoryPage, 'current-item-index': 3 })],
            async (calls) => {
                await client.getItemsPage(3);
                expect(calls).toHaveLength(2);
                expect(calls[1].url).toBe('https://cloud.culturedcode.com/version/1/history/HK/items?start-index=3');
            }
        );
    });

    it('remembers the head index reported with a page of items', async () => {
        const client = new ThingsCloudClient('a@b.c', 'pw', 'key');
        await withFetch([fakeResponse(200, { ...emptyHistoryPage, 'current-item-index': 42 })], async () => {
            await client.getItemsPage(40);
            expect(client.headIndex).toBe(42);
        });
    });

    it('turns a failed items request into a ThingsCloudError with the status', async () => {
        const client = new ThingsCloudClient('a@b.c', 'pw', 'key');
        await withFetch([fakeResponse(500, 'boom')], async () => {
            await expect(client.getItemsPage(0)).rejects.toMatchObject({ statusCode: 500, responseBody: 'boom' });
        });
    });

    it('commits changes against the given ancestor index and returns the new head', async () => {
        const client = new ThingsCloudClient('a@b.c', 'pw', 'key');
        const changes: Record<string, ThingsWireObject> = {
            t1: { t: ThingsItemAction.Created, e: 'Task7', p: { tt: 'Hello' } },
        };
        await withFetch([fakeResponse(200, { 'server-head-index': 9 })], async (calls) => {
            const head = await client.commit(changes, 7);
            expect(head).toBe(9);
            expect(client.headIndex).toBe(9);
            expect(calls[0].url).toBe('https://cloud.culturedcode.com/version/1/history/key/commit?ancestor-index=7&_cnt=1');
            expect(calls[0].init.method).toBe('POST');
            expect(JSON.parse(calls[0].init.body as string)).toEqual(changes);
            expect(headersOf(calls[0])['Content-Type']).toBe('application/json; charset=UTF-8');
        });
    });

    it('marks a transport failure during POST as an unknown write outcome', async () => {
        const client = new ThingsCloudClient('a@b.c', 'pw', 'key');
        const original = globalThis.fetch;
        globalThis.fetch = (async () => { throw new Error('socket closed'); }) as typeof fetch;
        try {
            await expect(client.commit({ t1: { t: ThingsItemAction.Created, e: 'Task7', p: { tt: 'A' } } }))
                .rejects.toMatchObject({ outcomeUnknown: true });
        } finally {
            globalThis.fetch = original;
        }
    });

    it('marks a successful response without a valid head index as unknown', async () => {
        const client = new ThingsCloudClient('a@b.c', 'pw', 'key');
        await withFetch([fakeResponse(200, {})], async () => {
            await expect(client.commit({ t1: { t: ThingsItemAction.Created, e: 'Task7', p: { tt: 'A' } } }))
                .rejects.toMatchObject({ outcomeUnknown: true });
        });
    });

    it('falls back to its own head index when no ancestor index is given', async () => {
        const client = new ThingsCloudClient('a@b.c', 'pw', 'key', 12);
        await withFetch([fakeResponse(200, { 'server-head-index': 13 })], async (calls) => {
            await client.commit({});
            expect(calls[0].url.includes('ancestor-index=12')).toBe(true);
        });
    });

    it('raises a conflict error when the server rejects a stale commit', async () => {
        const client = new ThingsCloudClient('a@b.c', 'pw', 'key');
        await withFetch([fakeResponse(409, 'stale')], async () => {
            let caught: unknown;
            try {
                await client.commit({}, 1);
            } catch (error) {
                caught = error;
            }
            expect(isThingsCloudConflictError(caught)).toBe(true);
        });
    });

    it('does not assume HTTP 412 is a stale-ancestor rejection', async () => {
        const client = new ThingsCloudClient('a@b.c', 'pw', 'key');
        await withFetch([fakeResponse(412, 'precondition failed')], async () => {
            await expect(client.commit({}, 1)).rejects.toMatchObject({
                statusCode: 412,
                outcomeUnknown: true,
            });
        });
    });
});
