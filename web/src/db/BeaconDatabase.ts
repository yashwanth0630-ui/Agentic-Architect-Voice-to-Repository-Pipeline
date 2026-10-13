import { ProjectEntity, TimelineMilestoneEntity } from './schemas';
import { INITIAL_PROJECT, INITIAL_TIMELINE_EVENTS } from '../data/initialData';
import { BeaconIndexedDB } from './BeaconIndexedDB';

const STORAGE_KEYS = {
  PROJECT: 'idea_beacon_project_v2',
  TIMELINE: 'idea_beacon_timeline_v2',
  SETTINGS: 'idea_beacon_settings_v2',
};

type Listener = () => void;

class BeaconDatabaseClass {
  private listeners: Set<Listener> = new Set();

  constructor() {
    this.initDatabase();
    // Forward IndexedDB mutations to database listeners
    BeaconIndexedDB.subscribe(() => {
      this.notify();
    });
  }

  public subscribe(listener: Listener): () => void {
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
        console.error('Error notifying database listener', err);
      }
    });
  }

  private initDatabase() {
    try {
      if (!localStorage.getItem(STORAGE_KEYS.PROJECT)) {
        const seededProject: ProjectEntity = {
          ...INITIAL_PROJECT,
          isDemo: true,
          updatedAt: new Date().toISOString(),
        };
        localStorage.setItem(STORAGE_KEYS.PROJECT, JSON.stringify(seededProject));
      }

      if (!localStorage.getItem(STORAGE_KEYS.TIMELINE)) {
        const seededTimeline: TimelineMilestoneEntity[] = INITIAL_TIMELINE_EVENTS.map((evt) => ({
          id: evt.id,
          projectId: INITIAL_PROJECT.id,
          timestamp: evt.timestamp,
          title: evt.title,
          description: evt.description,
          statusBadge: evt.statusBadge || 'Logged',
          statusType: evt.statusType || 'authored',
          progress: evt.progress,
          progressStep: evt.progressStep,
          speed: evt.speed,
          tags: evt.tags,
          isDemo: true,
          createdAt: new Date().toISOString(),
        }));
        localStorage.setItem(STORAGE_KEYS.TIMELINE, JSON.stringify(seededTimeline));
      }
    } catch (e) {
      console.warn('LocalStorage unavailable in current context', e);
    }
  }

  // --- IDEA DAO (Unified through IndexedDB as single source of truth) ---
  public ideaDao = {
    getAll: () => BeaconIndexedDB.getAll(),

    getById: (id: string) => BeaconIndexedDB.getById(id),

    insert: (idea: any) => BeaconIndexedDB.captureIdeaSync(idea),

    update: (id: string, updates: any) => BeaconIndexedDB.updateIdeaSync(id, updates),

    delete: (id: string) => BeaconIndexedDB.deleteIdeaSync(id),

    retrySync: (id: string) => BeaconIndexedDB.retrySyncIdea(id),

    pushDraft: (id: string) => BeaconIndexedDB.retrySyncIdea(id),

    search: (
      queryText: string,
      options?: {
        scope?: 'all' | 'title' | 'tag' | 'content';
        category?: string;
      }
    ) => BeaconIndexedDB.search(queryText, options),

    resolveAllErrors: () => BeaconIndexedDB.resolveAllErrors(),

    resetToDemoSeed: () => BeaconIndexedDB.resetToDemoSeed(),
  };

  // --- PROJECT DAO (LocalStorage for small UI metadata) ---
  public projectDao = {
    get: (): ProjectEntity => {
      try {
        const raw = localStorage.getItem(STORAGE_KEYS.PROJECT);
        return raw ? JSON.parse(raw) : (INITIAL_PROJECT as ProjectEntity);
      } catch {
        return INITIAL_PROJECT as ProjectEntity;
      }
    },

    update: (updates: Partial<ProjectEntity>): ProjectEntity => {
      const current = this.projectDao.get();
      const updated: ProjectEntity = {
        ...current,
        ...updates,
        updatedAt: new Date().toISOString(),
      };
      localStorage.setItem(STORAGE_KEYS.PROJECT, JSON.stringify(updated));
      this.notify();
      return updated;
    },
  };

  // --- TIMELINE DAO (LocalStorage for small UI telemetry history) ---
  public timelineDao = {
    getAll: (): TimelineMilestoneEntity[] => {
      try {
        const raw = localStorage.getItem(STORAGE_KEYS.TIMELINE);
        return raw ? JSON.parse(raw) : [];
      } catch {
        return [];
      }
    },

    insert: (milestone: Omit<TimelineMilestoneEntity, 'id' | 'createdAt'>): TimelineMilestoneEntity => {
      const all = this.timelineDao.getAll();
      const newEntity: TimelineMilestoneEntity = {
        ...milestone,
        id: `evt-${Date.now()}`,
        createdAt: new Date().toISOString(),
      };
      const updated = [newEntity, ...all];
      localStorage.setItem(STORAGE_KEYS.TIMELINE, JSON.stringify(updated));
      this.notify();
      return newEntity;
    },
  };
}

export const BeaconDatabase = new BeaconDatabaseClass();
