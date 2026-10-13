export interface Idea {
  id: string;
  title: string;
  category: 'runtime' | 'database' | 'architecture' | 'systems' | 'infra' | 'wasm';
  preview: string;
  content: string;
  tags: string[];
  status: 'synced' | 'pending' | 'unsynced' | 'error' | 'draft';
  isDemo?: boolean;
  createdAt?: string;
  updatedAt: string;
  relativeTime: string;
  syncErrorMessage?: string;
  isFavorite?: boolean;
}

export interface SetupStep {
  id: number;
  title: string;
  duration?: string;
  description: string;
  status: 'completed' | 'active' | 'pending';
  progress?: number;
  activeTask?: string;
  details?: { label: string; value: string }[];
}

export interface TimelineEvent {
  id: string;
  timestamp: string;
  title: string;
  description: string;
  statusBadge?: string;
  statusType?: 'in-progress' | 'actions' | 'authored' | 'reconciled' | 'git';
  progress?: number;
  progressStep?: string;
  speed?: string;
  tags?: string[];
  specSnippet?: {
    title: string;
    description: string;
  };
}

export interface ProjectData {
  id: string;
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
}

export interface ChatMessage {
  id: string;
  sender: 'user' | 'assistant';
  timestamp: string;
  text?: string;
  isGenerating?: boolean;
  spec?: {
    architectureFlow: string;
    recommendedLibraries: { name: string; version: string }[];
    requirements: { label: string; value: string }[];
    tokenCount?: string;
  };
}

export interface DiffLine {
  type: 'context' | 'add' | 'del';
  content: string;
  oldLineNum?: number;
  newLineNum?: number;
}

export interface DiffHunk {
  oldStart: number;
  oldLines: number;
  newStart: number;
  newLines: number;
  lines: DiffLine[];
}

export interface CodeDiffFile {
  path: string;
  oldPath?: string;
  status: 'modified' | 'added' | 'deleted';
  additions: number;
  deletions: number;
  hunks: DiffHunk[];
}

export interface ChangeProposal {
  id: string;
  repoId: string;
  title: string;
  description: string;
  sourceIdeaTitle?: string;
  diffs: CodeDiffFile[];
  status: 'pending_review' | 'approved' | 'rejected' | 'committed';
  requiresApproval: boolean;
  createdAt: string;
  commitMessage?: string;
  committedHash?: string;
}

export interface Repository {
  id: string;
  name: string;
  owner: string;
  url: string;
  branch: string;
  status: 'connected' | 'syncing' | 'diverged' | 'offline';
  lastCommit: {
    hash: string;
    message: string;
    author: string;
    timestamp: string;
  };
  isLocalBridgeLinked?: boolean;
  activeProposal?: ChangeProposal;
  history?: Array<{
    hash: string;
    message: string;
    author: string;
    timestamp: string;
  }>;
}

export type ScreenType =
  | 'dashboard'
  | 'capture'
  | 'vault'
  | 'details'
  | 'brainstorm'
  | 'project'
  | 'stepper'
  | 'timeline';
