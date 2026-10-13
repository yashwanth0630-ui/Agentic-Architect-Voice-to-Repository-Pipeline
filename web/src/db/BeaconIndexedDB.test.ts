import { describe, it, expect, beforeEach } from 'vitest';
import { BeaconIndexedDB } from './BeaconIndexedDB';

describe('BeaconIndexedDB — Offline-First Persistence & Synchronization', () => {
  beforeEach(async () => {
    await BeaconIndexedDB.resetToDemoSeed();
  });

  it('captures new ideas locally with "unsynced" status by default', async () => {
    const saved = await BeaconIndexedDB.captureIdea({
      title: 'Distributed Log Compaction',
      category: 'database',
      preview: 'LSM tree compaction running on edge storage nodes.',
      content: 'Detailed LSM tree compaction specification with Bloom filters.',
      tags: ['#database', '#lsm'],
      isDemo: false,
    });

    expect(saved.id).toBeDefined();
    expect(saved.status).toBe('unsynced');
    expect(saved.isDemo).toBe(false);

    const fetched = BeaconIndexedDB.getById(saved.id);
    expect(fetched).toBeDefined();
    expect(fetched?.title).toBe('Distributed Log Compaction');
    expect(fetched?.status).toBe('unsynced');
  });

  it('updates ideas locally and preserves modified fields with "unsynced" status', async () => {
    const saved = await BeaconIndexedDB.captureIdea({
      title: 'Original Title',
      category: 'systems',
      preview: 'Original Preview',
      content: 'Original Content',
      tags: ['#systems'],
      isDemo: false,
    });

    const updated = await BeaconIndexedDB.updateIdea(saved.id, {
      title: 'Updated Title',
      content: 'Updated Content',
    });

    expect(updated).not.toBeNull();
    expect(updated?.title).toBe('Updated Title');
    expect(updated?.content).toBe('Updated Content');
    expect(updated?.status).toBe('unsynced');

    const inCache = BeaconIndexedDB.getById(saved.id);
    expect(inCache?.title).toBe('Updated Title');
  });

  it('records deletion tombstones when ideas are deleted', async () => {
    const saved = await BeaconIndexedDB.captureIdea({
      title: 'Transient Prototype',
      category: 'wasm',
      preview: 'Prototype',
      content: 'WASM JIT prototype',
      tags: ['#wasm'],
      isDemo: false,
    });

    const deleted = await BeaconIndexedDB.deleteIdea(saved.id);
    expect(deleted).toBe(true);
    expect(BeaconIndexedDB.getById(saved.id)).toBeUndefined();

    const tombstones = await BeaconIndexedDB.getTombstones();
    const hasTombstone = tombstones.some((t) => t.id === saved.id);
    expect(hasTombstone).toBe(true);
  });

  it('marks idea as synced when remote server confirms synchronization', async () => {
    const saved = await BeaconIndexedDB.captureIdea({
      title: 'Queue Worker',
      category: 'infra',
      preview: 'Queue Worker',
      content: 'Queue Worker',
      tags: ['#infra'],
      isDemo: false,
    });

    expect(saved.status).toBe('unsynced');

    await BeaconIndexedDB.markSynced(saved.id, {
      updatedAt: '2026-10-14T01:00:00.000Z',
    });

    const synced = BeaconIndexedDB.getById(saved.id);
    expect(synced?.status).toBe('synced');
    expect(synced?.syncErrorMessage).toBeUndefined();
  });

  it('filters and searches ideas across scopes (all, title, tag, content)', () => {
    BeaconIndexedDB.captureIdeaSync({
      title: 'Searchable Rust Microkernel',
      category: 'systems',
      preview: 'Microkernel preview',
      content: 'Capability-based addressing and IPC semantics',
      tags: ['#kernel', '#rust'],
      isDemo: false,
    });

    const allMatches = BeaconIndexedDB.search('Microkernel', { scope: 'all' });
    expect(allMatches.length).toBeGreaterThan(0);
    expect(allMatches.some((i) => i.title.includes('Microkernel'))).toBe(true);

    const tagMatches = BeaconIndexedDB.search('kernel', { scope: 'tag' });
    expect(tagMatches.some((i) => i.tags.includes('#kernel'))).toBe(true);

    const contentMatches = BeaconIndexedDB.search('Capability-based', { scope: 'content' });
    expect(contentMatches.length).toBeGreaterThan(0);
  });

  it('resolves all offline error states back to unsynced for retry', async () => {
    const errorIdea = await BeaconIndexedDB.captureIdea({
      title: 'Failing Idea',
      category: 'runtime',
      preview: 'Failing',
      content: 'Failing',
      tags: ['#runtime'],
      isDemo: false,
      status: 'error',
    });

    expect(BeaconIndexedDB.getById(errorIdea.id)?.status).toBe('error');

    await BeaconIndexedDB.resolveAllErrors();

    const resolved = BeaconIndexedDB.getById(errorIdea.id);
    expect(resolved?.status).toBe('unsynced');
  });

  it('prevents duplicate idea creation and preserves stable ID when updating an existing idea', async () => {
    const original = await BeaconIndexedDB.captureIdea({
      title: 'Stable ID Prototype',
      category: 'architecture',
      preview: 'Preview',
      content: 'Initial architecture hypothesis',
      tags: ['#architecture'],
      isDemo: false,
    });

    const countBefore = BeaconIndexedDB.getAll().length;

    // Editing via updateIdea
    const updated = await BeaconIndexedDB.updateIdea(original.id, {
      title: 'Updated Architecture Prototype',
      content: 'Revised architecture hypothesis with eBPF probes',
    });

    expect(updated?.id).toBe(original.id);
    expect(updated?.title).toBe('Updated Architecture Prototype');
    expect(BeaconIndexedDB.getAll().length).toBe(countBefore);

    // Editing via captureIdea with the same ID should also update in place without creating duplicate
    const reCaptured = await BeaconIndexedDB.captureIdea({
      id: original.id,
      title: 'Re-saved Architecture Prototype',
      category: 'architecture',
      preview: 'Updated preview',
      content: 'Second revision',
      tags: ['#architecture'],
      isDemo: false,
    });

    expect(reCaptured.id).toBe(original.id);
    expect(reCaptured.title).toBe('Re-saved Architecture Prototype');
    expect(BeaconIndexedDB.getAll().length).toBe(countBefore);
  });
});
