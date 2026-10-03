import { describe, it, expect } from 'vitest';
import request from 'supertest';
import app from '../src/api/server.js';
import { defaultNpuEngine } from '../src/core/npu.js';

describe('Snapdragon NPU Mobile Inference Engine & Parser', () => {
  it('parses natural-language prompt into strict ProjectConfig for FastAPI + Next.js + Tailwind', async () => {
    const prompt = 'FastAPI backend, Next.js frontend, Tailwind UI';
    const result = await defaultNpuEngine.inferProjectConfig(prompt, 'qwen-2.5-coder-7b-int4');

    expect(result.config.backendFramework).toContain('FastAPI');
    expect(result.config.frontendFramework).toContain('Next.js');
    expect(result.config.stylingEngine).toContain('Tailwind');
    expect(result.config.runtime).toContain('Python 3.14');
    expect(result.config.devCommand).toContain('uvicorn');
    expect(result.config.testCommand).toBe('pytest');
    expect(result.detectedKeywords).toContain('FastAPI');
    expect(result.detectedKeywords).toContain('Next.js');
    expect(result.detectedKeywords).toContain('Tailwind UI');

    // Telemetry validation
    expect(result.telemetry.hardwareAccelerator).toBe('Qualcomm Hexagon NPU');
    expect(result.telemetry.quantization).toBe('INT4');
    expect(result.telemetry.sdk).toBe('Qualcomm GenieX SDK');
    expect(result.telemetry.isOffline).toBe(true);
    expect(result.telemetry.latencyMs).toBeGreaterThan(0);
  });

  it('parses alternative prompt with Go and Vue', async () => {
    const prompt = 'Go Gin backend, Vue frontend with Prisma and Vanilla CSS';
    const result = await defaultNpuEngine.inferProjectConfig(prompt, 'phi-4-mini-int4');

    expect(result.config.backendFramework).toContain('Go');
    expect(result.config.frontendFramework).toContain('Vue');
    expect(result.config.stylingEngine).toContain('Vanilla CSS');
    expect(result.telemetry.model).toBe('phi-4-mini-int4');
  });

  it('POST /api/npu/infer returns manifests and RFC 7807 compliant success payload', async () => {
    const res = await request(app)
      .post('/api/npu/infer')
      .send({ prompt: 'FastAPI backend, Next.js frontend, Tailwind UI' })
      .expect(200);

    expect(res.body.success).toBe(true);
    expect(res.body.data.config).toBeDefined();
    expect(res.body.data.telemetry.hardwareAccelerator).toBe('Qualcomm Hexagon NPU');
    expect(res.body.data.manifests.agentsMarkdown).toContain('FastAPI');
    expect(res.body.data.manifests.cursorRules).toContain('Next.js');
    expect(res.body.data.manifests.claudeMarkdown).toBeDefined();
    expect(res.body.data.manifests.geminiMarkdown).toBeDefined();
    expect(res.body.data.manifests.packageJson).toBeDefined();
    expect(res.body.data.manifests.uiComponentSystem).toBeDefined();
    expect(res.body.data.manifests.testingPatterns).toBeDefined();
  });

  it('POST /api/npu/infer rejects empty prompt with RFC 7807 error envelope', async () => {
    const res = await request(app)
      .post('/api/npu/infer')
      .send({ prompt: '   ' })
      .expect(400);

    expect(res.body.success).toBe(false);
    expect(res.body.error.code).toBe('INVALID_ARGUMENT');
    expect(res.body.error.message).toContain('non-empty string');
  });

  it('GET /api/health includes NPU engine status', async () => {
    const res = await request(app).get('/api/health').expect(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data.npuEngine).toContain('Qualcomm GenieX SDK');
    expect(res.body.data.npuStatus).toBe('ONLINE');
  });
});
