/**
 * Types and wire structures for Cultured Code's Things Cloud sync protocol.
 * Synthesized from reverse-engineered implementations in things3-cloud and things-cloud-mcp (Task7).
 */

export interface ThingsClientInfo {
    dm: string; // Device Model, e.g. "MacBookPro18,3" or "iPhone14,2"
    lr: string; // Local Region, e.g. "US"
    nf: boolean; // Flag
    nk: boolean; // Flag
    nn: string; // App Name, e.g. "ThingsMac"
    nv: string; // App Version, e.g. "32209501"
    on: string; // OS Name, e.g. "macOS"
    ov: string; // OS Version, e.g. "15.7.3"
    pl: string; // Primary Language, e.g. "en-US"
    ul: string; // User Locale, e.g. "en-Latn-US"
}

/**
 * Client identity sent in the `things-client-info` header. The default says
 * plainly that this is Mindwtr.
 */
export const MINDWTR_THINGS_CLIENT_INFO: ThingsClientInfo = {
    dm: "Mindwtr",
    lr: "US",
    nf: true,
    nk: true,
    nn: "Mindwtr",
    nv: "1",
    on: "Mindwtr",
    ov: "1",
    pl: "en-US",
    ul: "en-Latn-US",
};

/**
 * Values copied from Things for Mac. Not used by default; kept only as a
 * fallback in case the server turns out to reject unknown clients.
 */
export const LEGACY_THINGS_MAC_CLIENT_INFO: ThingsClientInfo = {
    dm: "MacBookPro18,3",
    lr: "US",
    nf: true,
    nk: true,
    nn: "ThingsMac",
    nv: "32209501",
    on: "macOS",
    ov: "15.7.3",
    pl: "en-US",
    ul: "en-Latn-US",
};

export const DEFAULT_THINGS_CLIENT_INFO: ThingsClientInfo = MINDWTR_THINGS_CLIENT_INFO;

export const ThingsItemAction = {
    Created: 0,
    Modified: 1,
    Deleted: 2,
} as const;
export type ThingsItemAction = typeof ThingsItemAction[keyof typeof ThingsItemAction];

export const ThingsTaskType = {
    Todo: 0,
    Project: 1,
    Heading: 2,
} as const;
export type ThingsTaskType = typeof ThingsTaskType[keyof typeof ThingsTaskType];

export const ThingsTaskStatus = {
    Incomplete: 0,
    Canceled: 2,
    Completed: 3,
} as const;
export type ThingsTaskStatus = typeof ThingsTaskStatus[keyof typeof ThingsTaskStatus];

export const ThingsTaskSchedule = {
    Inbox: 0,
    Anytime: 1,
    Someday: 2,
} as const;
export type ThingsTaskSchedule = typeof ThingsTaskSchedule[keyof typeof ThingsTaskSchedule];

export type ThingsItemKind =
    | 'Task'
    | 'Task3'
    | 'Task4'
    | 'Task6'
    | 'Task7'
    | 'ChecklistItem'
    | 'ChecklistItem2'
    | 'ChecklistItem3'
    | 'Area'
    | 'Area2'
    | 'Area3'
    | 'Tag'
    | 'Tag3'
    | 'Tag4'
    | 'Tombstone'
    | 'Tombstone2'
    | 'Settings3'
    | 'Settings4'
    | 'Settings5'
    | 'Command';

export interface ThingsTaskWireProps {
    tt?: string; // Title
    nt?: string | { text?: string }; // Notes / Description
    tp?: ThingsTaskType; // Task Type: 0 = Todo, 1 = Project, 2 = Heading
    tr?: boolean; // Trashed (moved to Things' Trash). Field name from reverse-engineered payloads; unverified.
    ss?: ThingsTaskStatus; // Status: 0 = Incomplete, 2 = Canceled, 3 = Completed
    sp?: number | null; // Stop date (epoch seconds when completed/canceled)
    st?: ThingsTaskSchedule; // Schedule/Location: 0 = Inbox, 1 = Anytime, 2 = Someday
    sr?: number | null; // Scheduled date (day timestamp)
    tir?: number | null; // Today index reference day timestamp
    dd?: number | null; // Deadline day timestamp
    pr?: string[]; // Parent project IDs
    ar?: string[]; // Area IDs
    agr?: string[]; // Action group / Heading IDs
    tg?: string[]; // Tag IDs
    ix?: number; // Structural sort index in container
    ti?: number; // Today-view sort index
    cd?: number; // Creation date (epoch seconds)
    md?: number | null; // Modification date (epoch seconds, null on create)
    leaves_tombstone?: boolean;
    [key: string]: unknown;
}

export interface ThingsChecklistWireProps {
    tt?: string; // Title
    ss?: ThingsTaskStatus; // Status (0 = Incomplete, 3 = Completed)
    sp?: number | null; // Stop date (epoch seconds)
    ts?: string[]; // Parent task IDs (single task UUID)
    ix?: number; // Sort index
    cd?: number; // Creation date
    md?: number | null; // Modification date
    [key: string]: unknown;
}

export interface ThingsAreaWireProps {
    tt?: string; // Title / Name
    ix?: number; // Sort index
    tg?: string[]; // Tag IDs
    cd?: number; // Creation date
    md?: number | null; // Modification date
    [key: string]: unknown;
}

export interface ThingsTagWireProps {
    tt?: string; // Title
    sc?: string; // Shortcut key
    pn?: string[]; // Parent tag IDs
    ix?: number; // Sort index
    cd?: number; // Creation date
    md?: number | null; // Modification date
    [key: string]: unknown;
}

export interface ThingsWireObject {
    t: ThingsItemAction; // 0 = Created, 1 = Modified, 2 = Deleted
    e: ThingsItemKind; // e.g. "Task6", "Task7", "ChecklistItem3", "Area3", "Tag4"
    p: Record<string, unknown>; // Properties
}

export interface ThingsItemsPageResponse {
    items: Array<Record<string, ThingsWireObject>>;
    'latest-total-content-size': number;
    'start-total-content-size': number;
    'end-total-content-size': number;
    'current-item-index': number;
    schema: number;
}

export interface ThingsCommitResponse {
    'server-head-index': number;
}

export interface ThingsHistoryResponse {
    'latest-schema-version': number;
    'latest-total-content-size': number;
    'is-empty': boolean;
    'latest-server-index': number;
}

export interface ThingsAuthResponse {
    'history-key': string;
    email?: string;
    [key: string]: unknown;
}

export interface ThingsCloudCredentials {
    email: string;
    password: string;
    historyKey?: string;
    latestServerIndex?: number;
}
