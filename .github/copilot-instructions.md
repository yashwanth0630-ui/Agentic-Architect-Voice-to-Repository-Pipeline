# GitHub Copilot Instructions

- Project: IQOO Agent Architecture
- Always generate TypeScript with strict typing. Avoid `any`.
- Enforce API contract envelope: `{ success: boolean, data?: unknown, error?: unknown, meta: { timestamp, requestId } }`.
- Follow functional React component patterns and Tailwind CSS utilities.
- Never hardcode credentials or secrets.
