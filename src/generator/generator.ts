import * as fs from 'fs';
import * as path from 'path';
import { z } from 'zod';

export const ProjectConfigSchema = z.object({
  projectName: z.string().default('IQOO Agent Architecture'),
  description: z.string().default('Universal AI Agent Architecture Manifest, Generator, and Multi-Agent Orchestrator'),
  runtime: z.string().default('Node.js 24.x / TypeScript 5.x & Python 3.14 / Kotlin'),
  architecture: z.string().default('Modular Monolith (Express / Next.js + Domain Core + Agent Generators)'),
  installCommand: z.string().default('npm install'),
  devCommand: z.string().default('npm run dev'),
  lintCommand: z.string().default('npm run lint'),
  testCommand: z.string().default('npm test'),
  frontendFramework: z.string().default('React 18+ / Next.js 14+ App Router'),
  backendFramework: z.string().default('Express / Node.js 24+ & Kotlin / Python 3.14'),
  stylingEngine: z.string().default('Tailwind CSS, shadcn/ui primitives'),
  dataLayer: z.string().default('TanStack Query, Zod runtime validation, Prisma')
});

export type ProjectConfig = z.infer<typeof ProjectConfigSchema>;

export function generateAgentsMarkdown(config: ProjectConfig): string {
  return `# Agent Context & Architectural Guidelines

## 1. Project Overview
- **Project Name:** ${config.projectName}
- **Description:** ${config.description}
- **Primary Runtime:** ${config.runtime}
- **Primary Architecture:** ${config.architecture}

## 2. Directory Layout & Module Ownership
\`\`\`text
├── src/
│   ├── api/          # Route handlers and external adapters
│   ├── core/         # Pure business logic and domain entities
│   ├── components/   # Presentation layer & reusable UI primitives
│   └── lib/          # Utilities, clients, and database singletons
├── tests/            # Integration and end-to-end test suites
└── skills/           # Specific architectural domain guidelines
\`\`\`

## 3. Standard Commands
Always execute these exact commands; do not guess alternate scripts.

- **Install Dependencies:** ${config.installCommand}
- **Run Local Server:** ${config.devCommand}
- **Lint & Format:** ${config.lintCommand}
- **Execute Test Suite:** ${config.testCommand}

## 4. Operational Boundaries & Safety Guardrails
- **File Modifications:** Never edit auto-generated files (e.g., dist/, .next/, lockfiles) directly.
- **Sensitive Data:** Never hardcode secrets, API keys, or private tokens. Always read from environment variables defined in .env.example.
- **Destructive Commands:** Never execute destructive Git commands (git reset --hard, git push --force) or recursive deletions (rm -rf /) without explicit confirmation.
- **Scope Creep:** Implement only what was requested. Avoid rewriting unrelated modules or changing existing formatting styles unnecessarily.

## 5. Domain Skill Index
Consult the dedicated documents in skills/ before authoring logic for these subsystems:

- **UI & Styling:** skills/ui-component-system.md
- **Backend & Contracts:** skills/api-contracts.md
- **Testing Expectations:** skills/testing-patterns.md
`;
}

export function generateCursorRules(config: ProjectConfig): string {
  return `You are an expert full-stack engineer building ${config.projectName}. Follow these instructions strictly across all code generations.

### Core Tech Stack
- Frontend: ${config.frontendFramework}
- Backend: ${config.backendFramework}
- Styling: ${config.stylingEngine}
- State / Data Fetching: ${config.dataLayer}

### Code Style & Implementation Rules
1. **Type Safety:** 
   - Write strict, fully typed code. Avoid \`any\` in TypeScript or unannotated signatures in Python / Kotlin.
   - Use runtime validation (e.g., Zod or Pydantic) at all system boundaries (APIs, forms, local storage).

2. **Component Architecture:**
   - Prefer functional components with clear prop interfaces.
   - Separate stateful logic into custom hooks; keep UI rendering components pure and declarative.
   - Ensure interactive UI elements have accessible labels (\`aria-label\`, correct semantic HTML tags).

3. **Error Handling & Logging:**
   - Avoid empty \`catch\` blocks. Always handle errors gracefully with typed error wrappers.
   - Log actionable error messages that include contextual identifiers, avoiding raw object dumps.

4. **Testing Expectations:**
   - When generating new features, write corresponding unit tests covering both the happy path and critical edge cases.
   - Mock external network requests and persistent database instances during test runs.

### Anti-Patterns (Do NOT Do)
- Do not import client-only packages inside server components/modules.
- Do not add new external npm/pip dependencies without explicitly notifying the developer.
- Do not use inline styles when utility classes (e.g., Tailwind) are configured.
`;
}

export function generateClaudeMarkdown(config: ProjectConfig): string {
  return `# Claude Code Guidelines - ${config.projectName}

## Project Overview
${config.description}

## Standard Commands
- Build & Run Dev: \`${config.devCommand}\`
- Tests: \`${config.testCommand}\`
- Lint: \`${config.lintCommand}\`
- Install: \`${config.installCommand}\`

## Architecture & Code Style
- **Type Safety**: Full TypeScript strict mode. Runtime schema validation with Zod.
- **API Contracts**: All API responses use \`{ success: true, data: ..., meta: ... }\` or RFC 7807 error format.
- **Security**: Never hardcode secrets. Read strictly from environment variables.
- **Safety**: Do not execute destructive Git operations (\`git reset --hard\`, \`git push --force\`) or recursive deletions.
`;
}

export function generateGeminiMarkdown(config: ProjectConfig): string {
  return `# Gemini & Antigravity Instructions - ${config.projectName}

## Project Context
${config.description}

## Architectural Guidelines
- **Modularity**: Code organized into \`src/api/\`, \`src/core/\`, \`src/components/\`, \`src/lib/\`, and \`src/generator/\`.
- **Validation**: Strict schema validation with Zod on all payloads.
- **API Contracts**: Follow RFC 7807 and success envelope defined in \`skills/api-contracts.md\`.
- **Guardrails**: No editing generated artifacts directly, no destructive commands.
`;
}

export function generateApiContracts(): string {
  return `# Skill: API Design & Error Contracts

## Response Schema Format
All JSON responses from internal endpoints must conform to this schema:

### Success Response
\`\`\`json
{
  "success": true,
  "data": {},
  "meta": {
    "timestamp": "ISO-8601 string",
    "requestId": "uuid-v4"
  }
}
\`\`\`

### Error Response (RFC 7807 Pattern)
\`\`\`json
{
  "success": false,
  "error": {
    "code": "RESOURCE_NOT_FOUND",
    "message": "Human-readable explanation",
    "details": []
  }
}
\`\`\`

## Authentication & Headers
- Client calls must supply bearer authorization: \`Authorization: Bearer <token>\`.
- Internal service-to-service calls require the \`X-Internal-Secret\` header.
`;
}

export function generateUiComponentSystem(): string {
  return `# Skill: UI & Design Component System

## Architectural Principles
1. **Design System & Styling**:
   - Use utility classes via Tailwind CSS or CSS variables.
   - Absolutely no raw inline style objects (\`style={{...}}\`) except for dynamic CSS transforms or container queries.
   - Support dark mode by default (\`dark:\` variant or system preference tokens).

2. **Component Composition**:
   - Prefer functional components with explicit TypeScript interfaces for props (\`interface ButtonProps { ... }\`).
   - Keep presentational components pure and stateless; extract stateful logic and data queries into custom hooks.
   - Reusable primitives live in \`src/components/ui/\` (buttons, inputs, cards, dialogs).
   - Domain composite widgets live in \`src/components/features/\`.

3. **Accessibility (a11y)**:
   - Provide explicit \`aria-label\` or \`aria-labelledby\` attributes for icon-only buttons and interactive controls.
   - Ensure keyboard navigability (\`Tab\`, \`Escape\`, \`Enter\`, \`Space\`) on all modal/dropdown dialogs.
   - Semantic HTML: Use \`<header>\`, \`<main>\`, \`<section>\`, \`<nav>\`, \`<article>\`, \`<button>\` instead of clickable \`<div>\` elements.

4. **Animations & Polish**:
   - Micro-interactions on buttons, hovers, active states, and transitions (e.g. \`transition-all duration-200 ease-in-out\`).
   - Loading skeletons and optimistic UI updates for async operations.
`;
}

export function generateTestingPatterns(): string {
  return `# Skill: Testing Patterns & Quality Expectations

## Core Testing Philosophy
- Every new feature, endpoint, or utility must be paired with unit and integration tests.
- High test coverage on domain rules (\`src/core/\`) and schema validators (\`src/api/\`).
- Fast test execution via Vitest / Jest.

## Unit Testing Rules
1. **Purity & Isolation**:
   - Unit tests must run without external network access or live databases.
   - Mock all network requests and file system writes where appropriate.
2. **Naming Convention**:
   - Test files live alongside modules or in \`tests/\`: \`*.test.ts\` or \`*.spec.ts\`.
   - Describe blocks: \`describe('ManifestGenerator', () => { it('should generate valid AGENTS.md given project config', () => {}) })\`.
3. **Edge Case Coverage**:
   - Validate empty inputs, oversized strings, invalid characters, and schema boundary violations.
   - Test error throwing and rejection handling explicitly.

## Integration Testing Rules
1. **API Contracts**:
   - Verify every endpoint produces the RFC 7807 error format or the unified success payload:
     \`{ success: true, data: ..., meta: { timestamp, requestId } }\`.
2. **Idempotency**:
   - Repeated calls to manifest generation or schema validation must produce consistent, reproducible output.
`;
}

export function generateScaffoldPackageJson(config: ProjectConfig): string {
  const pkgName = config.projectName.toLowerCase().replace(/[^a-z0-9_-]/g, '-');
  return JSON.stringify(
    {
      name: pkgName,
      version: '0.1.0',
      description: config.description,
      private: true,
      scripts: {
        dev: config.devCommand.replace(/^npm run /, ''),
        build: 'tsc',
        lint: config.lintCommand.replace(/^npm run /, ''),
        test: config.testCommand.replace(/^npm test/, 'vitest run')
      },
      dependencies: {
        zod: '^3.24.2'
      },
      devDependencies: {
        typescript: '^5.8.2',
        vitest: '^3.0.7'
      }
    },
    null,
    2
  );
}

export function syncAllManifests(targetDir: string = process.cwd(), overrides: Partial<ProjectConfig> = {}) {
  const config = ProjectConfigSchema.parse(overrides);
  const skillsDir = path.join(targetDir, 'skills');

  if (!fs.existsSync(skillsDir)) {
    fs.mkdirSync(skillsDir, { recursive: true });
  }

  fs.writeFileSync(path.join(targetDir, 'AGENTS.md'), generateAgentsMarkdown(config), 'utf-8');
  fs.writeFileSync(path.join(targetDir, '.cursorrules'), generateCursorRules(config), 'utf-8');
  fs.writeFileSync(path.join(targetDir, 'CLAUDE.md'), generateClaudeMarkdown(config), 'utf-8');
  fs.writeFileSync(path.join(targetDir, 'GEMINI.md'), generateGeminiMarkdown(config), 'utf-8');
  fs.writeFileSync(path.join(skillsDir, 'api-contracts.md'), generateApiContracts(), 'utf-8');
  fs.writeFileSync(path.join(skillsDir, 'ui-component-system.md'), generateUiComponentSystem(), 'utf-8');
  fs.writeFileSync(path.join(skillsDir, 'testing-patterns.md'), generateTestingPatterns(), 'utf-8');

  console.log(`[ManifestGenerator] Successfully synchronized AI agent files to: ${targetDir}`);
}

// Execute directly if run via CLI
if (require.main === module) {
  syncAllManifests();
}

