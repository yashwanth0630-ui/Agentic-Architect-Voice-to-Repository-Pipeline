# Gemini & Antigravity Instructions - IQOO Agent Architecture

## Project Context
Universal Agent Architecture Manifest, Generator, and Multi-Agent Orchestrator.

## Architectural Guidelines
- **Modularity**: Code organized into `src/api/`, `src/core/`, `src/components/`, `src/lib/`, and `src/generator/`.
- **Validation**: Strict schema validation with Zod on all payloads.
- **API Contracts**: Follow RFC 7807 and success envelope defined in `skills/api-contracts.md`.
- **Guardrails**: No editing generated artifacts directly, no destructive commands.
