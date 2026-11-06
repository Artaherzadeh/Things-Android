import {
    LEGACY_THINGS_MAC_CLIENT_INFO,
    MINDWTR_THINGS_CLIENT_INFO,
    type ThingsAuthResponse,
    type ThingsClientInfo,
    type ThingsCommitResponse,
    type ThingsHistoryResponse,
    type ThingsItemsPageResponse,
    type ThingsWireObject,
} from './things-cloud-types.ts';
import { bytesToBase64 } from '../base64-bytes.ts';

const BASE_URL = 'https://cloud.culturedcode.com/version/1';
const APP_ID = 'com.culturedcode.ThingsMac';
const SCHEMA_VERSION = '301';
const WRITE_PUSH_PRIORITY = '10';

/** What the client tells the server about itself (User-Agent and things-client-info). */
export interface ThingsClientIdentity {
    userAgent: string;
    clientInfo: ThingsClientInfo;
}

/** Default: identifies this app as Mindwtr. */
export const MINDWTR_IDENTITY: ThingsClientIdentity = {
    userAgent: 'Mindwtr-ThingsSync/1',
    clientInfo: MINDWTR_THINGS_CLIENT_INFO,
};

/**
 * Pretends to be Things for Mac, as earlier versions of this client did. Pass it
 * as `identity` only if the server turns out to reject the Mindwtr identity.
 */
export const LEGACY_THINGS_MAC_IDENTITY: ThingsClientIdentity = {
    userAgent: 'ThingsMac/32209501',
    clientInfo: LEGACY_THINGS_MAC_CLIENT_INFO,
};

export interface ThingsCloudClientOptions {
    /**
     * Stable id of this install, sent as `App-Instance-Id`. Generate once with
     * generateAppInstanceId() and persist it; otherwise every client instance gets
     * a new random id.
     */
    appInstanceId?: string;
    identity?: ThingsClientIdentity;
}

function randomHex(length: number): string {
    const bytes = new Uint8Array(Math.ceil(length / 2));
    if (typeof crypto === 'undefined' || typeof crypto.getRandomValues !== 'function') {
        throw new ThingsCloudError('Secure random generation is unavailable on this device.');
    }
    crypto.getRandomValues(bytes);
    return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('').slice(0, length);
}

/**
 * New random id for one install, in the shape earlier versions sent (63 hex,
 * app id, 63 hex) but unique instead of all zeros. The server's use of the
 * value is not documented.
 */
export function generateAppInstanceId(): string {
    return `${randomHex(63)}-${APP_ID}-${randomHex(63)}`;
}

function encodeBase64(input: string): string {
    return bytesToBase64(new TextEncoder().encode(input));
}

export class ThingsCloudError extends Error {
    public statusCode?: number;
    public responseBody?: string;

    constructor(
        message: string,
        statusCode?: number,
        responseBody?: string,
        public outcomeUnknown = false,
    ) {
        super(message);
        this.name = 'ThingsCloudError';
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }
}

/**
 * HTTP statuses treated as "the ancestor-index you committed against is stale",
 * i.e. another device committed after our last pull.
 *
 * 409 is what the reverse-engineered implementations report for this case.
 * Only 409 is reported by the inspected reverse-engineered clients. It has not
 * been verified against the live server; other commit errors retain the
 * durable outbox and are reconciled instead of being retried as a known reject.
 */
const COMMIT_CONFLICT_STATUS_CODES: ReadonlySet<number> = new Set([409]);

export function isThingsCloudConflictError(error: unknown): error is ThingsCloudError {
    return (
        error instanceof ThingsCloudError
        && error.statusCode !== undefined
        && COMMIT_CONFLICT_STATUS_CODES.has(error.statusCode)
    );
}

export class ThingsCloudClient {
    public email: string;
    private password: string;
    public historyKey?: string;
    public headIndex: number = 0;
    private clientInfoBase64: string;
    private userAgent: string;
    private appInstanceId: string;

    constructor(
        email: string,
        password: string,
        historyKey?: string,
        headIndex: number = 0,
        options: ThingsCloudClientOptions = {}
    ) {
        const identity = options.identity ?? MINDWTR_IDENTITY;
        this.email = email;
        this.password = password;
        this.historyKey = historyKey;
        this.headIndex = headIndex;
        this.clientInfoBase64 = encodeBase64(JSON.stringify(identity.clientInfo));
        this.userAgent = identity.userAgent;
        this.appInstanceId = options.appInstanceId || generateAppInstanceId();
    }

    private getCommonHeaders(): Record<string, string> {
        return {
            'Accept': 'application/json',
            'Accept-Charset': 'UTF-8',
            'User-Agent': this.userAgent,
            'things-client-info': this.clientInfoBase64,
            'App-Id': APP_ID,
            'Schema': SCHEMA_VERSION,
            'App-Instance-Id': this.appInstanceId,
        };
    }

    /**
     * Authenticates with Things Cloud using email and password, returning the own-history-key.
     */
    async authenticate(): Promise<string> {
        const encodedEmail = encodeURIComponent(this.email);
        const url = `${BASE_URL}/account/${encodedEmail}`;

        const headers = {
            ...this.getCommonHeaders(),
            'Authorization': `Password ${encodeURIComponent(this.password)}`,
        };

        const response = await fetch(url, {
            method: 'GET',
            headers,
        });

        if (!response.ok) {
            const body = await response.text().catch(() => '');
            if (response.status === 401 || response.status === 403) {
                throw new ThingsCloudError('Invalid Things Cloud credentials (Unauthorized)', response.status, body);
            }
            throw new ThingsCloudError(`Authentication failed with status ${response.status}`, response.status, body);
        }

        const data = (await response.json()) as ThingsAuthResponse;
        const key = data['history-key'];
        if (!key) {
            throw new ThingsCloudError('Missing history-key in Things Cloud authentication response');
        }

        this.historyKey = key;
        return key;
    }

    /**
     * Fetches current history metadata (latest-server-index, etc.).
     */
    async getHistoryInfo(): Promise<ThingsHistoryResponse> {
        if (!this.historyKey) {
            await this.authenticate();
        }

        const url = `${BASE_URL}/history/${this.historyKey}`;
        const response = await fetch(url, {
            method: 'GET',
            headers: this.getCommonHeaders(),
        });

        if (!response.ok) {
            const body = await response.text().catch(() => '');
            throw new ThingsCloudError(`Failed to get history info: HTTP ${response.status}`, response.status, body);
        }

        const data = (await response.json()) as ThingsHistoryResponse;
        if (!Number.isSafeInteger(data['latest-server-index']) || data['latest-server-index'] < 0
            || !Number.isSafeInteger(data['latest-total-content-size']) || data['latest-total-content-size'] < 0
            || !Number.isSafeInteger(data['latest-schema-version']) || data['latest-schema-version'] < 1
            || typeof data['is-empty'] !== 'boolean') {
            throw new ThingsCloudError('Things Cloud returned invalid history metadata.');
        }
        return data;
    }

    /**
     * Reads a single page of items starting from the specified server index.
     */
    async getItemsPage(startIndex: number): Promise<ThingsItemsPageResponse> {
        if (!this.historyKey) {
            await this.authenticate();
        }

        const url = `${BASE_URL}/history/${this.historyKey}/items?start-index=${startIndex}`;
        const response = await fetch(url, {
            method: 'GET',
            headers: this.getCommonHeaders(),
        });

        if (!response.ok) {
            const body = await response.text().catch(() => '');
            throw new ThingsCloudError(`Failed to fetch items at index ${startIndex}: HTTP ${response.status}`, response.status, body);
        }

        const data = (await response.json()) as ThingsItemsPageResponse;
        if (!Array.isArray(data.items)
            || !Number.isSafeInteger(data['current-item-index']) || data['current-item-index'] < startIndex
            || !Number.isSafeInteger(data.schema) || data.schema < 1
            || !Number.isSafeInteger(data['latest-total-content-size']) || data['latest-total-content-size'] < 0) {
            throw new ThingsCloudError('Things Cloud returned an invalid history page.');
        }
        if (data['current-item-index'] > this.headIndex) {
            this.headIndex = data['current-item-index'];
        }
        return data;
    }

    /**
     * Pushes / commits changes (new, modified, or deleted items) to Things Cloud.
     */
    async commit(
        changes: Record<string, ThingsWireObject>,
        ancestorIndex?: number
    ): Promise<number> {
        if (!this.historyKey) {
            await this.authenticate();
        }

        const index = ancestorIndex !== undefined ? ancestorIndex : this.headIndex;
        const url = `${BASE_URL}/history/${this.historyKey}/commit?ancestor-index=${index}&_cnt=1`;

        const headers = {
            ...this.getCommonHeaders(),
            'Content-Type': 'application/json; charset=UTF-8',
            'Content-Encoding': 'UTF-8',
            'Push-Priority': WRITE_PUSH_PRIORITY,
        };

        let response: Response;
        try {
            response = await fetch(url, {
                method: 'POST',
                headers,
                body: JSON.stringify(changes),
            });
        } catch {
            throw new ThingsCloudError('Things Cloud commit outcome is unknown; reconcile history before retrying.', undefined, undefined, true);
        }

        if (!response.ok) {
            const body = await response.text().catch(() => '');
            throw new ThingsCloudError(
                `Commit failed with status ${response.status}`,
                response.status,
                body,
                !COMMIT_CONFLICT_STATUS_CODES.has(response.status),
            );
        }

        const data = (await response.json()) as ThingsCommitResponse;
        const newIndex = data['server-head-index'];
        if (!Number.isSafeInteger(newIndex) || newIndex < index) {
            throw new ThingsCloudError('Things Cloud commit outcome is unknown; the server returned no valid checkpoint.', response.status, undefined, true);
        }
        this.headIndex = newIndex;
        return newIndex;
    }
}
