/**
 * Laptop Bridge Connection & Sync Service
 * Manages connectivity checks, latency measurement, mixed-content detection,
 * and authoritative idea capture through the FastAPI laptop bridge.
 */

import { IdeaEntity, SyncStatus } from '../db/schemas';
import { BeaconIndexedDB } from '../db/BeaconIndexedDB';

export type BridgeStatus = 'CONNECTED' | 'CONNECTING' | 'DISCONNECTED' | 'MIXED_CONTENT_BLOCKED' | 'TIMEOUT';

export interface BridgeDiagnostics {
  status: BridgeStatus;
  url: string;
  latencyMs?: number;
  message: string;
  vaultCount?: number;
  lastChecked: string;
  isLocalhost: boolean;
}

const DEFAULT_BRIDGE_URL = 'http://localhost:8000';
const SETTING_BRIDGE_URL = 'idea_beacon_bridge_url';
const SETTING_COMPANION_MODE = 'idea_beacon_companion_mode';

class BridgeService {
  private currentUrl: string = DEFAULT_BRIDGE_URL;
  private currentStatus: BridgeStatus = 'DISCONNECTED';
  private lastDiagnostics: BridgeDiagnostics | null = null;
  private listeners: Set<(diag: BridgeDiagnostics) => void> = new Set();

  constructor() {
    this.init();
  }

  private async init() {
    try {
      const saved = await BeaconIndexedDB.getSetting(SETTING_BRIDGE_URL, DEFAULT_BRIDGE_URL);
      this.currentUrl = saved || DEFAULT_BRIDGE_URL;
    } catch {
      this.currentUrl = DEFAULT_BRIDGE_URL;
    }
  }

  public subscribe(listener: (diag: BridgeDiagnostics) => void): () => void {
    this.listeners.add(listener);
    if (this.lastDiagnostics) {
      listener(this.lastDiagnostics);
    }
    return () => this.listeners.delete(listener);
  }

  private notify(diag: BridgeDiagnostics) {
    this.lastDiagnostics = diag;
    this.currentStatus = diag.status;
    this.listeners.forEach((fn) => fn(diag));
  }

  public getBridgeUrl(): string {
    return this.currentUrl;
  }

  public async setBridgeUrl(url: string): Promise<BridgeDiagnostics> {
    const cleanUrl = url.trim().replace(/\/$/, '');
    this.currentUrl = cleanUrl || DEFAULT_BRIDGE_URL;
    await BeaconIndexedDB.setSetting(SETTING_BRIDGE_URL, this.currentUrl);
    return this.checkConnection();
  }

  /**
   * Performs an honest connectivity check with timeout & mixed-content detection.
   */
  public async checkConnection(targetUrl?: string): Promise<BridgeDiagnostics> {
    const url = (targetUrl || this.currentUrl).trim().replace(/\/$/, '');
    const startTime = Date.now();
    const isHttpsPage = typeof window !== 'undefined' && window.location.protocol === 'https:';
    const isHttpTarget = url.startsWith('http://');
    const isLocal = url.includes('localhost') || url.includes('127.0.0.1');

    this.notify({
      status: 'CONNECTING',
      url,
      message: `Probing bridge at ${url}...`,
      lastChecked: new Date().toISOString(),
      isLocalhost: isLocal,
    });

    // 1. First test local/direct reachability if applicable
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 3500);

    try {
      // Browser fetch to direct bridge or relative /api endpoint
      const pingUrl = url === '/api' || url === '' ? '/api/bridge/health' : `${url}/api/bridge/health`;
      const directResponse = await fetch(pingUrl, {
        method: 'GET',
        signal: controller.signal,
        headers: { Accept: 'application/json' },
      });
      clearTimeout(timeoutId);

      const latencyMs = Date.now() - startTime;
      if (directResponse.ok) {
        const data = await directResponse.json();
        const diag: BridgeDiagnostics = {
          status: 'CONNECTED',
          url,
          latencyMs,
          message: `Connected (${latencyMs}ms)`,
          vaultCount: data.vaultCount,
          lastChecked: new Date().toISOString(),
          isLocalhost: isLocal,
        };
        this.notify(diag);
        return diag;
      }
    } catch (err: any) {
      clearTimeout(timeoutId);

      // Check if browser blocked mixed content (HTTPS -> HTTP)
      if (isHttpsPage && isHttpTarget && !isLocal) {
        const diag: BridgeDiagnostics = {
          status: 'MIXED_CONTENT_BLOCKED',
          url,
          message: 'Browser blocked insecure HTTP from HTTPS workspace. Use an HTTPS tunnel or local dev mode.',
          lastChecked: new Date().toISOString(),
          isLocalhost: isLocal,
        };
        this.notify(diag);
        return diag;
      }
    }

    // 2. Check the server-side proxy tester in case bridge is accessible via server
    try {
      const testRes = await fetch('/api/bridge/test-connection', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ targetUrl: url, timeoutMs: 3000 }),
      });
      const testData = await testRes.json();

      if (testData.reachable) {
        const diag: BridgeDiagnostics = {
          status: 'CONNECTED',
          url,
          latencyMs: testData.latencyMs,
          message: `Bridge reachable (${testData.latencyMs}ms)`,
          lastChecked: new Date().toISOString(),
          isLocalhost: isLocal,
        };
        this.notify(diag);
        return diag;
      }
    } catch {
      // Ignore proxy error and return honest disconnected state
    }

    const elapsed = Date.now() - startTime;
    const diag: BridgeDiagnostics = {
      status: elapsed >= 3000 ? 'TIMEOUT' : 'DISCONNECTED',
      url,
      message: `Unreachable at ${url}. Operating in local companion cache mode.`,
      lastChecked: new Date().toISOString(),
      isLocalhost: isLocal,
    };
    this.notify(diag);
    return diag;
  }

  /**
   * Captures idea: ALWAYS saves to local IndexedDB first with status 'unsynced',
   * then attempts bridge sync if connected. Never marks synced on network failure.
   */
  public async captureIdea(
    ideaData: Omit<IdeaEntity, 'id' | 'createdAt' | 'updatedAt' | 'relativeTime' | 'status'> & {
      id?: string;
      status?: SyncStatus;
    }
  ): Promise<{ idea: IdeaEntity; viaBridge: boolean; message: string }> {
    // 1. ALWAYS persist locally to IndexedDB first with stable ID and 'unsynced' status
    const localSaved = await BeaconIndexedDB.captureIdea({
      ...ideaData,
      status: 'unsynced',
    });

    // 2. If bridge is not connected, keep idea safe in offline queue
    if (this.currentStatus !== 'CONNECTED') {
      return {
        idea: localSaved,
        viaBridge: false,
        message: 'Saved locally to IndexedDB cache (queued for bridge sync)',
      };
    }

    // 3. Attempt network sync with bridge/server
    const targetEndpoint = this.currentUrl !== '/api'
      ? `${this.currentUrl}/api/ideas/capture`
      : '/api/ideas/capture';

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 4000);

    try {
      const response = await fetch(targetEndpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id: localSaved.id,
          prompt: localSaved.content || localSaved.title,
          title: localSaved.title,
          category: localSaved.category,
          tech_stack: localSaved.tech_stack,
        }),
        signal: controller.signal,
      });
      clearTimeout(timeoutId);

      if (response.ok) {
        const result = await response.json();
        const serverIdea: IdeaEntity = result.idea;

        // Mark local entity as synced
        await BeaconIndexedDB.markSynced(localSaved.id, serverIdea);
        const syncedEntity = BeaconIndexedDB.getById(localSaved.id) || localSaved;

        return {
          idea: syncedEntity,
          viaBridge: true,
          message: 'Saved and synchronized with laptop bridge',
        };
      } else {
        // Record honest failure message without claiming synced
        await BeaconIndexedDB.updateIdea(localSaved.id, {
          status: 'error',
          syncErrorMessage: `Bridge returned HTTP ${response.status}`,
        });
      }
    } catch (err: any) {
      clearTimeout(timeoutId);
      console.warn('Bridge capture unreachable, idea safely retained offline in IndexedDB:', err);
    }

    return {
      idea: BeaconIndexedDB.getById(localSaved.id) || localSaved,
      viaBridge: false,
      message: 'Saved locally to IndexedDB cache (queued for bridge sync)',
    };
  }

  /**
   * Updates an existing idea: ALWAYS persists to local IndexedDB first with 'unsynced' status,
   * keeping the idea ID stable and preventing duplicate inserts.
   * Then attempts network update if bridge is connected.
   * Never marks synced on failure or without server confirmation.
   */
  public async updateIdea(
    id: string,
    updates: Partial<IdeaEntity>
  ): Promise<{ idea: IdeaEntity | null; viaBridge: boolean; message: string }> {
    // 1. Persist updates locally in IndexedDB first (offline-first)
    const localUpdated = await BeaconIndexedDB.updateIdea(id, {
      ...updates,
      status: 'unsynced',
    });

    if (!localUpdated) {
      return {
        idea: null,
        viaBridge: false,
        message: `Idea with ID ${id} not found in local vault`,
      };
    }

    // 2. If bridge is not connected, keep mutation queued
    if (this.currentStatus !== 'CONNECTED') {
      return {
        idea: localUpdated,
        viaBridge: false,
        message: 'Updated locally in IndexedDB vault (queued for bridge sync)',
      };
    }

    // 3. Attempt network update with bridge/server
    const targetEndpoint = this.currentUrl !== '/api'
      ? `${this.currentUrl}/api/ideas/${id}`
      : `/api/ideas/${id}`;

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 4000);

    try {
      const response = await fetch(targetEndpoint, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: localUpdated.title,
          category: localUpdated.category,
          prompt: localUpdated.content || localUpdated.title,
          content: localUpdated.content,
          tags: localUpdated.tags,
        }),
        signal: controller.signal,
      });
      clearTimeout(timeoutId);

      if (response.ok) {
        const result = await response.json();
        const serverIdea = result.idea;
        await BeaconIndexedDB.markSynced(id, serverIdea);
        const syncedEntity = BeaconIndexedDB.getById(id) || localUpdated;
        return {
          idea: syncedEntity,
          viaBridge: true,
          message: 'Updated and synchronized with laptop bridge',
        };
      } else {
        await BeaconIndexedDB.updateIdea(id, {
          status: 'error',
          syncErrorMessage: `Bridge update returned HTTP ${response.status}`,
        });
      }
    } catch (err: any) {
      clearTimeout(timeoutId);
      console.warn('Bridge update unreachable, update safely retained offline in IndexedDB:', err);
    }

    return {
      idea: BeaconIndexedDB.getById(id) || localUpdated,
      viaBridge: false,
      message: 'Updated locally in IndexedDB vault (queued for bridge sync)',
    };
  }

  /**
   * Syncs pending mutations to the bridge
   */
  public async syncVault(): Promise<{ success: boolean; syncedCount: number; error?: string }> {
    const targetBase = this.currentStatus === 'CONNECTED' && this.currentUrl !== '/api'
      ? this.currentUrl
      : window.location.origin;

    return BeaconIndexedDB.syncWithRemote(targetBase);
  }

  /**
   * Fetches connected repositories from bridge
   */
  public async getRepositories(): Promise<any[]> {
    try {
      const res = await fetch('/api/repos');
      if (res.ok) {
        const data = await res.json();
        return data.repos || [];
      }
    } catch (err) {
      console.warn('Failed to fetch repositories from bridge:', err);
    }
    return [];
  }

  /**
   * Connects a new GitHub repository or local bridge workspace
   */
  public async connectRepository(payload: {
    name: string;
    url?: string;
    branch?: string;
    isLocalBridgeLinked?: boolean;
  }): Promise<{ success: boolean; repo?: any; error?: string }> {
    try {
      const res = await fetch('/api/repos/connect', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      const data = await res.json();
      if (res.ok) {
        return { success: true, repo: data.repo };
      }
      return { success: false, error: data.error || 'Failed to connect repository' };
    } catch (err: any) {
      return { success: false, error: err.message || 'Network error connecting repository' };
    }
  }

  /**
   * Fetches pending diffs for a repository
   */
  public async getRepositoryDiffs(repoId: string): Promise<{ proposal: any; diffs: any[] }> {
    try {
      const res = await fetch(`/api/repos/${repoId}/diffs`);
      if (res.ok) {
        const data = await res.json();
        return { proposal: data.proposal, diffs: data.diffs || [] };
      }
    } catch (err) {
      console.warn('Failed to fetch diffs for repo:', repoId, err);
    }
    return { proposal: null, diffs: [] };
  }

  /**
   * Proposes a new code diff specification for a repository
   */
  public async proposeDiff(
    repoId: string,
    payload: { title: string; description?: string; sourceIdeaTitle?: string }
  ): Promise<{ success: boolean; proposal?: any; error?: string }> {
    try {
      const res = await fetch(`/api/repos/${repoId}/propose-diff`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      const data = await res.json();
      if (res.ok) {
        return { success: true, proposal: data.proposal };
      }
      return { success: false, error: data.error || 'Failed to propose diff' };
    } catch (err: any) {
      return { success: false, error: err.message };
    }
  }

  /**
   * Applies and commits changes to repository with explicit Hold-to-Confirm gate
   */
  public async applyRepositoryChanges(
    repoId: string,
    confirmed: boolean,
    commitMessage?: string,
    selectedFiles: string[] = []
  ): Promise<{ success: boolean; commitHash?: string; message?: string; error?: string; requiresApproval?: boolean }> {
    try {
      const res = await fetch(`/api/repos/${repoId}/apply`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          confirmed,
          commitMessage,
          selected_files: selectedFiles,
          files: selectedFiles,
        }),
      });
      const data = await res.json();
      if (res.ok) {
        return {
          success: true,
          commitHash: data.commitHash || data.commit_hash,
          message: data.message,
        };
      }
      return {
        success: false,
        error: data.error?.message || data.error,
        requiresApproval: data.requiresApproval,
      };
    } catch (err: any) {
      return { success: false, error: err.message };
    }
  }

  // -------------------------------------------------------------
  // Real Workspace & Controlled Publishing Operations
  // -------------------------------------------------------------

  public async getGitHubStatus(): Promise<{ authenticated: boolean; user?: string; error?: string }> {
    try {
      const res = await fetch('/api/github/status');
      return await res.json();
    } catch (err: any) {
      return { authenticated: false, error: err.message };
    }
  }

  public async getGitHubRepos(): Promise<any[]> {
    try {
      const res = await fetch('/api/github/repos');
      if (res.ok) {
        const data = await res.json();
        return data.repos || [];
      }
    } catch (err) {
      console.warn('Failed to fetch GitHub repos:', err);
    }
    return [];
  }

  public async initWorkspace(payload: {
    repo_url?: string;
    branch?: string;
    explicit_workspace_id?: string;
  }): Promise<{ success: boolean; workspace_id?: string; workspace?: any; error?: string }> {
    try {
      const res = await fetch('/api/repo/workspace/init', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      const data = await res.json();
      return {
        success: res.ok && data.success !== false,
        workspace_id: data.workspace_id || data.workspace?.id,
        workspace: data,
        error: data.error?.message || data.error,
      };
    } catch (err: any) {
      return { success: false, error: err.message };
    }
  }

  public async getWorkspaceStatus(workspaceId: string): Promise<any> {
    try {
      const res = await fetch(`/api/repo/workspace/${encodeURIComponent(workspaceId)}/status`);
      return await res.json();
    } catch (err: any) {
      return { success: false, error: err.message };
    }
  }

  public async instructWorkspace(
    workspaceId: string,
    instruction: string
  ): Promise<{ success: boolean; result?: any; error?: string }> {
    try {
      const res = await fetch(`/api/repo/workspace/${encodeURIComponent(workspaceId)}/instruct`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ instruction }),
      });
      const data = await res.json();
      return { success: res.ok && data.success !== false, result: data, error: data.error?.message || data.error };
    } catch (err: any) {
      return { success: false, error: err.message };
    }
  }

  public async getWorkspaceDiff(workspaceId: string): Promise<any> {
    try {
      const res = await fetch(`/api/repo/workspace/${encodeURIComponent(workspaceId)}/diff`);
      return await res.json();
    } catch (err: any) {
      return { success: false, error: err.message };
    }
  }

  public async getWorkspacePublishReview(workspaceId: string): Promise<any> {
    try {
      const res = await fetch(`/api/repo/workspace/${encodeURIComponent(workspaceId)}/publish/review`);
      return await res.json();
    } catch (err: any) {
      return { success: false, error: err.message };
    }
  }

  public async commitWorkspace(
    workspaceId: string,
    commitMessage: string,
    files: string[] = []
  ): Promise<{ success: boolean; commit_hash?: string; error?: string }> {
    try {
      const res = await fetch(`/api/repo/workspace/${encodeURIComponent(workspaceId)}/commit`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ commit_message: commitMessage, files }),
      });
      const data = await res.json();
      return {
        success: res.ok && data.success !== false,
        commit_hash: data.commit_hash,
        error: data.error?.message || data.error,
      };
    } catch (err: any) {
      return { success: false, error: err.message };
    }
  }

  public async pushWorkspace(
    workspaceId: string,
    confirmed: boolean,
    branch: string = 'main'
  ): Promise<{ success: boolean; error?: string; message?: string }> {
    try {
      const res = await fetch(`/api/repo/workspace/${encodeURIComponent(workspaceId)}/push`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ confirmed, branch }),
      });
      const data = await res.json();
      return {
        success: res.ok && data.success !== false,
        message: data.message,
        error: data.error?.message || data.error,
      };
    } catch (err: any) {
      return { success: false, error: err.message };
    }
  }
}

export const bridgeService = new BridgeService();
