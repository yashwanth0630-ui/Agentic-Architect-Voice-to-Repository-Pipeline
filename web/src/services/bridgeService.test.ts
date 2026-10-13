import { describe, it, expect, vi, beforeEach } from 'vitest';
import { bridgeService } from './bridgeService';
import { BeaconIndexedDB } from '../db/BeaconIndexedDB';

describe('BridgeService — Real Bridge Workflows & Offline Safety', () => {
  beforeEach(async () => {
    await BeaconIndexedDB.resetToDemoSeed();
    vi.restoreAllMocks();
  });

  it('reports honest disconnected status when laptop bridge is offline', async () => {
    // Mock global fetch to simulate connection refused
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Failed to fetch')));

    const diag = await bridgeService.checkConnection('http://127.0.0.1:8000');
    expect(diag.status).toBe('DISCONNECTED');
    expect(diag.message).toContain('Unreachable');
    expect(diag.isLocalhost).toBe(true);
  });

  it('guarantees offline-first capture precedence: stores idea locally in IndexedDB even if network throws', async () => {
    // Network completely fails
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Network error')));

    const result = await bridgeService.captureIdea({
      title: 'Offline Resilient Idea',
      category: 'systems',
      preview: 'Stored locally first',
      content: 'Detailed offline resilient idea description',
      tags: ['#offline', '#systems'],
      isDemo: false,
    });

    // Successfully returned local entity
    expect(result.idea).toBeDefined();
    expect(result.viaBridge).toBe(false);
    expect(result.message).toContain('IndexedDB');

    // Confirmed present in BeaconIndexedDB with 'unsynced' status
    const inStore = BeaconIndexedDB.getById(result.idea.id);
    expect(inStore).toBeDefined();
    expect(inStore?.status).toBe('unsynced');
    expect(inStore?.title).toBe('Offline Resilient Idea');
  });

  it('marks idea as synced in IndexedDB only when network response is ok', async () => {
    const serverIdea = {
      idea_id: 'idea-remote-77',
      title: 'Online Synced Idea',
      category: 'database',
      preview: 'Preview',
      raw_prompt: 'Full prompt content',
      tags: ['#database'],
      status: 'captured',
      tech_stack: 'python',
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    };

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 201,
        json: async () => ({
          success: true,
          idea: {
            id: 'idea-remote-77',
            title: 'Online Synced Idea',
            category: 'database',
            preview: 'Preview',
            content: 'Full prompt content',
            tags: ['#database'],
            status: 'synced',
            updatedAt: new Date().toISOString(),
          },
        }),
      })
    );

    // Set connected status on bridgeService
    await bridgeService.setBridgeUrl('/api');

    const result = await bridgeService.captureIdea({
      id: 'idea-remote-77',
      title: 'Online Synced Idea',
      category: 'database',
      preview: 'Preview',
      content: 'Full prompt content',
      tags: ['#database'],
      isDemo: false,
    });

    expect(result.viaBridge).toBe(true);
    expect(result.idea.status).toBe('synced');
  });

  it('blocks apply commit request when Hold-to-Confirm approval is not confirmed', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 403,
        json: async () => ({
          success: false,
          error: 'Consequential action requires explicit Hold-to-Confirm gate approval',
          requiresApproval: true,
        }),
      })
    );

    const result = await bridgeService.applyRepositoryChanges('ws-hyperstream', false, 'unconfirmed commit');
    expect(result.success).toBe(false);
    expect(result.requiresApproval).toBe(true);
    expect(result.error).toContain('Hold-to-Confirm');
  });

  it('passes selected files array and explicit confirmation to commit and push operations', async () => {
    const fetchSpy = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        success: true,
        commit_hash: 'c0ffee42',
        message: 'Committed successfully',
      }),
    });
    vi.stubGlobal('fetch', fetchSpy);

    const res = await bridgeService.applyRepositoryChanges(
      'ws-hyperstream',
      true,
      'feat: add probe hook',
      ['src/main.rs', 'Cargo.toml']
    );

    expect(res.success).toBe(true);
    expect(fetchSpy).toHaveBeenCalled();
    const callBody = JSON.parse(fetchSpy.mock.calls[0][1].body);
    expect(callBody.confirmed).toBe(true);
    expect(callBody.selected_files).toEqual(['src/main.rs', 'Cargo.toml']);
  });

  it('guarantees offline-first updateIdea: updates locally and preserves stable ID when network throws', async () => {
    // 1. Seed an initial idea locally
    const original = await BeaconIndexedDB.captureIdea({
      title: 'Initial Idea to Edit',
      category: 'systems',
      preview: 'Original preview',
      content: 'Original content',
      tags: ['#systems'],
      isDemo: false,
    });

    const initialTotal = BeaconIndexedDB.getAll().length;

    // Simulate network error on PUT
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Network offline')));

    const updateResult = await bridgeService.updateIdea(original.id, {
      title: 'Updated Idea Title',
      content: 'Updated idea content with new architecture',
    });

    expect(updateResult.idea).not.toBeNull();
    expect(updateResult.idea?.id).toBe(original.id);
    expect(updateResult.idea?.title).toBe('Updated Idea Title');
    expect(updateResult.viaBridge).toBe(false);

    // Verify local cache updated without creating a duplicate record
    const stored = BeaconIndexedDB.getById(original.id);
    expect(stored).toBeDefined();
    expect(stored?.title).toBe('Updated Idea Title');
    expect(stored?.content).toBe('Updated idea content with new architecture');
    expect(BeaconIndexedDB.getAll().length).toBe(initialTotal);
  });

  it('marks updated idea as synced only when network PUT confirms success', async () => {
    const original = await BeaconIndexedDB.captureIdea({
      title: 'Idea For Remote Sync',
      category: 'infra',
      preview: 'Infra preview',
      content: 'Infra content',
      tags: ['#infra'],
      isDemo: false,
    });

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({
          success: true,
          idea: {
            id: original.id,
            title: 'Idea For Remote Sync (Synced)',
            category: 'infra',
            content: 'Infra content updated',
            status: 'synced',
            updatedAt: new Date().toISOString(),
          },
        }),
      })
    );

    await bridgeService.setBridgeUrl('/api');

    const result = await bridgeService.updateIdea(original.id, {
      title: 'Idea For Remote Sync (Synced)',
      content: 'Infra content updated',
    });

    expect(result.viaBridge).toBe(true);
    expect(result.idea?.status).toBe('synced');
    expect(BeaconIndexedDB.getById(original.id)?.status).toBe('synced');
  });
});
