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

---

## 6. Agentic Architect: Voice-to-Repository Pipeline

```text
┌────────────────────────────────┐     ┌────────────────────────────────┐     ┌────────────────────────────────┐
│ 1. Mobile Layer (iQOO 13)      │     │ 2. Core Manifest Engine        │     │ 3. Desktop Handoff & Auto-Boot │
│ • Voice Prompt Ingestion       │ ──> │ • Strict Zod & RFC 7807 Rules  │ ──> │ • iQOO Office Kit Folder Sync  │
│ • Hexagon NPU (45 TOPS)        │     │ • Full Manifest Pyramid        │     │ • bootstrap.sh / .ps1 Unpack   │
│ • Qwen 2.5 / Phi-4 INT4        │     │ • bundle.zip In-Memory Stream  │     │ • npm install & Auto-Boot IDE  │
│ • 100% Offline (GenieX SDK)    │     │ • Kotlin & TypeScript Writers  │     │ • Instant Multi-Agent Priming  │
└────────────────────────────────┘     └────────────────────────────────┘     └────────────────────────────────┘
```

### The 4 Execution Phases
1. **Input & On-Device Processing (Mobile Layer - Native Android App in `android/`)**:
   - Voice audio captured via Android native `SpeechRecognizer` (`VoicePromptManager.kt`).
   - Single-screen Jetpack Compose UI with large pulsing microphone button (`MainActivity.kt`).
   - Snapdragon NPU offload via Qualcomm GenieX SDK INT4 quantized inference (`QualcommGenieXNpuEngine.kt`).
   - Constrained grammar decoding ensures output complies with typed `AgentProjectConfig`.
   - On-device packaging writing directly to `/sdcard/iQOO_Share/bundle.zip` for iQOO Office Kit transfer.
2. **Manifest Generation & Packaging (Core Engine)**:
   - Assembles `AGENTS.md`, `.cursorrules`, `CLAUDE.md`, `GEMINI.md`, and domain `skills/`.
   - Packages into `bundle.zip` through Express `POST /api/export` or Kotlin file writer.
3. **Desktop Handoff & Auto-Boot (Laptop Execution)**:
   - Synchronized over local Wi-Fi/Bluetooth via iQOO Office Kit shared folders or HTTP.
   - `bootstrap.sh` extracts files, hydrates dependencies with `npm install`, and boots Cursor/VS Code.
   - Primed agents immediately adopt workspace rules.
4. **Final Submission Artifacts**:
   - Monorepo repository with Express server, Kotlin generator, and 100% passing Vitest suite.
   - `ManifestViewer.tsx` interactive console.
   - `Agentic_Architect_Pitch_Deck.pdf` 5-slide executive presentation.

