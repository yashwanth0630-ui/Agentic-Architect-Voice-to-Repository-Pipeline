import { Router, Request, Response } from 'express';
import { ZipArchive, ArchiverError } from 'archiver';
import { buildError } from '../lib/response.js';
import {
  ProjectConfigSchema,
  generateAgentsMarkdown,
  generateCursorRules,
  generateClaudeMarkdown,
  generateGeminiMarkdown,
  generateApiContracts,
  generateUiComponentSystem,
  generateTestingPatterns,
  generateScaffoldPackageJson,
  type ProjectConfig
} from '../generator/generator.js';

const router = Router();

/**
 * POST /api/export
 *
 * Accepts an optional JSON body matching ProjectConfigSchema (all fields
 * have sensible defaults, so an empty body `{}` is perfectly valid).
 *
 * Returns a `bundle.zip` containing:
 *   AGENTS.md
 *   .cursorrules
 *   CLAUDE.md
 *   GEMINI.md
 *   package.json
 *   skills/api-contracts.md
 *   skills/ui-component-system.md
 *   skills/testing-patterns.md
 *
 * The archive is built entirely in-memory via Node streams —
 * nothing is written to a temp directory on the server.
 */
router.post('/export', (req: Request, res: Response) => {
  try {
    // ── 1. Validate & parse incoming config (all fields optional) ──
    const parseResult = ProjectConfigSchema.safeParse(req.body ?? {});

    if (!parseResult.success) {
      return res.status(400).json(
        buildError(
          'INVALID_ARGUMENT',
          'Validation failed for project configuration',
          parseResult.error.errors.map((e) => ({
            field: e.path.join('.'),
            message: e.message
          }))
        )
      );
    }

    const config: ProjectConfig = parseResult.data;

    // ── 2. Generate manifest contents from templates ──
    const agentsMd = generateAgentsMarkdown(config);
    const cursorRules = generateCursorRules(config);
    const claudeMd = generateClaudeMarkdown(config);
    const geminiMd = generateGeminiMarkdown(config);
    const packageJson = generateScaffoldPackageJson(config);
    const apiContracts = generateApiContracts();
    const uiComponentSystem = generateUiComponentSystem();
    const testingPatterns = generateTestingPatterns();

    // ── 3. Set response headers for a downloadable zip ──
    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', 'attachment; filename="bundle.zip"');

    // ── 4. Create the archive and pipe it straight to the response ──
    const archive = new ZipArchive({ zlib: { level: 9 } });

    // If archiver encounters an internal error, surface it as a 500
    // before the headers are fully flushed (best-effort).
    archive.on('error', (err: ArchiverError) => {
      console.error('[export] Archive error:', err.message);
      if (!res.headersSent) {
        res.status(500).json(
          buildError('INTERNAL_ERROR', `Archive generation failed: ${err.message}`)
        );
      }
    });

    // When archiver is done writing, the response stream closes automatically.
    archive.on('end', () => {
      console.log(
        `[export] Archive streamed successfully — ${archive.pointer()} total bytes`
      );
    });

    archive.pipe(res);

    // ── 5. Append generated files to the archive ──
    archive.append(agentsMd, { name: 'AGENTS.md' });
    archive.append(cursorRules, { name: '.cursorrules' });
    archive.append(claudeMd, { name: 'CLAUDE.md' });
    archive.append(geminiMd, { name: 'GEMINI.md' });
    archive.append(packageJson, { name: 'package.json' });
    archive.append(apiContracts, { name: 'skills/api-contracts.md' });
    archive.append(uiComponentSystem, { name: 'skills/ui-component-system.md' });
    archive.append(testingPatterns, { name: 'skills/testing-patterns.md' });

    // Signals that no more entries will be added; flushes and closes.
    archive.finalize();
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Unknown server error';
    console.error('[export] Unexpected error:', message);

    if (!res.headersSent) {
      return res.status(500).json(buildError('INTERNAL_ERROR', message));
    }
  }
});

export default router;
