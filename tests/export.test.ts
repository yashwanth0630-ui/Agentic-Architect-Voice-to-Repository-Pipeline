import { describe, it, expect } from 'vitest';
import request from 'supertest';
import app from '../src/api/server.js';

describe('POST /api/export — Green Light Desktop Handoff', () => {
  it('returns a valid zip archive with correct content-type', async () => {
    const res = await request(app)
      .post('/api/export')
      .send({})
      .buffer(true)
      .parse((res, callback) => {
        const chunks: Buffer[] = [];
        res.on('data', (chunk: Buffer) => chunks.push(chunk));
        res.on('end', () => callback(null, Buffer.concat(chunks)));
      })
      .expect(200);

    expect(res.headers['content-type']).toBe('application/zip');
    expect(res.headers['content-disposition']).toBe('attachment; filename="bundle.zip"');
    expect(Buffer.isBuffer(res.body)).toBe(true);
    // Zip magic bytes: PK (0x50 0x4b)
    expect(res.body[0]).toBe(0x50);
    expect(res.body[1]).toBe(0x4b);

    // Verify all manifests and skills are present in the zip header
    const rawZipString = (res.body as Buffer).toString('latin1');
    expect(rawZipString).toContain('AGENTS.md');
    expect(rawZipString).toContain('.cursorrules');
    expect(rawZipString).toContain('CLAUDE.md');
    expect(rawZipString).toContain('GEMINI.md');
    expect(rawZipString).toContain('skills/api-contracts.md');
    expect(rawZipString).toContain('skills/ui-component-system.md');
    expect(rawZipString).toContain('skills/testing-patterns.md');
    expect(rawZipString).toContain('package.json');
  });

  it('returns a valid zip with custom project config', async () => {
    const res = await request(app)
      .post('/api/export')
      .send({ projectName: 'CustomProject', description: 'E2E test project' })
      .buffer(true)
      .parse((res, callback) => {
        const chunks: Buffer[] = [];
        res.on('data', (chunk: Buffer) => chunks.push(chunk));
        res.on('end', () => callback(null, Buffer.concat(chunks)));
      })
      .expect(200);

    expect(res.headers['content-type']).toBe('application/zip');
    expect(Buffer.isBuffer(res.body)).toBe(true);
    expect(res.body.length).toBeGreaterThan(0);
    // Still valid zip
    expect(res.body[0]).toBe(0x50);
    expect(res.body[1]).toBe(0x4b);
  });
});
