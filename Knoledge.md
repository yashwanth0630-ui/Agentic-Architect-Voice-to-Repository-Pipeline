# IQOO Knowledge Base: Universal AI Agent Architecture & Context Engineering

## 1. Executive Summary
Modern software engineering utilizes multi-agent systems (Claude Code, Cline, Roo Code, Aider, Cursor Composer, Google Antigravity, GitHub Copilot). To ensure deterministic, high-quality code generation across heterogeneous agents, projects require a unified **Agent Manifest System**.

This repository implements the standardized Universal Agent Architecture, ensuring context persistence, behavioral guardrails, and protocol-compliant contracts across all AI developer tools.

---

## 2. Core Architecture Components

```text
├── AGENTS.md                  # Root architectural manifest for multi-agent systems
├── .cursorrules               # Cursor IDE composer & chat steering rules
├── .cursor/rules/*.mdc        # Modular Cursor context rules (tech stack, API, testing)
├── skills/                    # Specialized domain skills for modular agent execution
│   ├── api-contracts.md       # RFC 7807 and success envelope schemas
│   ├── ui-component-system.md # UI design tokens, accessibility & component patterns
│   └── testing-patterns.md    # Unit/integration test patterns and mock boundaries
├── CLAUDE.md                  # Anthropic Claude Code CLI guidelines
├── GEMINI.md                  # Google Antigravity / Gemini CLI context
└── .github/copilot-instructions.md # GitHub Copilot workspace instructions
```

---

## 3. The Manifest & Context Pyramid

1. **Global Boundaries (`AGENTS.md`)**:
   - High-level directory ownership and primary commands (`install`, `dev`, `lint`, `test`).
   - Hard safety rules: Never modify generated assets, never run destructive Git commands.
2. **Editor-Specific Steering (`.cursorrules`, `.cursor/rules/*.mdc`)**:
   - Glob-based rule activation for file types and subsystems.
   - Code style, naming conventions, and anti-patterns.
3. **Domain Skills (`skills/*.md`)**:
   - Deep-dive technical specifications loaded on demand by agents tackling specific subsystems.
   - Exact schemas, status codes, and structural rules.
4. **Dynamic Codebase Ingestion (`repomix.config.json`)**:
   - Packages repository state into token-efficient XML/Markdown prompts for one-shot LLM tasks.

---

## 4. Multi-Agent Interoperability Matrix

| Tool / Agent | Target File | Purpose |
| :--- | :--- | :--- |
| **Cline / Roo Code** | `AGENTS.md` | Primary instruction set, shell command whitelist, path boundaries |
| **Cursor IDE** | `.cursorrules`, `.cursor/rules/*.mdc` | Real-time code completions, chat context, composer generation |
| **Claude Code CLI** | `CLAUDE.md` | Session initialization, test workflows, git boundaries |
| **Aider** | `AGENTS.md`, `.aider.conf.yml` | Repo map generation, commit message formatting, coding constraints |
| **Google Antigravity** | `GEMINI.md`, `.agents/rules/` | Customization system, autonomous planner, IDE execution |
| **GitHub Copilot** | `.github/copilot-instructions.md` | Inline autocomplete and PR review context |

---

## 5. Security & Boundary Guardrails
- **Zero Secret Ingestion**: All credentials, tokens, and keys must stay in `.env` (excluded by `.gitignore` and `repomix`).
- **Non-Destructive Operations**: Prohibit automated execution of `git push --force`, `git reset --hard`, and `rm -rf`.
- **Lockfile Integrity**: Agents must never manually edit lockfiles (`package-lock.json`, `pnpm-lock.yaml`, `poetry.lock`).
