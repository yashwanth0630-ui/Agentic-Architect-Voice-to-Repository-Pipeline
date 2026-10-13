/**
 * Room / Jetpack Compose Data Schemas for Idea Beacon (IQ)
 * Mirroring Android Room @Entity definitions with offline-first persistence.
 */

export type IdeaCategory = 'runtime' | 'database' | 'architecture' | 'systems' | 'infra' | 'wasm';
export type SyncStatus = 'synced' | 'unsynced' | 'pending' | 'error' | 'draft';

export interface IdeaEntity {
  id: string; // @PrimaryKey
  title: string;
  category: IdeaCategory;
  preview: string;
  content: string;
  tags: string[];
  status: SyncStatus;
  isDemo: boolean; // Marks demo vs actual user-created data
  createdAt: string;
  updatedAt: string;
  relativeTime: string;
  syncErrorMessage?: string;
  isFavorite?: boolean;
  tech_stack?: string;
  lifecycleStatus?: string;
}

export interface ProjectEntity {
  id: string; // @PrimaryKey
  name: string;
  tagline: string;
  version: string;
  gitBranch: string;
  gitCommit: string;
  toolchain: string;
  health: string;
  kernel: string;
  daemonStatus: string;
  mapMem: string;
  eventsPerSec: string;
  verifierStatus: string;
  cpuAlloc: string;
  provisionStatusText: string;
  isDemo: boolean;
  updatedAt: string;
}

export interface TimelineMilestoneEntity {
  id: string; // @PrimaryKey
  projectId: string;
  timestamp: string;
  title: string;
  description: string;
  statusBadge: string;
  statusType: 'in-progress' | 'actions' | 'authored' | 'reconciled' | 'git';
  progress?: number;
  progressStep?: string;
  speed?: string;
  tags?: string[];
  isDemo: boolean;
  createdAt: string;
}
