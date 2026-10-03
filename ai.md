# AI Tools & Orchestration Guide

This guide details all installed AI tools, command-line interfaces, and configuration manifests for the IQOO Agent platform.

---

## 1. Installed AI Tools & CLI Utilities

### Globally Installed Tools
- **Repomix** (`repomix`): Codebase packager optimized for AI LLMs (packs repo into clean token-efficient context).
  - *Usage*: `repomix` (generates `repomix-output.xml` or `.txt`)
- **Claude Code CLI** (`claude`): Official Anthropic CLI agent for autonomous coding in terminal.
  - *Usage*: `claude` (starts terminal interactive session)
- **Google Gemini CLI** (`gemini`): Google AI developer CLI for Gemini models.
  - *Usage*: `gemini-cli`
- **Ollama Python SDK & CLI** (`ollama`): Local LLM runner and orchestration client.
- **Model Context Protocol SDK** (`@modelcontextprotocol/sdk`): Official standard for exposing tools and resources to LLMs.

---

## 2. Universal Agent Configuration Files

| File | Tool | Functionality |
| :--- | :--- | :--- |
| `AGENTS.md` | Cline, Roo Code, Aider, Antigravity | Project overview, standard commands, boundaries |
| `.cursorrules` | Cursor IDE | Composer and Chat prompt system |
| `.cursor/rules/*.mdc` | Cursor IDE | Glob-scoped modular architectural rules |
| `skills/api-contracts.md` | All Agents | Unified JSON response schema & error handling |
| `skills/ui-component-system.md` | All Agents | Design system, Tailwind rules, accessibility |
| `skills/testing-patterns.md` | All Agents | Unit & integration testing standards |
| `CLAUDE.md` | Claude Code CLI | Session memory and repository instructions |
| `GEMINI.md` | Antigravity / Gemini CLI | Code assist guidelines |
| `.github/copilot-instructions.md` | GitHub Copilot | Autocomplete and conversational guidance |

---

## 3. Dynamic Template Generation

### Kotlin (Android / Phone Execution)
```kotlin
val generator = AgentManifestGenerator()
val agentsMd = generator.generateAgentsMarkdown(
    projectName = "IQOO Agent Architecture",
    description = "Universal AI Agent Architecture Manifest",
    runtime = "Node.js 24.x / TypeScript 5.x"
)
```

### TypeScript / Node.js
```bash
npm run generate
```
Runs `src/generator/generator.ts` to validate and synchronize all agent manifest files across the workspace.

---

## 4. Useful AI Development Commands

```bash
# Pack the entire codebase into an AI-ready context prompt
repomix

# Run full Vitest test suite (12 tests covering NPU, API contracts, export)
npm test

# Regenerate / sync all agent manifests
npm run generate

# Run bootstrap desktop handoff (Bash / Linux / macOS)
./scripts/bootstrap.sh

# Run bootstrap desktop handoff (Windows PowerShell)
.\scripts\bootstrap.ps1

# Generate the 5-slide Pitch Deck PDF
python scripts/generate_pitch_deck.py

# Run Claude Code in current directory
claude
```

---

## 5. API Endpoints

- `GET /api/health`: Health status & Qualcomm GenieX NPU engine telemetry.
- `POST /api/npu/infer`: On-device Snapdragon NPU structured inference from natural-language prompt.
- `POST /api/prompt/parse`: Natural language prompt parser.
- `POST /api/manifests/generate`: Dynamic manifest generator adhering to `ProjectConfigSchema`.
- `POST /api/export`: In-memory streaming zip exporter (`bundle.zip`) containing all manifests and skills.

