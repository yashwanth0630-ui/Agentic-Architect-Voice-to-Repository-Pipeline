import { describe, it, expect } from 'vitest';
import { generateAgentsMarkdown, generateCursorRules, generateApiContracts, ProjectConfigSchema } from '../src/generator/generator.js';

describe('Manifest Generator Logic', () => {
  const sampleConfig = ProjectConfigSchema.parse({
    projectName: 'DemoAgent',
    description: 'Autonomous Coding Agent',
    runtime: 'Node.js 24.x'
  });

  it('generates AGENTS.md with correct project parameters', () => {
    const agents = generateAgentsMarkdown(sampleConfig);
    expect(agents).toContain('# Agent Context & Architectural Guidelines');
    expect(agents).toContain('DemoAgent');
    expect(agents).toContain('Autonomous Coding Agent');
    expect(agents).toContain('skills/api-contracts.md');
  });

  it('generates .cursorrules with strict rules and anti-patterns', () => {
    const cursor = generateCursorRules(sampleConfig);
    expect(cursor).toContain('DemoAgent');
    expect(cursor).toContain('Type Safety');
    expect(cursor).toContain('Anti-Patterns');
  });

  it('generates api-contracts skill with RFC 7807 specifications', () => {
    const contracts = generateApiContracts();
    expect(contracts).toContain('# Skill: API Design & Error Contracts');
    expect(contracts).toContain('RFC 7807 Pattern');
    expect(contracts).toContain('RESOURCE_NOT_FOUND');
  });
});
