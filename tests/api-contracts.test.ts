import { describe, it, expect } from 'vitest';
import { buildSuccess, buildError } from '../src/lib/response.js';
import { SuccessResponseSchema, ErrorResponseSchema } from '../src/core/contracts.js';
import { z } from 'zod';

describe('API Contracts & RFC 7807 Compliance', () => {
  it('generates valid success response envelope matching schema', () => {
    const payload = { id: 'test-123', status: 'ready' };
    const response = buildSuccess(payload, '123e4567-e89b-12d3-a456-426614174000');

    expect(response.success).toBe(true);
    expect(response.data).toEqual(payload);
    expect(response.meta.requestId).toBe('123e4567-e89b-12d3-a456-426614174000');
    expect(new Date(response.meta.timestamp).getTime()).toBeGreaterThan(0);

    const dataSchema = z.object({ id: z.string(), status: z.string() });
    const validated = SuccessResponseSchema(dataSchema).safeParse(response);
    expect(validated.success).toBe(true);
  });

  it('generates valid RFC 7807 error response envelope', () => {
    const errorResponse = buildError(
      'RESOURCE_NOT_FOUND',
      'The requested manifest could not be found',
      [{ field: 'manifestId', message: 'Unknown identifier' }]
    );

    expect(errorResponse.success).toBe(false);
    expect(errorResponse.error.code).toBe('RESOURCE_NOT_FOUND');
    expect(errorResponse.error.details).toHaveLength(1);

    const validated = ErrorResponseSchema.safeParse(errorResponse);
    expect(validated.success).toBe(true);
  });
});
