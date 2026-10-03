import { randomUUID } from 'crypto';
import { ErrorResponse, Meta } from '../core/contracts.js';

export function createMeta(requestId?: string): Meta {
  return {
    timestamp: new Date().toISOString(),
    requestId: requestId || randomUUID()
  };
}

export function buildSuccess<T>(data: T, requestId?: string) {
  return {
    success: true as const,
    data,
    meta: createMeta(requestId)
  };
}

export function buildError(code: string, message: string, details: Array<{ field?: string; message: string }> = []): ErrorResponse {
  return {
    success: false as const,
    error: {
      code,
      message,
      details
    }
  };
}
