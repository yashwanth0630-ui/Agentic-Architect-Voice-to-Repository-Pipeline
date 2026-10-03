import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import path from 'path';
import { buildSuccess, buildError } from '../lib/response.js';
import { 
  ProjectConfigSchema, 
  generateAgentsMarkdown, 
  generateCursorRules, 
  generateClaudeMarkdown,
  generateGeminiMarkdown,
  generateApiContracts,
  generateUiComponentSystem,
  generateTestingPatterns,
  generateScaffoldPackageJson
} from '../generator/generator.js';
import { defaultNpuEngine, SnapdragonModel } from '../core/npu.js';
import exportRouter from './export.js';

const app = express();
const port = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// Request ID & correlation middleware
app.use((req: Request, res: Response, next: NextFunction) => {
  const reqId = (req.headers['x-request-id'] as string) || undefined;
  res.locals.requestId = reqId;
  next();
});

// Health check endpoint
app.get('/api/health', (req: Request, res: Response) => {
  res.json(buildSuccess({ 
    status: 'healthy', 
    uptime: process.uptime(),
    npuEngine: 'Qualcomm GenieX SDK (Snapdragon 8 Gen 3 / 8 Elite)',
    npuStatus: 'ONLINE',
    supportedModels: ['qwen-2.5-coder-7b-int4', 'qwen-2.5-coder-1.5b-int4', 'phi-4-mini-int4']
  }, res.locals.requestId));
});

// Snapdragon NPU Inference endpoint (Qualcomm GenieX SDK bridge)
app.post('/api/npu/infer', async (req: Request, res: Response) => {
  try {
    const { prompt, model } = req.body ?? {};
    if (!prompt || typeof prompt !== 'string' || prompt.trim().length === 0) {
      return res.status(400).json(
        buildError('INVALID_ARGUMENT', 'Field "prompt" is required and must be a non-empty string')
      );
    }

    const selectedModel = (model as SnapdragonModel) || 'qwen-2.5-coder-7b-int4';
    const result = await defaultNpuEngine.inferProjectConfig(prompt, selectedModel);

    const generated = {
      agentsMarkdown: generateAgentsMarkdown(result.config),
      cursorRules: generateCursorRules(result.config),
      claudeMarkdown: generateClaudeMarkdown(result.config),
      geminiMarkdown: generateGeminiMarkdown(result.config),
      apiContracts: generateApiContracts(),
      uiComponentSystem: generateUiComponentSystem(),
      testingPatterns: generateTestingPatterns(),
      packageJson: generateScaffoldPackageJson(result.config)
    };

    return res.json(buildSuccess({
      ...result,
      manifests: generated
    }, res.locals.requestId));
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : 'NPU inference error';
    return res.status(500).json(buildError('INTERNAL_ERROR', msg));
  }
});

// Natural Language Prompt Parser endpoint
app.post('/api/prompt/parse', async (req: Request, res: Response) => {
  try {
    const { prompt } = req.body ?? {};
    if (!prompt || typeof prompt !== 'string') {
      return res.status(400).json(
        buildError('INVALID_ARGUMENT', 'Field "prompt" is required and must be a string')
      );
    }

    const result = await defaultNpuEngine.inferProjectConfig(prompt);
    return res.json(buildSuccess(result, res.locals.requestId));
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : 'Prompt parsing error';
    return res.status(500).json(buildError('INTERNAL_ERROR', msg));
  }
});

// Dynamic Manifest Generator endpoint
app.post('/api/manifests/generate', (req: Request, res: Response) => {
  try {
    const parseResult = ProjectConfigSchema.safeParse(req.body);
    if (!parseResult.success) {
      return res.status(400).json(
        buildError('INVALID_ARGUMENT', 'Validation failed for project configuration', 
          parseResult.error.errors.map(err => ({ field: err.path.join('.'), message: err.message }))
        )
      );
    }

    const config = parseResult.data;
    const generated = {
      agentsMarkdown: generateAgentsMarkdown(config),
      cursorRules: generateCursorRules(config),
      claudeMarkdown: generateClaudeMarkdown(config),
      geminiMarkdown: generateGeminiMarkdown(config),
      apiContracts: generateApiContracts(),
      uiComponentSystem: generateUiComponentSystem(),
      testingPatterns: generateTestingPatterns(),
      packageJson: generateScaffoldPackageJson(config)
    };

    return res.json(buildSuccess(generated, res.locals.requestId));
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : 'Unknown server error';
    return res.status(500).json(buildError('INTERNAL_ERROR', msg));
  }
});

// Internal endpoint demonstrating service-to-service header
app.get('/api/internal/status', (req: Request, res: Response) => {
  const secret = req.headers['x-internal-secret'];
  if (!secret || secret !== (process.env.API_SECRET_KEY || 'secret-key')) {
    return res.status(403).json(buildError('PERMISSION_DENIED', 'Missing or invalid X-Internal-Secret header'));
  }
  return res.json(buildSuccess({ internalServices: 'all operational' }, res.locals.requestId));
});

// Green Light Desktop Handoff — zip export pipeline
app.use('/api', exportRouter);

// Static Web Console & Pitch Deck Delivery
const projectRoot = process.cwd();
const publicDir = path.join(projectRoot, 'public');

app.use(express.static(publicDir));

app.get('/pitch-deck.pdf', (req: Request, res: Response) => {
  const pdfPath = path.join(projectRoot, 'Agentic_Architect_Pitch_Deck.pdf');
  res.sendFile(pdfPath);
});

app.get('/', (req: Request, res: Response) => {
  res.sendFile(path.join(publicDir, 'index.html'));
});

// 404 handler adhering to RFC 7807 contract
app.use((req: Request, res: Response) => {
  res.status(404).json(buildError('RESOURCE_NOT_FOUND', `Route ${req.method} ${req.path} not found`));
});

if (process.env.NODE_ENV !== 'test') {
  app.listen(port, () => {
    console.log(`[IQOO Server] Running on http://localhost:${port}`);
  });
}

export default app;
