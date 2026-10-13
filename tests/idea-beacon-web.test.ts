import { describe, it, expect } from 'vitest';
import request from 'supertest';
import app from '../src/api/server.js';
import {
  mapPythonToReactIdea,
  mapReactToPythonCapture,
  isSafeUrl
} from '../src/api/ideaBeaconWeb.js';

describe('Idea Beacon Web UI Adapter & Contract Tests', () => {
  describe('Idea Model Field Mapping (React <-> Python)', () => {
    it('correctly maps Python IdeaRecord to React IdeaEntity', () => {
      const pythonRecord = {
        idea_id: 'idea-12345',
        title: 'Decentralized Cache',
        raw_prompt: 'Build an offline-first cache using SQLite and B-tree indexes.',
        created_at: '2026-10-14T00:00:00.000Z',
        updated_at: '2026-10-14T01:00:00.000Z',
        status: 'refined', // Python lifecycle status
        tech_stack: 'python',
        tags: ['#cache', '#sqlite'],
        linked_project_id: 'proj-42',
        blueprint: {
          summary: 'High performance caching layer',
          recommended_tech_stack: 'python'
        }
      };

      const mapped = mapPythonToReactIdea(pythonRecord, { category: 'systems' });

      expect(mapped.id).toBe('idea-12345');
      expect(mapped.title).toBe('Decentralized Cache');
      expect(mapped.content).toBe('Build an offline-first cache using SQLite and B-tree indexes.');
      expect(mapped.preview).toContain('Build an offline-first');
      expect(mapped.category).toBe('systems');
      expect(mapped.tags).toEqual(['#cache', '#sqlite']);
      expect(mapped.status).toBe('synced'); // UI sync status
      expect(mapped.lifecycleStatus).toBe('refined'); // Preserved lifecycle state!
      expect(mapped.tech_stack).toBe('python');
      expect(mapped.linked_project_id).toBe('proj-42');
      expect(mapped.createdAt).toBe('2026-10-14T00:00:00.000Z');
      expect(mapped.updatedAt).toBe('2026-10-14T01:00:00.000Z');
    });

    it('correctly maps React IdeaEntity to Python capture payload', () => {
      const reactEntity = {
        id: 'idea-react-99',
        title: 'Voice Compiler',
        category: 'devtools',
        content: 'Compile voice inputs directly to AST structures',
        tech_stack: 'rust',
        tags: ['#voice', '#compiler']
      };

      const pyPayload = mapReactToPythonCapture(reactEntity);

      expect(pyPayload.idea_id).toBe('idea-react-99');
      expect(pyPayload.title).toBe('Voice Compiler');
      expect(pyPayload.prompt).toBe('Compile voice inputs directly to AST structures');
      expect(pyPayload.tech_stack).toBe('rust');
    });

    it('handles fallback title generation when prompt is long and title missing', () => {
      const reactEntity = {
        content: 'A very detailed prompt without explicit title that exceeds thirty characters for testing'
      };

      const pyPayload = mapReactToPythonCapture(reactEntity);
      expect(pyPayload.prompt).toBe(reactEntity.content);
      expect(pyPayload.title).toBe('A very detailed prompt without...');
    });
  });

  describe('SSRF Protection Guardrails', () => {
    it('blocks AWS/GCP cloud metadata IP (169.254.169.254)', () => {
      const check = isSafeUrl('http://169.254.169.254/latest/meta-data/');
      expect(check.safe).toBe(false);
      expect(check.reason).toContain('cloud metadata');
    });

    it('blocks Google Cloud metadata hostnames', () => {
      const check = isSafeUrl('http://metadata.google.internal/computeMetadata/v1/');
      expect(check.safe).toBe(false);
      expect(check.reason).toContain('cloud metadata');
    });

    it('blocks dangerous or unauthorized schemes and ports', () => {
      expect(isSafeUrl('ftp://localhost:8000').safe).toBe(false);
      expect(isSafeUrl('file:///etc/passwd').safe).toBe(false);
      expect(isSafeUrl('http://localhost:22').safe).toBe(false); // SSH port
    });

    it('permits authorized bridge ports on loopback', () => {
      expect(isSafeUrl('http://127.0.0.1:8000').safe).toBe(true);
      expect(isSafeUrl('http://localhost:8000/health').safe).toBe(true);
      expect(isSafeUrl('http://127.0.0.1:11434').safe).toBe(true);
    });
  });

  describe('Bridge Status & Reachability Endpoint', () => {
    it('reports honest offline status when bridge is unreachable without falsely claiming online', async () => {
      const res = await request(app).get('/api/bridge/health');
      // If bridge is offline during test run, returns 503 and reachable: false
      // If bridge is running locally, returns 200 and reachable: true
      if (res.status === 503) {
        expect(res.body.reachable).toBe(false);
        expect(res.body.ollamaOnline).toBe(false);
        expect(res.body.error).toContain('Laptop bridge');
      } else {
        expect(res.status).toBe(200);
        expect(res.body.reachable).toBe(true);
        expect(res.body).toHaveProperty('ollamaOnline');
      }
    });

    it('validates test-connection targetUrl against SSRF attacks', async () => {
      const res = await request(app)
        .post('/api/bridge/test-connection')
        .send({ targetUrl: 'http://169.254.169.254/secret' })
        .expect(400);

      expect(res.body.reachable).toBe(false);
      expect(res.body.isSsrfBlocked).toBe(true);
      expect(res.body.error).toContain('Security violation');
    });

    it('rejects test-connection request missing targetUrl', async () => {
      const res = await request(app)
        .post('/api/bridge/test-connection')
        .send({})
        .expect(400);

      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('INVALID_ARGUMENT');
    });
  });

  describe('Idea Capture Validation Contract', () => {
    it('rejects capture request with empty content and prompt', async () => {
      const res = await request(app)
        .post('/api/ideas/capture')
        .send({ title: '', content: '' })
        .expect(400);

      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('INVALID_ARGUMENT');
      expect(res.body.error.message).toContain('required');
    });
  });

  describe('AI Brainstorming (Safe Inference Operation)', () => {
    it('rejects brainstorming request missing prompt', async () => {
      const res = await request(app)
        .post('/api/ai/brainstorm')
        .send({})
        .expect(400);

      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('INVALID_ARGUMENT');
    });

    it('executes safe technical architecture inference using local NPU engine', async () => {
      const res = await request(app)
        .post('/api/ai/brainstorm')
        .send({ prompt: 'Real-time telemetry event processor in TypeScript' })
        .expect(200);

      expect(res.body.success).toBe(true);
      expect(res.body.spec).toBeDefined();
      expect(res.body.spec.architectureFlow).toBeDefined();
      expect(res.body.spec.roadmap).toBeInstanceOf(Array);
      expect(res.body.model).toContain('qwen');
    });
  });

  describe('Controlled Workspace Operations & Approval Gates', () => {
    it('requires explicit Hold-to-Confirm gate approval before applying changes (HTTP 403 on unconfirmed)', async () => {
      const res = await request(app)
        .post('/api/repos/demo-repo/apply')
        .send({
          commitMessage: 'feat: apply changes without user confirmation',
          confirmed: false
        })
        .expect(403);

      expect(res.body.success).toBe(false);
      expect(res.body.requiresApproval).toBe(true);
      expect(res.body.error).toContain('Consequential action requires explicit Hold-to-Confirm gate approval');
    });

    it('blocks apply request missing confirmation flag altogether', async () => {
      const res = await request(app)
        .post('/api/repos/demo-repo/apply')
        .send({ commitMessage: 'unconfirmed commit' })
        .expect(403);

      expect(res.body.success).toBe(false);
      expect(res.body.requiresApproval).toBe(true);
    });

    it('handles workspace diff queries honestly without fabricating synthetic changes', async () => {
      const res = await request(app).get('/api/repos/workspace-local/diffs');
      // If bridge is offline during CI/test run, returns 503 BRIDGE_UNAVAILABLE
      // If bridge is running, returns 200 with repoId and diffs
      if (res.status === 503) {
        expect(res.body.error.code).toBe('BRIDGE_UNAVAILABLE');
      } else {
        expect(res.status).toBe(200);
        expect(res.body.success).toBe(true);
        expect(res.body.repoId).toBe('workspace-local');
        expect(Array.isArray(res.body.diffs)).toBe(true);
      }
    });

    it('requires explicit user confirmation before executing remote push (HTTP 403 on unconfirmed)', async () => {
      const res = await request(app)
        .post('/api/repo/workspace/ws-local/push')
        .send({
          branch: 'main',
          confirmed: false
        })
        .expect(403);

      expect(res.body.success).toBe(false);
      expect(res.body.requiresApproval).toBe(true);
      expect(res.body.error).toContain('explicit confirmation before pushing');
    });

    it('rejects remote push missing confirmation flag altogether', async () => {
      const res = await request(app)
        .post('/api/repo/workspace/ws-local/push')
        .send({ branch: 'main' })
        .expect(403);

      expect(res.body.success).toBe(false);
      expect(res.body.requiresApproval).toBe(true);
    });
  });

  describe('Batch Idempotent Idea Synchronization Contract', () => {
    it('handles batch sync requests honestly without throwing unhandled exceptions', async () => {
      const res = await request(app)
        .post('/api/ideas/sync')
        .send({
          mutations: [
            {
              id: 'idea-batch-1',
              title: 'Batch Spark',
              content: 'Content for batch synchronization',
              category: 'database'
            }
          ],
          tombstones: [{ id: 'idea-tombstone-old' }]
        });

      // When bridge is offline, returns 503 BRIDGE_UNAVAILABLE
      // When bridge is online, returns 200 with syncedIds
      if (res.status === 503) {
        expect(res.body.error.code).toBe('BRIDGE_UNAVAILABLE');
      } else {
        expect(res.status).toBe(200);
        expect(res.body.success).toBe(true);
        expect(Array.isArray(res.body.syncedIds)).toBe(true);
      }
    });
  });
});
