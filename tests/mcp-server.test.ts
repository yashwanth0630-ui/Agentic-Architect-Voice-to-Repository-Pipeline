import { describe, it, expect } from 'vitest';
import { defaultNpuEngine } from '../src/core/npu.js';
import {
  generateAgentsMarkdown,
  generateCursorRules,
  ProjectConfigSchema
} from '../src/generator/generator.js';

describe('Model Context Protocol (MCP) Tools for Android Studio', () => {
  it('infer_architecture tool returns verified config and telemetry', async () => {
    const result = await defaultNpuEngine.inferProjectConfig(
      'FastAPI backend, Next.js frontend, Tailwind UI',
      'qwen-2.5-coder-7b-int4'
    );

    expect(result.config.backendFramework).toContain('FastAPI');
    expect(result.config.frontendFramework).toContain('Next.js');
    expect(result.telemetry.hardwareAccelerator).toBe('Qualcomm Hexagon NPU');
    expect(result.telemetry.isOffline).toBe(true);
  });

  it('generate_agent_manifests produces all pyramid documents', () => {
    const config = ProjectConfigSchema.parse({
      projectName: 'AndroidStudioTest',
      description: 'MCP Test Project',
    });

    const agentsMd = generateAgentsMarkdown(config);
    const cursorRules = generateCursorRules(config);

    expect(agentsMd).toContain('AndroidStudioTest');
    expect(cursorRules).toContain('AndroidStudioTest');
  });
});
