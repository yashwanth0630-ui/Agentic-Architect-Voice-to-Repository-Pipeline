# Agent Context & Architectural Guidelines

## 1. Project Overview
- **Project Name:** IQOO Agent Architecture
- **Description:** Universal AI Agent Architecture Manifest, Generator, and Multi-Agent Orchestrator
- **Primary Runtime:** Node.js 24.x / TypeScript 5.x & Python 3.14 / Kotlin
- **Primary Architecture:** Modular Monolith (Express / Next.js + Domain Core + Agent Generators)

## 2. Directory Layout & Module Ownership
```text
├── src/
│   ├── api/          # Route handlers and external adapters
│   ├── core/         # Pure business logic and domain entities
│   ├── components/   # Presentation layer & reusable UI primitives
│   └── lib/          # Utilities, clients, and database singletons
├── tests/            # Integration and end-to-end test suites
└── skills/           # Specific architectural domain guidelines
```

## 3. Standard Commands
Always execute these exact commands; do not guess alternate scripts.

- **Install Dependencies:** npm install
- **Run Local Server:** npm run dev
- **Lint & Format:** npm run lint
- **Execute Test Suite:** npm test

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
