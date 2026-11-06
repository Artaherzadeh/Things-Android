import AsyncStorage from '@react-native-async-storage/async-storage';
import {
    applySyncedDataToStore,
    flushPendingSave,
    getInMemorySyncChangeFingerprint,
    getInMemoryAppDataSnapshot,
    getStorageAdapter,
    useTaskStore,
    type AppData,
} from '@mindwtr/core';
import type { ThingsWireObject } from '@mindwtr/core/things-cloud';
import {
    ThingsCloudClient,
    ThingsCloudError,
    ThingsCloudSyncEngine,
    ThingsCloudConcurrentEditError,
    isThingsCloudConflictError,
    historyBatchConfirmsChanges,
    generateAppInstanceId,
    type ThingsTagState,
    type ThingsCloudConcurrentEntityConflict,
} from '@mindwtr/core/things-cloud';

import {
    deleteSecureConfigValue,
    getSecureConfigValue,
    setSecureConfigValue,
} from './secure-config';
import { logInfo, logSyncError } from './app-log';
import {
    THINGS_CLOUD_EMAIL_KEY as KEY_EMAIL,
    THINGS_CLOUD_PASSWORD_KEY as KEY_PASSWORD,
    THINGS_CLOUD_HISTORY_KEY as KEY_HISTORY_KEY,
} from '@mindwtr/core';

const KEY_SERVER_INDEX = 'things_cloud_server_index';
const KEY_LAST_SYNC = 'things_cloud_last_sync';
const KEY_LAST_ERROR = 'things_cloud_last_error';
const KEY_LAST_DATA = 'things_cloud_last_data_snapshot';
const KEY_SYNC_STATE = 'things_cloud_sync_state_v2';
// Things tag uuid -> title memory. Must live as long as KEY_SERVER_INDEX: an
// incremental pull never re-delivers tag entities, so losing it breaks tag names.
const KEY_TAG_STATE = 'things_cloud_tag_state';
// Random id of this install, sent as App-Instance-Id. Deliberately kept across
// logout/login: it identifies the app install, not the account.
const KEY_INSTANCE_ID = 'things_cloud_instance_id';

async function getOrCreateInstanceId(): Promise<string> {
    const existing = await AsyncStorage.getItem(KEY_INSTANCE_ID);
    if (existing) return existing;
    const created = generateAppInstanceId();
    await AsyncStorage.setItem(KEY_INSTANCE_ID, created);
    return created;
}

export interface ThingsCloudStatus {
    isConnected: boolean;
    email: string | null;
    lastSyncTime: string | null;
    lastError: string | null;
    serverIndex: number;
    conflicts: ThingsCloudConcurrentEntityConflict[];
}

interface ThingsCloudOutbox {
    createdAt: string;
    ancestorIndex: number;
    changes: Record<string, ThingsWireObject>;
}

interface ThingsCloudAccountState {
    version: 2;
    account: string;
    serverIndex: number;
    lastSyncTime: string | null;
    lastError: string | null;
    baselineData: AppData | null;
    tagState: ThingsTagState | null;
    outbox: ThingsCloudOutbox | null;
    wireRecords: Record<string, ThingsWireObject>;
    conflicts: ThingsCloudConcurrentEntityConflict[];
}

const newAccountState = (account: string, baselineData: AppData | null): ThingsCloudAccountState => ({
    version: 2,
    account,
    serverIndex: 0,
    lastSyncTime: null,
    lastError: null,
    baselineData,
    tagState: null,
    outbox: null,
    wireRecords: {},
    conflicts: [],
});

const normalizedAccount = (email: string): string => email.trim().toLocaleLowerCase('en-US');
const stateStorageKey = (email: string): string => `${KEY_SYNC_STATE}:${encodeURIComponent(normalizedAccount(email))}`;

async function readAccountState(email: string): Promise<ThingsCloudAccountState> {
    const account = normalizedAccount(email);
    const storage = getStorageAdapter();
    const stateJson = storage.getThingsCloudSyncState
        ? await storage.getThingsCloudSyncState(stateStorageKey(account))
        : await AsyncStorage.getItem(stateStorageKey(account));
    if (stateJson) {
        try {
            const parsed = JSON.parse(stateJson) as ThingsCloudAccountState;
            if (parsed.version === 2 && parsed.account === account && Number.isSafeInteger(parsed.serverIndex)
                && parsed.serverIndex >= 0 && (parsed.baselineData === null || Array.isArray(parsed.baselineData?.tasks))) {
                return { ...parsed, conflicts: Array.isArray(parsed.conflicts) ? parsed.conflicts : [] };
            }
        } catch {
            // A malformed checkpoint must never be treated as a valid advanced cursor.
        }
    }

    // One-time compatibility read for installations on the original storage format.
    const legacyEmail = await AsyncStorage.getItem(KEY_EMAIL);
    if (legacyEmail && normalizedAccount(legacyEmail) === account) {
        const indexText = await AsyncStorage.getItem(KEY_SERVER_INDEX);
        const baselineText = await AsyncStorage.getItem(KEY_LAST_DATA);
        const tagText = await AsyncStorage.getItem(KEY_TAG_STATE);
        let baselineData: AppData | null = null;
        let tagState: ThingsTagState | null = null;
        try {
            if (baselineText) {
                const parsed = JSON.parse(baselineText) as AppData;
                if (Array.isArray(parsed?.tasks) && Array.isArray(parsed?.projects)) baselineData = parsed;
            }
        } catch { baselineData = null; }
        try { if (tagText) tagState = JSON.parse(tagText) as ThingsTagState; } catch { tagState = null; }
        const legacyIndex = Number(indexText ?? 0);
        const legacyState = {
            ...newAccountState(account, baselineData),
            serverIndex: Number.isSafeInteger(legacyIndex) && legacyIndex >= 0 ? legacyIndex : 0,
            lastSyncTime: await AsyncStorage.getItem(KEY_LAST_SYNC),
            lastError: (await AsyncStorage.getItem(KEY_LAST_ERROR)) ? 'A previous sync failed. Try again.' : null,
            tagState,
        };
        // A corrupt old baseline cannot safely imply "everything is new". Reset
        // the cursor and use the current local library as a no-upload baseline.
        if (baselineText && !baselineData) {
            const local = getInMemoryAppDataSnapshot();
            legacyState.serverIndex = 0;
            legacyState.baselineData = local;
            legacyState.tagState = null;
        }
        return legacyState;
    }
    return newAccountState(account, null);
}

async function writeAccountState(state: ThingsCloudAccountState): Promise<void> {
    const storage = getStorageAdapter();
    if (storage.setThingsCloudSyncState) {
        await storage.setThingsCloudSyncState(stateStorageKey(state.account), JSON.stringify(state));
        return;
    }
    await AsyncStorage.setItem(stateStorageKey(state.account), JSON.stringify(state));
}

async function saveDataAndAccountState(data: AppData, state: ThingsCloudAccountState): Promise<void> {
    const storage = getStorageAdapter();
    if (!storage.saveDataWithThingsCloudSyncState) {
        throw new Error('This storage backend cannot atomically save Things Cloud data and its checkpoint.');
    }
    await storage.saveDataWithThingsCloudSyncState(data, stateStorageKey(state.account), JSON.stringify(state));
}

let syncInFlight = false;

const safeErrorMessage = (error: unknown): string => {
    if (error instanceof ThingsCloudConcurrentEditError) return error.message;
    if (error && typeof error === 'object' && 'outcomeUnknown' in error
        && (error as { outcomeUnknown?: unknown }).outcomeUnknown === true) {
        return 'A Things Cloud write may have reached the server. Sync is paused until history can confirm the result.';
    }
    const statusCode = error && typeof error === 'object' && 'statusCode' in error
        ? Number((error as { statusCode?: unknown }).statusCode)
        : undefined;
    if (statusCode === 401 || statusCode === 403) return 'Things Cloud authentication failed. Reconnect your account.';
    if (statusCode === 409) return 'Things Cloud changed during sync. Try syncing again.';
    if (Number.isFinite(statusCode)) return `Things Cloud request failed (HTTP ${statusCode}).`;
    return 'Things Cloud sync failed. Check your connection and try again.';
};

export async function getThingsCloudStatus(): Promise<ThingsCloudStatus> {
    const email = await getSecureConfigValue(KEY_EMAIL);
    let state: ThingsCloudAccountState | null = null;
    if (email) {
        try { state = await readAccountState(email); } catch { state = null; }
    }
    const lastSyncTime = state?.lastSyncTime ?? await AsyncStorage.getItem(KEY_LAST_SYNC);
    const legacyError = await AsyncStorage.getItem(KEY_LAST_ERROR);
    const lastError = state?.lastError ?? (legacyError ? 'A previous sync failed. Try again.' : null);
    const serverIndex = state?.serverIndex ?? 0;

    return {
        isConnected: !!email,
        email,
        lastSyncTime,
        lastError,
        serverIndex: isNaN(serverIndex) ? 0 : serverIndex,
        conflicts: state?.conflicts ?? [],
    };
}

function replaceConflictEntity(
    data: AppData,
    conflict: ThingsCloudConcurrentEntityConflict,
    choice: 'local' | 'remote',
): AppData {
    let replacement = choice === 'local' ? conflict.local : conflict.remote;
    if (!replacement && choice === 'local' && conflict.remote) {
        // A locally deleted row cannot safely be recreated as a hard delete
        // after a remote edit. Keep a durable local tombstone as the chosen op.
        const at = new Date().toISOString();
        replacement = conflict.collection === 'tasks' || conflict.collection === 'projects'
            ? { ...conflict.remote, deletedAt: at, purgedAt: at } as typeof conflict.remote
            : { ...conflict.remote, deletedAt: at } as typeof conflict.remote;
    }
    const rows = data[conflict.collection] as Array<{ id: string }>;
    const index = rows.findIndex((item) => item.id === conflict.id);
    const next = replacement
        ? index < 0
            ? [...rows, replacement as { id: string }]
            : rows.map((item, rowIndex) => rowIndex === index ? replacement as { id: string } : item)
        : rows.filter((item) => item.id !== conflict.id);
    return { ...data, [conflict.collection]: next } as AppData;
}

export async function resolveThingsCloudConflict(id: string, choice: 'local' | 'remote'): Promise<void> {
    const email = await getSecureConfigValue(KEY_EMAIL);
    if (!email) throw new Error('Reconnect the Things Cloud account to resolve its saved conflicts.');
    const state = await readAccountState(email);
    const conflict = state.conflicts.find((item) => item.id === id);
    if (!conflict) throw new Error('This saved Things Cloud conflict no longer exists.');
    const data = replaceConflictEntity(getInMemoryAppDataSnapshot(), conflict, choice);
    const conflicts = state.conflicts.filter((item) => item.id !== id);
    const nextState = {
        ...state,
        conflicts,
        lastError: conflicts.length > 0 ? state.lastError : null,
    };
    await saveDataAndAccountState(data, nextState);
    applySyncedDataToStore(data);
}

/**
 * Logs in to Things Cloud and performs the initial pull.
 */
export async function loginThingsCloud(email: string, password: string): Promise<boolean> {
    const normalizedEmail = email.trim();
    const client = new ThingsCloudClient(email.trim(), password, undefined, 0, {
        appInstanceId: await getOrCreateInstanceId(),
    });
    try {
        const historyKey = await client.authenticate();

        const currentAccount = await getSecureConfigValue(KEY_EMAIL);
        const localData = getInMemoryAppDataSnapshot();
        const currentState = currentAccount && normalizedAccount(currentAccount) === normalizedAccount(normalizedEmail)
            ? await readAccountState(normalizedEmail)
            // A new account must not silently receive the whole library accumulated
            // under a different account. Existing local items remain in Mindwtr;
            // only later edits are candidates for outbound sync.
            : newAccountState(normalizedAccount(normalizedEmail),
                localData.tasks.length + localData.projects.length + localData.sections.length + localData.areas.length > 0
                    ? localData
                    : null);

        // All three values are secure allowlisted keys; getSecureConfigValue migrates
        // any pre-existing plaintext values before returning them.
        await setSecureConfigValue(KEY_EMAIL, normalizedEmail);
        await setSecureConfigValue(KEY_PASSWORD, password);
        await setSecureConfigValue(KEY_HISTORY_KEY, historyKey);
        await writeAccountState(currentState);

        logInfo('Things Cloud authentication successful.');

        // Immediately perform initial sync
        const result = await syncThingsCloud();
        if (!result.success) throw new Error(result.error || 'Things Cloud initial sync failed.');
        return true;
    } catch (err: any) {
        const message = safeErrorMessage(err);
        logSyncError('Things Cloud login failed.');
        const activeEmail = await getSecureConfigValue(KEY_EMAIL);
        if (activeEmail) {
            const state = await readAccountState(activeEmail);
            await writeAccountState({ ...state, lastError: message });
        }
        throw new Error(message);
    }
}

/**
 * Disconnects from Things Cloud and clears cached cloud tokens.
 */
export async function logoutThingsCloud(): Promise<void> {
    await deleteSecureConfigValue(KEY_EMAIL);
    await deleteSecureConfigValue(KEY_PASSWORD);
    await deleteSecureConfigValue(KEY_HISTORY_KEY);
    // Keep account-scoped cursor, baseline and any uncertain outbox. Logout must
    // not discard operations that may already have been accepted by the server.
    logInfo('Things Cloud account disconnected.');
}

/**
 * Performs a two-way synchronization cycle with Things Cloud.
 */
export async function syncThingsCloud(): Promise<{
    success: boolean;
    pulled: number;
    pushed: number;
    error?: string;
}> {
    if (syncInFlight) {
        return { success: false, pulled: 0, pushed: 0, error: 'Sync already in progress' };
    }

    const email = await getSecureConfigValue(KEY_EMAIL);
    const password = await getSecureConfigValue(KEY_PASSWORD);
    const historyKey = (await getSecureConfigValue(KEY_HISTORY_KEY)) || undefined;

    if (!email || (!password && !historyKey)) {
        return { success: false, pulled: 0, pushed: 0, error: 'Things Cloud not configured' };
    }

    syncInFlight = true;
    let activeEngine: ThingsCloudSyncEngine | null = null;

    try {
        const state = await readAccountState(email);
        if (state.conflicts.length > 0) {
            return {
                success: false,
                pulled: 0,
                pushed: 0,
                error: 'Resolve saved Things Cloud conflicts before syncing again.',
            };
        }
        const serverIndex = state.serverIndex;

        const client = new ThingsCloudClient(email, password, historyKey, serverIndex, {
            appInstanceId: await getOrCreateInstanceId(),
        });

        const tagState = state.tagState;
        const engine = new ThingsCloudSyncEngine(client, tagState, state.wireRecords);
        activeEngine = engine;

        // Drain the app's existing debounced local writes before selecting a
        // sync snapshot; a crash must not leave a server commit for an edit that
        // was never durable locally.
        await flushPendingSave();
        const cycleFingerprint = getInMemorySyncChangeFingerprint();
        let currentData = getInMemoryAppDataSnapshot();
        if (getInMemorySyncChangeFingerprint() !== cycleFingerprint) {
            throw new Error('Local tasks changed while sync was starting. Try again.');
        }

        // Upgrade path: this install synced before tags were supported, so its saved
        // index is past the tag entities. Read them once and fix raw tag uuids that
        // the old sync left on tasks.
        if (!tagState && serverIndex > 0) {
            logInfo('Things Cloud: loading tags from history (one-time upgrade)...');
            await engine.loadTagRegistry();
            currentData = engine.repairLegacyTagIds(currentData);
        }

        const baselineData = state.baselineData;

        logInfo(`Starting Things Cloud sync cycle (server index: ${serverIndex})...`);
        if (state.outbox) {
            // A prior POST may have reached the server even though this process
            // did not receive its response. Pull from the unchanged checkpoint.
            // Match one history commit as a unit; allow server-added normalized
            // fields, but never infer success from a partial or unrelated event.
            const pulled = await engine.pull(currentData, serverIndex);
            if (getInMemorySyncChangeFingerprint() !== cycleFingerprint) {
                throw new ThingsCloudError('Local tasks changed during recovery; retry reconciliation before advancing the checkpoint.');
            }
            const reconciled = pulled.receivedBatches.some((batch) =>
                historyBatchConfirmsChanges(state.outbox!.changes, batch));
            if (!reconciled) {
                throw new ThingsCloudError(
                    'A previous Things Cloud write has an unknown outcome. Sync is paused to avoid replaying it; inspect Things and contact support if it remains unresolved.',
                    undefined,
                    undefined,
                    true,
                );
            }
            const nowIso = new Date().toISOString();
            const nextState = {
                ...state,
                serverIndex: pulled.newServerIndex,
                baselineData: pulled.data,
                tagState: engine.tagState,
                outbox: null,
                wireRecords: pulled.wireRecords,
                lastSyncTime: nowIso,
                lastError: null,
            };
            await saveDataAndAccountState(pulled.data, nextState);
            applySyncedDataToStore(pulled.data);
            return { success: true, pulled: pulled.changeCount, pushed: 0 };
        }

        let commitPrepared = false;
        const result = await engine.sync(currentData, baselineData, serverIndex, {
            onCommitPrepared: async (changes, ancestorIndex) => {
                commitPrepared = true;
                await writeAccountState({
                    ...state,
                    outbox: { createdAt: new Date().toISOString(), ancestorIndex, changes },
                });
            },
            onCommitRejected: async () => {
                commitPrepared = false;
                await writeAccountState({ ...state, outbox: null });
            },
        });

        if (getInMemorySyncChangeFingerprint() !== cycleFingerprint) {
            throw new ThingsCloudError(
                'Local tasks changed during sync. The checkpoint was not advanced; any prepared remote write will be reconciled before retry.',
                undefined,
                undefined,
                commitPrepared,
            );
        }

        // Save task state first. If the process dies before the single checkpoint
        // record below lands, replay begins at the older cursor and is idempotent.
        const nowIso = new Date().toISOString();
        const nextState: ThingsCloudAccountState = {
            ...state,
            serverIndex: result.newServerIndex,
            lastSyncTime: nowIso,
            lastError: null,
            baselineData: result.data,
            tagState: engine.tagState,
            outbox: null,
            wireRecords: result.wireRecords,
        };
        await saveDataAndAccountState(result.data, nextState);
        applySyncedDataToStore(result.data);

        // Legacy metadata is no longer authoritative after the new envelope commits.
        await Promise.all([KEY_SERVER_INDEX, KEY_LAST_SYNC, KEY_LAST_ERROR, KEY_LAST_DATA, KEY_TAG_STATE]
            .map((key) => AsyncStorage.removeItem(key)));

        const conflictNote = result.conflictRetries > 0 ? ` (${result.conflictRetries} conflict retries)` : '';
        logInfo(
            `Things Cloud sync completed: +${result.pulledChangesCount} pulled, +${result.pushedChangesCount} pushed${conflictNote}. New index: ${result.newServerIndex}`
        );

        return {
            success: true,
            pulled: result.pulledChangesCount,
            pushed: result.pushedChangesCount,
        };
    } catch (err: any) {
        const message = safeErrorMessage(err);
        logSyncError('Things Cloud sync failed.');
        if (err instanceof ThingsCloudConcurrentEditError && email && activeEngine) {
            try {
                const state = await readAccountState(email);
                const conflictsById = new Map(state.conflicts.map((conflict) => [conflict.id, conflict]));
                for (const conflict of err.conflicts) conflictsById.set(conflict.id, conflict);
                const nextState: ThingsCloudAccountState = {
                    ...state,
                    serverIndex: err.newServerIndex,
                    baselineData: err.remoteData,
                    tagState: activeEngine.tagState,
                    wireRecords: err.wireRecords,
                    outbox: null,
                    conflicts: [...conflictsById.values()],
                    lastError: message,
                };
                await saveDataAndAccountState(err.remoteData, nextState);
                applySyncedDataToStore(err.remoteData);
            } catch {
                // Keep the pre-pull local snapshot and checkpoint if the atomic conflict save fails.
            }
            return { success: false, pulled: 0, pushed: 0, error: message };
        }
        if (email) {
            try {
                const state = await readAccountState(email);
                const rejectedConflict = err instanceof ThingsCloudError && isThingsCloudConflictError(err) && !err.outcomeUnknown;
                await writeAccountState({ ...state, lastError: message, ...(rejectedConflict ? { outbox: null } : {}) });
            } catch {
                // A failed durable status write must not replace the original sync failure.
            }
        }
        return { success: false, pulled: 0, pushed: 0, error: message };
    } finally {
        syncInFlight = false;
    }
}
