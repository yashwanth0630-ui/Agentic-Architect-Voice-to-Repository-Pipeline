# Skill: Testing Patterns & Quality Expectations

## Core Testing Philosophy
- Every new feature, endpoint, or utility must be paired with unit and integration tests.
- High test coverage on domain rules (`src/core/`) and schema validators (`src/api/`).
- Fast test execution via Vitest / Jest.

## Unit Testing Rules
1. **Purity & Isolation**:
   - Unit tests must run without external network access or live databases.
   - Mock all network requests and file system writes where appropriate.
2. **Naming Convention**:
   - Test files live alongside modules or in `tests/`: `*.test.ts` or `*.spec.ts`.
   - Describe blocks: `describe('ManifestGenerator', () => { it('should generate valid AGENTS.md given project config', () => {}) })`.
3. **Edge Case Coverage**:
   - Validate empty inputs, oversized strings, invalid characters, and schema boundary violations.
   - Test error throwing and rejection handling explicitly.

## Integration Testing Rules
1. **API Contracts**:
   - Verify every endpoint produces the RFC 7807 error format or the unified success payload:
     `{ success: true, data: ..., meta: { timestamp, requestId } }`.
2. **Idempotency**:
   - Repeated calls to manifest generation or schema validation must produce consistent, reproducible output.
