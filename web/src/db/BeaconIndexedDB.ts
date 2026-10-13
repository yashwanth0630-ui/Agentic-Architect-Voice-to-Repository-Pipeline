/**
 * Idea Beacon IndexedDB Persistent Cache & Synchronization Engine
 * Provides persistent browser-side caching, offline mutation queues,
 * stable ID generation, deletion tombstones, and idempotent delta sync.
 */

import { IdeaEntity, SyncStatus } from './schemas';
import { INITIAL_IDEAS } from '../data/initialData';

const DB_NAME = 'idea_beacon_cache_v1';
const DB_VERSION = 1;

export interface OfflineMutation {
  id: string;
  action: 'insert' | 'update' | 'delete';
  payload: Partial<IdeaEntity>;
  timestamp: string;
  attempts: number;
}

export interface DeletionTombstone {
  id: string;
  deletedAt: string;
  synced: boolean;
}

type DBListener = () => void;

class BeaconIndexedDBService {
  private dbPromise: Promise<IDBDatabase> | null = null;
  private memoryCache: Map<string, IdeaEntity> = new Map();
  private tombstones: Map<string, DeletionTombstone> = new Map();
  private listeners: Set<DBListener> = new Set();
  private isInitialized = false;

  constructor() {
    this.init();
  }

  public subscribe(listener: DBListener): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notify() {
    this.listeners.forEach((listener) => {
      try {
        listener();
      } catch (err) {
        console.error('Error notifying IndexedDB listener', err);
      }
    });
  }

  /**
   * Initializes IndexedDB with stores for ideas, mutations, tombstones, and settings.
   */
  private async getDB(): Promise<IDBDatabase> {
    if (this.dbPromise) return this.dbPromise;

    if (typeof window === 'undefined' || !window.indexedDB) {
      return Promise.reject(new Error('IndexedDB not supported in current environment'));
    }

    this.dbPromise = new Promise((resolve, reject) => {
      const request = window.indexedDB.open(DB_NAME, DB_VERSION);

      request.onupgradeneeded = (event) => {
        const db = (event.target as IDBOpenDBRequest).result;

        if (!db.objectStoreNames.contains('ideas')) {
          db.createObjectStore('ideas', { keyPath: 'id' });
        }
        if (!db.objectStoreNames.contains('mutations')) {
          db.createObjectStore('mutations', { keyPath: 'id' });
        }
        if (!db.objectStoreNames.contains('tombstones')) {
          db.createObjectStore('tombstones', { keyPath: 'id' });
        }
        if (!db.objectStoreNames.contains('settings')) {
          db.createObjectStore('settings', { keyPath: 'key' });
        }
      };

      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });

    return this.dbPromise;
  }

  /**
   * Populates in-memory cache from IndexedDB (or fallback seed)
   */
  public async init(): Promise<void> {
    try {
      const db = await this.getDB();

      // Load tombstones
      const tombstoneTx = db.transaction('tombstones', 'readonly');
      const tombstoneStore = tombstoneTx.objectStore('tombstones');
      const allTombstones: DeletionTombstone[] = await new Promise((res) => {
        const req = tombstoneStore.getAll();
        req.onsuccess = () => res(req.result || []);
        req.onerror = () => res([]);
      });
      allTombstones.forEach((t) => this.tombstones.set(t.id, t));

      // Load ideas
      const ideasTx = db.transaction('ideas', 'readonly');
      const ideasStore = ideasTx.objectStore('ideas');
      const allIdeas: IdeaEntity[] = await new Promise((res) => {
        const req = ideasStore.getAll();
        req.onsuccess = () => res(req.result || []);
        req.onerror = () => res([]);
      });

      if (allIdeas.length > 0) {
        allIdeas.forEach((idea) => {
          if (!this.tombstones.has(idea.id)) {
            this.memoryCache.set(idea.id, idea);
          }
        });
      } else {
        // Seed with initial ideas if empty
        const initialSeed: IdeaEntity[] = INITIAL_IDEAS.map((idea) => ({
          ...idea,
          isDemo: true,
          status: 'synced',
          createdAt: idea.updatedAt,
        }));

        const seedTx = db.transaction('ideas', 'readwrite');
        const seedStore = seedTx.objectStore('ideas');
        initialSeed.forEach((item) => {
          seedStore.put(item);
          this.memoryCache.set(item.id, item);
        });
      }

      this.isInitialized = true;
      this.notify();
    } catch (err) {
      console.warn('IndexedDB initialization fallback to localStorage', err);
      this.initFallbackLocalStorage();
    }
  }

  private initFallbackLocalStorage() {
    try {
      const raw = localStorage.getItem('idea_beacon_ideas_cache');
      if (raw) {
        const parsed = JSON.parse(raw);
        parsed.forEach((item: IdeaEntity) => this.memoryCache.set(item.id, item));
      } else {
        INITIAL_IDEAS.forEach((item) => {
          const entity: IdeaEntity = { ...item, isDemo: true, status: 'synced', createdAt: item.updatedAt };
          this.memoryCache.set(entity.id, entity);
        });
      }
    } catch {
      // Ignored
    }
    this.isInitialized = true;
    this.notify();
  }

  // --- Synchronous In-Memory Read API for 0ms Render Latency ---

  public getAll(): IdeaEntity[] {
    const list = Array.from(this.memoryCache.values());
    return list.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());
  }

  public getById(id: string): IdeaEntity | undefined {
    return this.memoryCache.get(id);
  }

  // --- Persistent Write Operations ---

  public async captureIdea(
    data: Omit<IdeaEntity, 'id' | 'createdAt' | 'updatedAt' | 'relativeTime' | 'status'> & {
      id?: string;
      status?: SyncStatus;
      relativeTime?: string;
    }
  ): Promise<IdeaEntity> {
    const now = new Date().toISOString();
    const id = data.id || `idea-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;

    // Prevent duplicate records if idea already exists with this ID
    if (this.memoryCache.has(id)) {
      return (await this.updateIdea(id, data)) || this.memoryCache.get(id)!;
    }

    const entity: IdeaEntity = {
      ...data,
      id,
      isDemo: data.isDemo ?? false,
      status: data.status || 'unsynced',
      createdAt: now,
      updatedAt: now,
      relativeTime: data.relativeTime || 'Edited just now',
    };

    // Update memory
    this.memoryCache.set(id, entity);
    this.tombstones.delete(id);
    this.notify();

    // Persist to IndexedDB
    try {
      const db = await this.getDB();
      const tx = db.transaction(['ideas', 'mutations'], 'readwrite');
      tx.objectStore('ideas').put(entity);
      tx.objectStore('mutations').put({
        id,
        action: 'insert',
        payload: entity,
        timestamp: now,
        attempts: 0,
      });
    } catch {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem('idea_beacon_ideas_cache', JSON.stringify(Array.from(this.memoryCache.values())));
      }
    }

    return entity;
  }

  public async updateIdea(id: string, updates: Partial<IdeaEntity>): Promise<IdeaEntity | null> {
    const existing = this.memoryCache.get(id);
    if (!existing) return null;

    const now = new Date().toISOString();
    const updated: IdeaEntity = {
      ...existing,
      ...updates,
      id: existing.id,
      updatedAt: now,
      status: updates.status || 'unsynced',
      relativeTime: 'Edited just now',
    };

    this.memoryCache.set(id, updated);
    this.notify();

    try {
      const db = await this.getDB();
      const tx = db.transaction(['ideas', 'mutations'], 'readwrite');
      tx.objectStore('ideas').put(updated);
      tx.objectStore('mutations').put({
        id,
        action: 'update',
        payload: updated,
        timestamp: now,
        attempts: 0,
      });
    } catch {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem('idea_beacon_ideas_cache', JSON.stringify(Array.from(this.memoryCache.values())));
      }
    }

    return updated;
  }

  public async deleteIdea(id: string): Promise<boolean> {
    const existing = this.memoryCache.get(id);
    if (!existing) return false;

    const now = new Date().toISOString();
    this.memoryCache.delete(id);
    const tombstone: DeletionTombstone = { id, deletedAt: now, synced: false };
    this.tombstones.set(id, tombstone);
    this.notify();

    try {
      const db = await this.getDB();
      const tx = db.transaction(['ideas', 'mutations', 'tombstones'], 'readwrite');
      tx.objectStore('ideas').delete(id);
      tx.objectStore('tombstones').put(tombstone);
      tx.objectStore('mutations').put({
        id,
        action: 'delete',
        payload: { id },
        timestamp: now,
        attempts: 0,
      });
    } catch {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem('idea_beacon_ideas_cache', JSON.stringify(Array.from(this.memoryCache.values())));
      }
    }

    return true;
  }

  public async getPendingMutations(): Promise<OfflineMutation[]> {
    try {
      const db = await this.getDB();
      const tx = db.transaction('mutations', 'readonly');
      return await new Promise((res) => {
        const req = tx.objectStore('mutations').getAll();
        req.onsuccess = () => res(req.result || []);
        req.onerror = () => res([]);
      });
    } catch {
      return [];
    }
  }

  public async getTombstones(): Promise<DeletionTombstone[]> {
    return Array.from(this.tombstones.values());
  }

  /**
   * Idempotent synchronization with the Laptop Bridge / API
   * Syncs queued mutations and tombstones, updates local ideas with server state
   */
  public async syncWithRemote(apiBaseUrl: string): Promise<{
    success: boolean;
    syncedCount: number;
    error?: string;
  }> {
    try {
      const pendingMutations = await this.getPendingMutations();
      const unsyncedTombstones = Array.from(this.tombstones.values()).filter((t) => !t.synced);

      const payload = {
        mutations: pendingMutations
          .filter((m) => m.action !== 'delete')
          .map((m) => m.payload),
        tombstones: unsyncedTombstones.map((t) => ({ id: t.id, deletedAt: t.deletedAt })),
      };

      const syncUrl = `${apiBaseUrl.replace(/\/$/, '')}/api/ideas/sync`;
      const response = await fetch(syncUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        throw new Error(`Sync failed with HTTP ${response.status}`);
      }

      const result = await response.json();
      const syncedIds: string[] = result.syncedIds || [];
      const tombstoneIds: string[] = result.tombstoneIds || [];
      const serverIdeas: IdeaEntity[] = result.serverIdeas || [];

      // Reconcile server ideas with local cache
      const db = await this.getDB();
      const tx = db.transaction(['ideas', 'mutations', 'tombstones'], 'readwrite');
      const ideaStore = tx.objectStore('ideas');
      const mutationStore = tx.objectStore('mutations');
      const tombstoneStore = tx.objectStore('tombstones');

      // Purge synced mutations
      syncedIds.forEach((id) => mutationStore.delete(id));
      tombstoneIds.forEach((id) => {
        mutationStore.delete(id);
        const t = this.tombstones.get(id);
        if (t) {
          t.synced = true;
          tombstoneStore.put(t);
        }
      });

      // Update local ideas with authoritative server ideas
      serverIdeas.forEach((item) => {
        if (!this.tombstones.has(item.id)) {
          const localItem = this.memoryCache.get(item.id);
          // Only update if server is same or newer
          const serverTime = new Date(item.updatedAt || 0).getTime();
          const localTime = new Date(localItem?.updatedAt || 0).getTime();
          if (!localItem || serverTime >= localTime) {
            const syncedEntity = { ...item, status: 'synced' as const };
            this.memoryCache.set(item.id, syncedEntity);
            ideaStore.put(syncedEntity);
          }
        }
      });

      this.notify();
      return { success: true, syncedCount: syncedIds.length + tombstoneIds.length };
    } catch (err: any) {
      console.warn('Sync attempt failed, mutations retained in queue:', err.message);
      return { success: false, syncedCount: 0, error: err.message };
    }
  }

  public async markSynced(id: string, serverData?: Partial<IdeaEntity>): Promise<void> {
    const existing = this.memoryCache.get(id);
    if (!existing) return;

    const syncedEntity: IdeaEntity = {
      ...existing,
      ...(serverData || {}),
      status: 'synced',
      syncErrorMessage: undefined,
      updatedAt: serverData?.updatedAt || existing.updatedAt || new Date().toISOString(),
    };

    this.memoryCache.set(id, syncedEntity);
    this.notify();

    try {
      const db = await this.getDB();
      const tx = db.transaction(['ideas', 'mutations'], 'readwrite');
      tx.objectStore('ideas').put(syncedEntity);
      tx.objectStore('mutations').delete(id);
    } catch {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem('idea_beacon_ideas_cache', JSON.stringify(Array.from(this.memoryCache.values())));
      }
    }
  }

  public captureIdeaSync(
    data: Omit<IdeaEntity, 'id' | 'createdAt' | 'updatedAt' | 'relativeTime' | 'status'> & {
      id?: string;
      status?: SyncStatus;
      relativeTime?: string;
    }
  ): IdeaEntity {
    const now = new Date().toISOString();
    const id = data.id || `idea-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;

    // Prevent duplicate records if idea already exists with this ID
    if (this.memoryCache.has(id)) {
      return this.updateIdeaSync(id, data) || this.memoryCache.get(id)!;
    }

    const entity: IdeaEntity = {
      ...data,
      id,
      isDemo: data.isDemo ?? false,
      status: data.status || 'unsynced',
      createdAt: now,
      updatedAt: now,
      relativeTime: data.relativeTime || 'Edited just now',
    };

    this.memoryCache.set(id, entity);
    this.tombstones.delete(id);
    this.notify();

    this.getDB().then((db) => {
      const tx = db.transaction(['ideas', 'mutations'], 'readwrite');
      tx.objectStore('ideas').put(entity);
      tx.objectStore('mutations').put({
        id,
        action: 'insert',
        payload: entity,
        timestamp: now,
        attempts: 0,
      });
    }).catch(console.warn);

    return entity;
  }

  public updateIdeaSync(id: string, updates: Partial<IdeaEntity>): IdeaEntity | null {
    const existing = this.memoryCache.get(id);
    if (!existing) return null;

    const now = new Date().toISOString();
    const updated: IdeaEntity = {
      ...existing,
      ...updates,
      id: existing.id,
      updatedAt: now,
      status: updates.status || 'unsynced',
      relativeTime: 'Edited just now',
    };

    this.memoryCache.set(id, updated);
    this.notify();

    this.getDB().then((db) => {
      const tx = db.transaction(['ideas', 'mutations'], 'readwrite');
      tx.objectStore('ideas').put(updated);
      tx.objectStore('mutations').put({
        id,
        action: 'update',
        payload: updated,
        timestamp: now,
        attempts: 0,
      });
    }).catch(console.warn);

    return updated;
  }

  public deleteIdeaSync(id: string): boolean {
    const existing = this.memoryCache.get(id);
    if (!existing) return false;

    const now = new Date().toISOString();
    this.memoryCache.delete(id);
    const tombstone: DeletionTombstone = { id, deletedAt: now, synced: false };
    this.tombstones.set(id, tombstone);
    this.notify();

    this.getDB().then((db) => {
      const tx = db.transaction(['ideas', 'mutations', 'tombstones'], 'readwrite');
      tx.objectStore('ideas').delete(id);
      tx.objectStore('tombstones').put(tombstone);
      tx.objectStore('mutations').put({
        id,
        action: 'delete',
        payload: { id },
        timestamp: now,
        attempts: 0,
      });
    }).catch(console.warn);

    return true;
  }

  public async retrySyncIdea(id: string, apiBaseUrl: string = ''): Promise<IdeaEntity | null> {
    const existing = this.memoryCache.get(id);
    if (!existing) return null;

    const targetUrl = apiBaseUrl ? `${apiBaseUrl.replace(/\/$/, '')}/api/ideas/capture` : '/api/ideas/capture';
    try {
      const res = await fetch(targetUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id: existing.id,
          prompt: existing.content || existing.title,
          title: existing.title,
          category: existing.category,
          tech_stack: existing.tech_stack,
        }),
      });

      if (res.ok) {
        const data = await res.json();
        await this.markSynced(id, data.idea);
        return this.memoryCache.get(id) || null;
      }

      // Record honest failure message without claiming synced
      const errUpdated: IdeaEntity = {
        ...existing,
        status: 'error',
        syncErrorMessage: `Bridge returned HTTP ${res.status}`,
      };
      this.memoryCache.set(id, errUpdated);
      this.notify();
      return errUpdated;
    } catch (err: any) {
      const errUpdated: IdeaEntity = {
        ...existing,
        status: 'unsynced',
        syncErrorMessage: `Bridge unreachable: ${err.message}`,
      };
      this.memoryCache.set(id, errUpdated);
      this.notify();
      return errUpdated;
    }
  }

  public async resolveAllErrors(apiBaseUrl: string = ''): Promise<number> {
    const errorIdeas = Array.from(this.memoryCache.values()).filter(
      (i) => i.status === 'error' || i.status === 'unsynced'
    );
    let resolved = 0;
    for (const item of errorIdeas) {
      const res = await this.retrySyncIdea(item.id, apiBaseUrl);
      if (res && res.status === 'synced') resolved++;
    }
    return resolved;
  }

  public async resetToDemoSeed(): Promise<void> {
    const initialSeed: IdeaEntity[] = INITIAL_IDEAS.map((idea) => ({
      ...idea,
      isDemo: true,
      status: 'synced',
      createdAt: idea.updatedAt,
    }));

    this.memoryCache.clear();
    this.tombstones.clear();
    initialSeed.forEach((item) => this.memoryCache.set(item.id, item));
    this.notify();

    try {
      const db = await this.getDB();
      const tx = db.transaction(['ideas', 'mutations', 'tombstones'], 'readwrite');
      tx.objectStore('ideas').clear();
      tx.objectStore('mutations').clear();
      tx.objectStore('tombstones').clear();
      initialSeed.forEach((item) => tx.objectStore('ideas').put(item));
    } catch {
      // Ignored
    }
  }

  public search(
    queryText: string,
    options?: {
      scope?: 'all' | 'title' | 'tag' | 'content';
      category?: string;
    }
  ): IdeaEntity[] {
    const all = this.getAll();
    const trimmed = queryText.trim().toLowerCase();
    const scope = options?.scope || 'all';
    const cat = options?.category;

    return all.filter((idea) => {
      if (cat && cat !== 'all' && idea.category.toLowerCase() !== cat.toLowerCase()) {
        return false;
      }

      if (!trimmed) return true;

      const cleanQuery = trimmed.replace(/^#/, '');
      const matchesTitle = idea.title.toLowerCase().includes(trimmed);
      const matchesTag = idea.tags.some(
        (t) =>
          t.toLowerCase().includes(trimmed) ||
          t.toLowerCase().replace(/^#/, '').includes(cleanQuery)
      );
      const matchesContent =
        (idea.content && idea.content.toLowerCase().includes(trimmed)) ||
        (idea.preview && idea.preview.toLowerCase().includes(trimmed));

      switch (scope) {
        case 'title':
          return matchesTitle;
        case 'tag':
          return matchesTag;
        case 'content':
          return matchesContent;
        case 'all':
        default:
          return matchesTitle || matchesTag || matchesContent;
      }
    });
  }

  // Settings Storage
  public async getSetting(key: string, defaultValue: string = ''): Promise<string> {
    try {
      const db = await this.getDB();
      const tx = db.transaction('settings', 'readonly');
      return await new Promise((res) => {
        const req = tx.objectStore('settings').get(key);
        req.onsuccess = () => res(req.result?.value ?? defaultValue);
        req.onerror = () => res(defaultValue);
      });
    } catch {
      if (typeof localStorage !== 'undefined') {
        return localStorage.getItem(`setting_${key}`) || defaultValue;
      }
      return defaultValue;
    }
  }

  public async setSetting(key: string, value: string): Promise<void> {
    try {
      const db = await this.getDB();
      const tx = db.transaction('settings', 'readwrite');
      tx.objectStore('settings').put({ key, value });
    } catch {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem(`setting_${key}`, value);
      }
    }
  }
}

export const BeaconIndexedDB = new BeaconIndexedDBService();
