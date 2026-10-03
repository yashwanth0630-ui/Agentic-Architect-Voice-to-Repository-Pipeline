import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import { buildSuccess, buildError } from '../lib/response.js';
import { ProjectConfigSchema, generateAgentsMarkdown, generateCursorRules, generateApiContracts } from '../generator/generator.js';

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
  res.json(buildSuccess({ status: 'healthy', uptime: process.uptime() }, res.locals.requestId));
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
      apiContracts: generateApiContracts()
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
