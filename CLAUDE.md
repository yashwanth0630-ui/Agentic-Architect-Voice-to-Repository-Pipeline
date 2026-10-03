# Claude Code Guidelines - IQOO Agent Architecture

## Project Overview
Universal Agent Architecture Manifest, Dynamic Template Generator, and Multi-Agent Orchestration Platform.

## Standard Commands
- Build & Run Dev: `npm run dev`
- Tests: `npm test`
- Lint: `npm run lint`
- Generate Manifests: `npm run generate`
- Repomix Pack: `npx repomix`

## Architecture & Code Style
- **Type Safety**: Full TypeScript strict mode. Runtime schema validation with Zod.
- **API Contracts**: All API responses use `{ success: true, data: ..., meta: ... }` or RFC 7807 error format.
- **Security**: Never hardcode secrets. Read strictly from environment variables.
- **Safety**: Do not execute destructive Git operations (`git reset --hard`, `git push --force`) or recursive deletions.
