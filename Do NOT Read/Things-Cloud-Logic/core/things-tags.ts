/**
 * Tag translation between Things Cloud and Mindwtr.
 *
 * Things stores tags as separate entities (Tag4) and items refer to them by
 * uuid (`tg`). Mindwtr has no tag entities: a tag is just a `#name` string on
 * a task (`tags`) or project (`tagIds`, despite the name). So the sync layer
 * has to remember which uuid belongs to which title. That memory (the
 * "registry") must persist between syncs, because an incremental pull only
 * delivers tag entities that changed since the last sync.
 *
 * Tag hierarchy (`pn`, nested tags), shortcuts and Mindwtr contexts (`@home`)
 * are not mapped.
 */

export interface ThingsTagState {
    /** Things tag uuid -> tag title (without '#'). */
    titles: Record<string, string>;
    /**
     * Task/project id -> tag uuids on the server that could not be resolved to a
     * title. They are invisible locally but are re-sent on push so an unrelated
     * edit does not silently strip them on the server.
     */
    orphans: Record<string, string[]>;
}

export const emptyThingsTagState = (): ThingsTagState => ({ titles: {}, orphans: {} });

/** "#Work " -> "Work". */
export function tagNameToTitle(name: string): string {
    return name.trim().replace(/^#+/, '').trim();
}

/** "Work" -> "#Work". */
export function tagTitleToName(title: string): string {
    return `#${tagNameToTitle(title)}`;
}

/** Case-insensitive identity used to match names and titles. */
export function tagKey(nameOrTitle: string): string {
    return tagNameToTitle(nameOrTitle).toLowerCase();
}

/** Removes empty entries and duplicates (by tagKey), keeping the first spelling. */
export function dedupeTagNames(names: readonly string[]): string[] {
    const seen = new Set<string>();
    const result: string[] = [];
    for (const name of names) {
        const key = tagKey(name);
        if (!key || seen.has(key)) continue;
        seen.add(key);
        result.push(name);
    }
    return result;
}

/** True when both lists contain the same tags, ignoring order, case and '#'. */
export function sameTagSet(a: readonly string[] | undefined, b: readonly string[] | undefined): boolean {
    const keysA = new Set((a ?? []).map(tagKey).filter(Boolean));
    const keysB = new Set((b ?? []).map(tagKey).filter(Boolean));
    if (keysA.size !== keysB.size) return false;
    for (const key of keysA) {
        if (!keysB.has(key)) return false;
    }
    return true;
}

export class ThingsTagRegistry {
    private titles = new Map<string, string>();
    private orphans = new Map<string, string[]>();
    /** Tags minted locally during a push that the server has not confirmed yet. */
    private pending = new Map<string, string>();
    /** Tags the server deleted during this registry's lifetime. */
    private removedIds = new Set<string>();

    constructor(state?: Partial<ThingsTagState> | null) {
        for (const [uuid, title] of Object.entries(state?.titles ?? {})) {
            const clean = typeof title === 'string' ? tagNameToTitle(title) : '';
            if (clean) this.titles.set(uuid, clean);
        }
        for (const [ownerId, ids] of Object.entries(state?.orphans ?? {})) {
            const valid = Array.isArray(ids) ? ids.filter((id): id is string => typeof id === 'string') : [];
            if (valid.length > 0) this.orphans.set(ownerId, valid);
        }
    }

    /** State to persist. Unconfirmed (pending) tags are excluded. */
    snapshot(): ThingsTagState {
        const titles: Record<string, string> = {};
        for (const [uuid, title] of this.titles) {
            if (!this.pending.has(uuid)) titles[uuid] = title;
        }
        return { titles, orphans: Object.fromEntries(this.orphans) };
    }

    get size(): number {
        return this.titles.size;
    }

    titleOf(uuid: string): string | undefined {
        return this.titles.get(uuid);
    }

    uuidForName(name: string): string | undefined {
        const key = tagKey(name);
        if (!key) return undefined;
        for (const [uuid, title] of this.titles) {
            if (tagKey(title) === key) return uuid;
        }
        return undefined;
    }

    /** Records a tag the server created or renamed. */
    set(uuid: string, title: string): { previousTitle?: string } {
        const clean = tagNameToTitle(title);
        if (!clean) return {};
        const previousTitle = this.titles.get(uuid);
        // The server now has a tag with this title: drop our own unsent duplicate
        // so the next push refers to the server's tag.
        for (const [pendingUuid, pendingTitle] of this.pending) {
            if (pendingUuid !== uuid && tagKey(pendingTitle) === tagKey(clean)) {
                this.pending.delete(pendingUuid);
                this.titles.delete(pendingUuid);
            }
        }
        this.removedIds.delete(uuid);
        this.titles.set(uuid, clean);
        return { previousTitle };
    }

    /** Records a tag the server deleted; returns its former title. */
    remove(uuid: string): string | undefined {
        const previous = this.titles.get(uuid);
        this.titles.delete(uuid);
        this.pending.delete(uuid);
        this.removedIds.add(uuid);
        for (const [ownerId, ids] of this.orphans) {
            const rest = ids.filter((id) => id !== uuid);
            if (rest.length === 0) this.orphans.delete(ownerId);
            else if (rest.length !== ids.length) this.orphans.set(ownerId, rest);
        }
        return previous;
    }

    /** Things tag uuids (from `tg`) -> Mindwtr `#name`s. Unknown uuids become orphans. */
    namesFor(ownerId: string, uuids: readonly string[]): string[] {
        const names: string[] = [];
        const unresolved: string[] = [];
        for (const uuid of uuids) {
            const title = this.titles.get(uuid);
            if (title) {
                names.push(tagTitleToName(title));
            } else if (!this.removedIds.has(uuid)) {
                unresolved.push(uuid);
            }
        }
        if (unresolved.length > 0) this.orphans.set(ownerId, unresolved);
        else this.orphans.delete(ownerId);
        return dedupeTagNames(names);
    }

    /**
     * Mindwtr `#name`s -> Things tag uuids. Names with no matching tag get a new
     * uuid from `mint`; those are "pending" until `confirmPending()`.
     */
    uuidsFor(ownerId: string, names: readonly string[], mint: () => string): string[] {
        const uuids: string[] = [];
        for (const name of dedupeTagNames(names)) {
            let uuid = this.uuidForName(name);
            if (!uuid) {
                uuid = mint();
                const title = tagNameToTitle(name);
                this.titles.set(uuid, title);
                this.pending.set(uuid, title);
            }
            uuids.push(uuid);
        }
        for (const orphan of this.orphans.get(ownerId) ?? []) {
            if (!uuids.includes(orphan)) uuids.push(orphan);
        }
        return uuids;
    }

    pendingTags(): Array<{ uuid: string; title: string }> {
        return Array.from(this.pending, ([uuid, title]) => ({ uuid, title }));
    }

    /** Call after the commit that created the pending tags succeeded. */
    confirmPending(): void {
        this.pending.clear();
    }

    /** Forget tags minted for a push that ended up not creating anything. */
    discardPending(): void {
        for (const uuid of this.pending.keys()) this.titles.delete(uuid);
        this.pending.clear();
    }

    clearOwner(ownerId: string): void {
        this.orphans.delete(ownerId);
    }
}
