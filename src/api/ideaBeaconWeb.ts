import { Router, Request, Response } from 'express';
import { buildError } from '../lib/response.js';
import { defaultNpuEngine } from '../core/npu.js';

export const ideaBeaconWebRouter = Router();

const BRIDGE_URL = process.env.BRIDGE_URL || 'http://127.0.0.1:8000';
const OLLAMA_BASE_URL = process.env.OLLAMA_BASE_URL || 'http://127.0.0.1:11434';

// SSRF Protection Helper
export function isSafeUrl(urlStr: string): { safe: boolean; reason?: string } {
  try {
    const parsed = new URL(urlStr);
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      return { safe: false, reason: 'Protocol must be http: or https:' };
    }

    const hostname = parsed.hostname.toLowerCase();
    if (
      hostname === '169.254.169.254' ||
      hostname.includes('metadata.google.internal') ||
      hostname.includes('169.254') ||
      hostname.includes('instance-data')
    ) {
      return { safe: false, reason: 'Access to cloud metadata endpoints is prohibited (SSRF prevention)' };
    }

    const port = parsed.port;
    if (port && !['80', '443', '8000', '8080', '11434', '3000', '5000', '5173'].includes(port)) {
      return { safe: false, reason: `Port ${port} is not permitted for proxying` };
    }

    const isCloudHost = process.env.NODE_ENV === 'production' && !process.env.ALLOW_LOCAL_BRIDGE_PROXY;
    if (isCloudHost) {
      if (
        hostname.startsWith('10.') ||
        hostname.startsWith('192.168.') ||
        /^172\.(1[6-9]|2\d|3[0-1])\./.test(hostname)
      ) {
        return { safe: false, reason: 'Arbitrary private internal subnet addresses are blocked on cloud instances' };
      }
    }

    return { safe: true };
  } catch {
    return { safe: false, reason: 'Malformed or invalid URL string' };
  }
}

// Safe param extraction and encoding helper for Express route parameters
export function getParam(param: string | string[] | undefined): string {
  if (Array.isArray(param)) return param[0] || '';
  return param || '';
}

export function encodeParam(param: string | string[] | undefined | null): string {
  return encodeURIComponent(getParam(param as string | string[] | undefined));
}

// React <-> Python Idea mapping
export interface ReactIdea {
  id: string;
  title: string;
  category: string;
  preview: string;
  content: string;
  tags: string[];
  status: 'synced' | 'unsynced' | 'syncing' | 'error';
  lifecycleStatus?: string;
  tech_stack?: string;
  blueprint?: any;
  linked_project_id?: string | null;
  isDemo?: boolean;
  createdAt: string;
  updatedAt: string;
}

export function mapPythonToReactIdea(pyIdea: any, existingReactIdea?: Partial<ReactIdea>): ReactIdea {
  return {
    id: pyIdea.idea_id,
    title: pyIdea.title || 'Untitled Idea',
    category: existingReactIdea?.category || 'systems',
    preview: pyIdea.raw_prompt ? pyIdea.raw_prompt.slice(0, 120) : '',
    content: pyIdea.raw_prompt || '',
    tags: Array.isArray(pyIdea.tags) ? pyIdea.tags : [],
    status: 'synced',
    lifecycleStatus: pyIdea.status || 'captured',
    tech_stack: pyIdea.tech_stack || 'auto',
    blueprint: pyIdea.blueprint || null,
    linked_project_id: pyIdea.linked_project_id || null,
    isDemo: false,
    createdAt: pyIdea.created_at || new Date().toISOString(),
    updatedAt: pyIdea.updated_at || new Date().toISOString()
  };
}

export function mapReactToPythonCapture(reactIdea: Partial<ReactIdea>) {
  const prompt = (reactIdea.content || (reactIdea as any).prompt || reactIdea.title || '').trim();
  const title = (reactIdea.title || (prompt.length > 30 ? `${prompt.slice(0, 30)}...` : prompt) || 'Untitled Idea').trim();
  const idea_id = reactIdea.id || undefined;
  const tech_stack = reactIdea.tech_stack || undefined;

  return {
    prompt,
    title,
    tech_stack,
    idea_id
  };
}

// -------------------------------------------------------------
// 1. Bridge Status & Connectivity Endpoints
// Honest connection testing. Never reports Node health as Python health.
// -------------------------------------------------------------

ideaBeaconWebRouter.get('/bridge/health', async (_req: Request, res: Response) => {
  const startTime = Date.now();
  const targetUrl = `${BRIDGE_URL.replace(/\/$/, '')}/health`;

  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 3500);

    const response = await fetch(targetUrl, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      signal: controller.signal
    });
    clearTimeout(timeout);

    const latencyMs = Date.now() - startTime;
    if (response.ok) {
      const data = await response.json();
      return res.json({
        status: 'ok',
        service: 'idea-beacon-bridge',
        version: data.version || '2.2.0',
        reachable: true,
        latencyMs,
        bridgeUrl: BRIDGE_URL,
        bridge: data,
        ollamaOnline: data.ollama?.online ?? false,
        workspaceWritable: data.workspace?.writable ?? false,
        timestamp: new Date().toISOString()
      });
    }

    return res.status(response.status).json({
      status: 'error',
      reachable: false,
      latencyMs,
      bridgeUrl: BRIDGE_URL,
      error: `Bridge returned HTTP ${response.status}`,
      ollamaOnline: false,
      workspaceWritable: false,
      timestamp: new Date().toISOString()
    });
  } catch (err: any) {
    const latencyMs = Date.now() - startTime;
    return res.status(503).json({
      status: 'offline',
      reachable: false,
      latencyMs,
      bridgeUrl: BRIDGE_URL,
      error: `Laptop bridge unreachable at ${BRIDGE_URL}: ${err.message}`,
      reason: err.name === 'AbortError' ? 'TIMEOUT' : 'CONNECTION_REFUSED',
      ollamaOnline: false,
      workspaceWritable: false,
      timestamp: new Date().toISOString()
    });
  }
});

ideaBeaconWebRouter.post('/bridge/test-connection', async (req: Request, res: Response) => {
  const { targetUrl, timeoutMs = 3500 } = req.body ?? {};

  if (!targetUrl || typeof targetUrl !== 'string') {
    return res.status(400).json(
      buildError('INVALID_ARGUMENT', 'Field targetUrl is required and must be a string')
    );
  }

  const safetyCheck = isSafeUrl(targetUrl);
  if (!safetyCheck.safe) {
    return res.status(400).json({
      reachable: false,
      error: `Security violation: ${safetyCheck.reason}`,
      isSsrfBlocked: true
    });
  }

  const startTime = Date.now();
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), Math.min(timeoutMs, 10000));

  try {
    const urlToTest = targetUrl.endsWith('/') ? `${targetUrl}health` : `${targetUrl}/health`;
    const response = await fetch(urlToTest, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      signal: controller.signal
    });
    clearTimeout(timeoutId);

    const latencyMs = Date.now() - startTime;
    return res.json({
      reachable: response.ok,
      statusCode: response.status,
      latencyMs,
      targetUrl,
      note: response.ok ? 'Connection established with bridge' : `HTTP ${response.status}`
    });
  } catch (err: any) {
    clearTimeout(timeoutId);
    const latencyMs = Date.now() - startTime;
    const isTimeout = err.name === 'AbortError';

    return res.json({
      reachable: false,
      latencyMs,
      targetUrl,
      error: isTimeout ? `Connection timed out after ${timeoutMs}ms` : err.message,
      reason: isTimeout ? 'TIMEOUT' : 'NETWORK_ERROR',
      note: 'Ensure laptop bridge is running and reachable from current network'
    });
  }
});

// -------------------------------------------------------------
// 2. Web Idea Vault Endpoints (Full translation to Python bridge)
// -------------------------------------------------------------

ideaBeaconWebRouter.post('/ideas/capture', async (req: Request, res: Response) => {
  try {
    const pyPayload = mapReactToPythonCapture(req.body);

    if (!pyPayload.prompt) {
      return res.status(400).json(
        buildError('INVALID_ARGUMENT', 'Idea content or prompt is required and cannot be empty')
      );
    }

    const bridgeEndpoint = `${BRIDGE_URL.replace(/\/$/, '')}/api/ideas/capture`;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 6000);

    const bridgeRes = await fetch(bridgeEndpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(pyPayload),
      signal: controller.signal
    });
    clearTimeout(timeout);

    if (!bridgeRes.ok) {
      const errData = await bridgeRes.json().catch(() => ({}));
      return res.status(bridgeRes.status).json(
        buildError('BRIDGE_ERROR', errData.detail || errData.error?.message || 'Failed to capture idea in bridge')
      );
    }

    const data = await bridgeRes.json();
    const mapped = mapPythonToReactIdea(data.idea, req.body);

    return res.status(201).json({
      success: true,
      idea: mapped,
      message: 'Idea captured successfully in vault'
    });
  } catch (err: any) {
    return res.status(503).json(
      buildError('BRIDGE_UNAVAILABLE', `Laptop bridge unavailable at ${BRIDGE_URL}: ${err.message}`)
    );
  }
});

ideaBeaconWebRouter.get('/ideas', async (req: Request, res: Response) => {
  try {
    const bridgeEndpoint = `${BRIDGE_URL.replace(/\/$/, '')}/api/ideas`;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 6000);

    const bridgeRes = await fetch(bridgeEndpoint, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      signal: controller.signal
    });
    clearTimeout(timeout);

    if (!bridgeRes.ok) {
      return res.status(bridgeRes.status).json(
        buildError('BRIDGE_ERROR', `Bridge returned HTTP ${bridgeRes.status}`)
      );
    }

    const data = await bridgeRes.json();
    const rawIdeas: any[] = data.ideas || [];
    let mappedList = rawIdeas.map((i) => mapPythonToReactIdea(i));

    const { category, search, limit } = req.query;
    if (category && typeof category === 'string' && category !== 'all') {
      mappedList = mappedList.filter((i) => i.category.toLowerCase() === category.toLowerCase());
    }

    if (search && typeof search === 'string') {
      const q = search.trim().toLowerCase();
      mappedList = mappedList.filter(
        (i) =>
          i.title.toLowerCase().includes(q) ||
          i.content.toLowerCase().includes(q) ||
          i.tags.some((t) => t.toLowerCase().includes(q))
      );
    }

    if (limit && !isNaN(Number(limit))) {
      mappedList = mappedList.slice(0, Number(limit));
    }

    return res.json({
      success: true,
      ideas: mappedList,
      count: mappedList.length
    });
  } catch (err: any) {
    return res.status(503).json(
      buildError('BRIDGE_UNAVAILABLE', `Laptop bridge unavailable at ${BRIDGE_URL}: ${err.message}`)
    );
  }
});

ideaBeaconWebRouter.get('/ideas/:id', async (req: Request, res: Response) => {
  try {
    const id = getParam(req.params.id);
    const bridgeEndpoint = `${BRIDGE_URL.replace(/\/$/, '')}/api/ideas/${encodeParam(id)}`;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 6000);

    const bridgeRes = await fetch(bridgeEndpoint, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      signal: controller.signal
    });
    clearTimeout(timeout);

    if (bridgeRes.status === 404) {
      return res.status(404).json(
        buildError('RESOURCE_NOT_FOUND', `Idea '${id}' not found in vault`)
      );
    }

    if (!bridgeRes.ok) {
      return res.status(bridgeRes.status).json(
        buildError('BRIDGE_ERROR', `Bridge returned HTTP ${bridgeRes.status}`)
      );
    }

    const data = await bridgeRes.json();
    return res.json({
      success: true,
      idea: mapPythonToReactIdea(data.idea)
    });
  } catch (err: any) {
    return res.status(503).json(
      buildError('BRIDGE_UNAVAILABLE', `Laptop bridge unavailable at ${BRIDGE_URL}: ${err.message}`)
    );
  }
});

ideaBeaconWebRouter.put('/ideas/:id', async (req: Request, res: Response) => {
  try {
    const id = getParam(req.params.id);
    const { title, content, prompt, tech_stack } = req.body ?? {};

    const bridgeEndpoint = `${BRIDGE_URL.replace(/\/$/, '')}/api/ideas/${encodeParam(id)}`;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 6000);

    const bridgeRes = await fetch(bridgeEndpoint, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title,
        prompt: prompt || content,
        tech_stack
      }),
      signal: controller.signal
    });
    clearTimeout(timeout);

    if (bridgeRes.status === 404) {
      return res.status(404).json(
        buildError('RESOURCE_NOT_FOUND', `Idea '${id}' not found in vault`)
      );
    }

    if (!bridgeRes.ok) {
      return res.status(bridgeRes.status).json(
        buildError('BRIDGE_ERROR', `Bridge returned HTTP ${bridgeRes.status}`)
      );
    }

    const data = await bridgeRes.json();
    return res.json({
      success: true,
      idea: mapPythonToReactIdea(data.idea, req.body),
      message: 'Idea updated successfully'
    });
  } catch (err: any) {
    return res.status(503).json(
      buildError('BRIDGE_UNAVAILABLE', `Laptop bridge unavailable at ${BRIDGE_URL}: ${err.message}`)
    );
  }
});

ideaBeaconWebRouter.delete('/ideas/:id', async (req: Request, res: Response) => {
  try {
    const id = getParam(req.params.id);
    const bridgeEndpoint = `${BRIDGE_URL.replace(/\/$/, '')}/api/ideas/${encodeParam(id)}`;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 6000);

    const bridgeRes = await fetch(bridgeEndpoint, {
      method: 'DELETE',
      headers: { Accept: 'application/json' },
      signal: controller.signal
    });
    clearTimeout(timeout);

    if (bridgeRes.status === 404) {
      return res.status(404).json(
        buildError('RESOURCE_NOT_FOUND', `Idea '${id}' not found in vault`)
      );
    }

    if (!bridgeRes.ok) {
      return res.status(bridgeRes.status).json(
        buildError('BRIDGE_ERROR', `Bridge returned HTTP ${bridgeRes.status}`)
      );
    }

    return res.json({
      success: true,
      id,
      message: 'Idea removed from vault'
    });
  } catch (err: any) {
    return res.status(503).json(
      buildError('BRIDGE_UNAVAILABLE', `Laptop bridge unavailable at ${BRIDGE_URL}: ${err.message}`)
    );
  }
});

// Batch Idempotent Synchronization Endpoint for IndexedDB
ideaBeaconWebRouter.post('/ideas/sync', async (req: Request, res: Response) => {
  try {
    const { mutations = [], tombstones = [] } = req.body ?? {};
    const syncedIds: string[] = [];
    const tombstoneIds: string[] = [];

    // Process client tombstones
    if (Array.isArray(tombstones)) {
      for (const t of tombstones) {
        if (!t || !t.id) continue;
        try {
          const delRes = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/api/ideas/${encodeURIComponent(t.id)}`, {
            method: 'DELETE'
          });
          if (delRes.ok || delRes.status === 404) {
            tombstoneIds.push(t.id);
          }
        } catch {
          // If bridge is unreachable for this item, stop syncing
          break;
        }
      }
    }

    // Process client mutations
    if (Array.isArray(mutations)) {
      for (const item of mutations) {
        if (!item || !item.id) continue;
        try {
          // Attempt capture or update
          const pyPayload = mapReactToPythonCapture(item);
          const putRes = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/api/ideas/${encodeURIComponent(item.id)}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              title: pyPayload.title,
              prompt: pyPayload.prompt,
              tech_stack: pyPayload.tech_stack
            })
          });

          if (putRes.ok) {
            syncedIds.push(item.id);
          } else if (putRes.status === 404) {
            // Not found, capture it
            const capRes = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/api/ideas/capture`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(pyPayload)
            });
            if (capRes.ok) {
              syncedIds.push(item.id);
            }
          }
        } catch {
          break;
        }
      }
    }

    // Retrieve fresh server state
    const listRes = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/api/ideas`, {
      method: 'GET'
    });

    let serverIdeas: ReactIdea[] = [];
    if (listRes.ok) {
      const listData = await listRes.json();
      serverIdeas = (listData.ideas || []).map((i: any) => mapPythonToReactIdea(i));
    }

    return res.json({
      success: true,
      syncedIds,
      tombstoneIds,
      serverIdeas,
      timestamp: new Date().toISOString()
    });
  } catch (err: any) {
    return res.status(503).json(
      buildError('BRIDGE_UNAVAILABLE', `Sync failed due to bridge unavailability: ${err.message}`)
    );
  }
});

// -------------------------------------------------------------
// 3. Safe AI Brainstorming Endpoint
// Never invokes code generation or project writing as side-effects.
// -------------------------------------------------------------

ideaBeaconWebRouter.post('/ai/brainstorm', async (req: Request, res: Response) => {
  try {
    const { prompt, model = 'qwen2.5-coder' } = req.body ?? {};

    if (!prompt || typeof prompt !== 'string' || !prompt.trim()) {
      return res.status(400).json(
        buildError('INVALID_ARGUMENT', 'Prompt is required and must be non-empty')
      );
    }

    // 1. Try Snapdragon NPU engine first if configured or requested
    try {
      const npuResult = await defaultNpuEngine.inferProjectConfig(prompt);
      const spec = {
        architectureFlow: `${npuResult.config.projectName} -> ${npuResult.config.backendFramework} -> ${npuResult.config.frontendFramework}`,
        recommendedLibraries: [
          { name: npuResult.config.frontendFramework, version: 'latest', reason: 'Frontend presentation layer' },
          { name: npuResult.config.backendFramework, version: 'latest', reason: 'API and core runtime' },
          { name: npuResult.config.dataLayer, version: 'latest', reason: 'Data layer & validation' },
          { name: npuResult.config.stylingEngine, version: 'latest', reason: 'Styling engine' }
        ],
        requirements: [
          { label: 'Runtime Environment', value: npuResult.config.runtime },
          { label: 'Architecture', value: npuResult.config.architecture }
        ],
        roadmap: [
          'Phase 1: Domain Entities & Invariants',
          'Phase 2: API Gateway & Contracts',
          'Phase 3: Integration & Telemetry'
        ],
        summary: npuResult.config.description
      };

      return res.json({
        success: true,
        model: 'snapdragon-npu-qwen-2.5-coder',
        rawText: JSON.stringify(spec, null, 2),
        spec,
        sources: [],
        webSearchQueries: []
      });
    } catch {
      // Fallback: Check local Ollama without code-injection side-effects
      const ollamaEndpoint = `${OLLAMA_BASE_URL.replace(/\/$/, '')}/api/generate`;
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 8000);

      const ollamaRes = await fetch(ollamaEndpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          model: model || 'qwen2.5-coder:1.5b',
          prompt: `Analyze the following developer idea and provide technical architecture recommendations: ${prompt}`,
          stream: false
        }),
        signal: controller.signal
      });
      clearTimeout(timeout);

      if (ollamaRes.ok) {
        const oData = await ollamaRes.json();
        return res.json({
          success: true,
          model: model || 'qwen2.5-coder:1.5b',
          rawText: oData.response || '',
          spec: null,
          sources: [],
          webSearchQueries: []
        });
      }

      return res.status(503).json(
        buildError('AI_UNAVAILABLE', 'Local AI providers (NPU / Ollama) currently unreachable for brainstorming.')
      );
    }
  } catch (err: any) {
    return res.status(500).json(
      buildError('INTERNAL_ERROR', err.message || 'Error executing brainstorm operation')
    );
  }
});

// -------------------------------------------------------------
// 4. Safe Ollama proxy endpoint for BridgeStatusModal
// -------------------------------------------------------------

ideaBeaconWebRouter.post('/bridge/ollama', async (req: Request, res: Response) => {
  const { bridgeUrl = BRIDGE_URL, prompt, model = 'qwen2.5-coder:1.5b' } = req.body ?? {};

  if (!prompt) {
    return res.status(400).json(buildError('INVALID_ARGUMENT', 'Field prompt is required'));
  }

  const safetyCheck = isSafeUrl(bridgeUrl);
  if (!safetyCheck.safe) {
    return res.status(400).json(buildError('SECURITY_VIOLATION', safetyCheck.reason || 'Invalid URL'));
  }

  try {
    const ollamaUrl = `${OLLAMA_BASE_URL.replace(/\/$/, '')}/api/generate`;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 8000);

    const response = await fetch(ollamaUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ model, prompt, stream: false }),
      signal: controller.signal
    });
    clearTimeout(timeout);

    if (!response.ok) {
      throw new Error(`Ollama returned status ${response.status}`);
    }

    const data = await response.json();
    return res.json({
      success: true,
      text: data.response || data.text || '',
      model,
      source: 'ollama'
    });
  } catch (err: any) {
    return res.status(503).json({
      success: false,
      error: `Ollama service unreachable: ${err.message}`,
      fallbackRecommended: true
    });
  }
});

// -------------------------------------------------------------
// 5. GitHub & Repository Proxy Endpoints to Python Bridge
// Connects UI directly to real Python bridge operations!
// -------------------------------------------------------------

ideaBeaconWebRouter.get('/github/status', async (_req: Request, res: Response) => {
  try {
    const r = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/github/status`);
    const data = await r.json();
    return res.status(r.status).json(data);
  } catch (err: any) {
    return res.status(503).json(buildError('BRIDGE_UNAVAILABLE', err.message));
  }
});

ideaBeaconWebRouter.get('/github/repos', async (_req: Request, res: Response) => {
  try {
    const r = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/github/repos`);
    const data = await r.json();
    return res.status(r.status).json(data);
  } catch (err: any) {
    return res.status(503).json(buildError('BRIDGE_UNAVAILABLE', err.message));
  }
});

ideaBeaconWebRouter.post('/repo/workspace/init', async (req: Request, res: Response) => {
  try {
    const r = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/repo/workspace/init`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req.body)
    });
    const data = await r.json();
    return res.status(r.status).json(data);
  } catch (err: any) {
    return res.status(503).json(buildError('BRIDGE_UNAVAILABLE', err.message));
  }
});

ideaBeaconWebRouter.get('/repo/workspace/:id/status', async (req: Request, res: Response) => {
  try {
    const id = getParam(req.params.id);
    const r = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/repo/workspace/${encodeParam(id)}/status`);
    const data = await r.json();
    return res.status(r.status).json(data);
  } catch (err: any) {
    return res.status(503).json(buildError('BRIDGE_UNAVAILABLE', err.message));
  }
});

ideaBeaconWebRouter.post('/repo/workspace/:id/instruct', async (req: Request, res: Response) => {
  try {
    const id = getParam(req.params.id);
    const r = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/repo/workspace/${encodeParam(id)}/instruct`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req.body)
    });
    const data = await r.json();
    return res.status(r.status).json(data);
  } catch (err: any) {
    return res.status(503).json(buildError('BRIDGE_UNAVAILABLE', err.message));
  }
});

ideaBeaconWebRouter.get('/repo/workspace/:id/diff', async (req: Request, res: Response) => {
  try {
    const id = getParam(req.params.id);
    const r = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/repo/workspace/${encodeParam(id)}/diff`);
    const data = await r.json();
    return res.status(r.status).json(data);
  } catch (err: any) {
    return res.status(503).json(buildError('BRIDGE_UNAVAILABLE', err.message));
  }
});

ideaBeaconWebRouter.get('/repo/workspace/:id/publish/review', async (req: Request, res: Response) => {
  try {
    const id = getParam(req.params.id);
    const r = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/repo/workspace/${encodeParam(id)}/publish/review`);
    const data = await r.json();
    return res.status(r.status).json(data);
  } catch (err: any) {
    return res.status(503).json(buildError('BRIDGE_UNAVAILABLE', err.message));
  }
});

ideaBeaconWebRouter.post('/repo/workspace/:id/commit', async (req: Request, res: Response) => {
  try {
    const id = getParam(req.params.id);
    let selectedFiles = req.body.selected_files || req.body.files || [];
    if (!Array.isArray(selectedFiles) || selectedFiles.length === 0) {
      try {
        const diffRes = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/repo/workspace/${encodeParam(id)}/diff`);
        if (diffRes.ok) {
          const diffData = await diffRes.json();
          const changedFiles = (diffData.changes?.files || []).map((f: any) => f.path || f.filename || f);
          if (changedFiles.length > 0) {
            selectedFiles = changedFiles;
          }
        }
      } catch {
        // Fallback
      }
    }

    const r = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/repo/workspace/${encodeParam(id)}/commit`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        commit_message: req.body.commit_message || req.body.commitMessage || 'feat: apply workspace changes',
        selected_files: selectedFiles,
        author_name: req.body.author_name,
        author_email: req.body.author_email
      })
    });
    const data = await r.json();
    return res.status(r.status).json(data);
  } catch (err: any) {
    return res.status(503).json(buildError('BRIDGE_UNAVAILABLE', err.message));
  }
});

ideaBeaconWebRouter.post('/repo/workspace/:id/push', async (req: Request, res: Response) => {
  try {
    const id = getParam(req.params.id);
    const { confirmed, branch = 'main', remote_name = 'origin' } = req.body ?? {};
    if (confirmed !== true) {
      return res.status(403).json({
        success: false,
        error: 'Consequential action requires explicit confirmation before pushing to remote repository',
        requiresApproval: true
      });
    }

    const r = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/repo/workspace/${encodeParam(id)}/push`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        confirmed: true,
        branch,
        remote_name
      })
    });
    const data = await r.json();
    return res.status(r.status).json(data);
  } catch (err: any) {
    return res.status(503).json(buildError('BRIDGE_UNAVAILABLE', err.message));
  }
});

// Environment setup proxies
ideaBeaconWebRouter.get('/environment/detect', async (_req: Request, res: Response) => {
  try {
    const r = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/api/environment/detect`);
    const data = await r.json();
    return res.status(r.status).json(data);
  } catch (err: any) {
    return res.status(503).json(buildError('BRIDGE_UNAVAILABLE', err.message));
  }
});

ideaBeaconWebRouter.post('/environment/plan', async (req: Request, res: Response) => {
  try {
    const r = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/api/environment/plan`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req.body)
    });
    const data = await r.json();
    return res.status(r.status).json(data);
  } catch (err: any) {
    return res.status(503).json(buildError('BRIDGE_UNAVAILABLE', err.message));
  }
});

ideaBeaconWebRouter.post('/environment/execute-plan', async (req: Request, res: Response) => {
  try {
    const r = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/api/environment/execute-plan`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req.body)
    });
    const data = await r.json();
    return res.status(r.status).json(data);
  } catch (err: any) {
    return res.status(503).json(buildError('BRIDGE_UNAVAILABLE', err.message));
  }
});

ideaBeaconWebRouter.get('/environment/status/:id', async (req: Request, res: Response) => {
  try {
    const id = getParam(req.params.id);
    const r = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/api/environment/status/${encodeParam(id)}`);
    const data = await r.json();
    return res.status(r.status).json(data);
  } catch (err: any) {
    return res.status(503).json(buildError('BRIDGE_UNAVAILABLE', err.message));
  }
});

ideaBeaconWebRouter.post('/environment/cancel/:id', async (req: Request, res: Response) => {
  try {
    const id = getParam(req.params.id);
    const r = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/api/environment/cancel/${encodeParam(id)}`, {
      method: 'POST'
    });
    const data = await r.json();
    return res.status(r.status).json(data);
  } catch (err: any) {
    return res.status(503).json(buildError('BRIDGE_UNAVAILABLE', err.message));
  }
});

// -------------------------------------------------------------
// Compatibility Repository Endpoints mapping to Workspaces
// -------------------------------------------------------------

ideaBeaconWebRouter.get('/repos', async (_req: Request, res: Response) => {
  try {
    const ghRes = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/github/repos`).catch(() => null);
    const reposList: any[] = [];
    if (ghRes && ghRes.ok) {
      const data = await ghRes.json();
      if (Array.isArray(data.repos)) {
        data.repos.forEach((r: any) => {
          reposList.push({
            id: r.name || r.id,
            name: r.name,
            owner: r.owner?.login || 'github',
            url: r.html_url || r.url,
            branch: r.default_branch || 'main',
            status: 'connected',
            lastCommit: {
              hash: 'HEAD',
              message: 'Latest tracked commit',
              author: r.owner?.login || 'git',
              timestamp: 'active'
            },
            history: []
          });
        });
      }
    }

    if (reposList.length === 0) {
      reposList.push({
        id: 'workspace-local',
        name: 'Agentic Architect Workspace',
        owner: 'local',
        url: 'http://localhost:8000',
        branch: 'main',
        status: 'connected',
        lastCommit: {
          hash: 'HEAD',
          message: 'Working baseline commit',
          author: 'Developer',
          timestamp: 'now'
        },
        history: []
      });
    }

    return res.json({
      success: true,
      repos: reposList,
      count: reposList.length,
      timestamp: new Date().toISOString()
    });
  } catch (err: any) {
    return res.status(503).json(buildError('BRIDGE_UNAVAILABLE', err.message));
  }
});

ideaBeaconWebRouter.post('/repos/connect', async (req: Request, res: Response) => {
  try {
    const { name, url, branch = 'main' } = req.body ?? {};
    const initRes = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/repo/workspace/init`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        repo_url: url,
        branch,
        explicit_workspace_id: name
      })
    });
    const initData = await initRes.json();
    if (!initRes.ok) {
      return res.status(initRes.status).json(initData);
    }

    return res.status(201).json({
      success: true,
      repo: {
        id: initData.workspace_id || name,
        name: name || initData.repo_name || 'workspace',
        branch,
        status: 'connected',
        workspace_id: initData.workspace_id
      },
      message: 'Workspace connected and initialized successfully'
    });
  } catch (err: any) {
    return res.status(503).json(buildError('BRIDGE_UNAVAILABLE', err.message));
  }
});

ideaBeaconWebRouter.get('/repos/:id/diffs', async (req: Request, res: Response) => {
  try {
    const id = getParam(req.params.id);
    const diffRes = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/repo/workspace/${encodeParam(id)}/diff`);
    if (diffRes.ok) {
      const diffData = await diffRes.json();
      return res.json({
        success: true,
        repoId: id,
        diffs: diffData.changes?.files || [],
        proposal: {
          id: `prop-${id}`,
          repoId: id,
          title: diffData.latest_operation?.instruction || 'Workspace modifications',
          description: diffData.latest_operation?.explanation || 'Changes verified against baseline commit',
          diffs: diffData.changes?.files || [],
          status: 'pending_review',
          requiresApproval: true
        }
      });
    }
    return res.json({
      success: true,
      repoId: id,
      diffs: [],
      proposal: null
    });
  } catch (err: any) {
    return res.status(503).json(buildError('BRIDGE_UNAVAILABLE', err.message));
  }
});

ideaBeaconWebRouter.post('/repos/:id/propose-diff', async (req: Request, res: Response) => {
  try {
    const id = getParam(req.params.id);
    const { title, description } = req.body ?? {};
    const instruction = title || description;
    const instructRes = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/repo/workspace/${encodeParam(id)}/instruct`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ instruction })
    });
    const instructData = await instructRes.json();
    return res.status(instructRes.status).json(instructData);
  } catch (err: any) {
    return res.status(503).json(buildError('BRIDGE_UNAVAILABLE', err.message));
  }
});

ideaBeaconWebRouter.post('/repos/:id/apply', async (req: Request, res: Response) => {
  try {
    const id = getParam(req.params.id);
    const { confirmed, commitMessage, files = [] } = req.body ?? {};
    if (confirmed !== true) {
      return res.status(403).json({
        success: false,
        error: 'Consequential action requires explicit Hold-to-Confirm gate approval',
        requiresApproval: true
      });
    }

    let selectedFiles = req.body.selected_files || files;
    if (!Array.isArray(selectedFiles) || selectedFiles.length === 0) {
      try {
        const diffRes = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/repo/workspace/${encodeParam(id)}/diff`);
        if (diffRes.ok) {
          const diffData = await diffRes.json();
          const changedFiles = (diffData.changes?.files || []).map((f: any) => f.path || f.filename || f);
          if (changedFiles.length > 0) {
            selectedFiles = changedFiles;
          }
        }
      } catch {
        // Fallback
      }
    }

    const commitRes = await fetch(`${BRIDGE_URL.replace(/\/$/, '')}/repo/workspace/${encodeParam(id)}/commit`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        commit_message: commitMessage || 'feat: apply approved workspace changes',
        selected_files: selectedFiles,
        author_name: req.body.author_name,
        author_email: req.body.author_email
      })
    });
    const commitData = await commitRes.json();
    return res.status(commitRes.status).json(commitData);
  } catch (err: any) {
    return res.status(503).json(buildError('BRIDGE_UNAVAILABLE', err.message));
  }
});

export default ideaBeaconWebRouter;
