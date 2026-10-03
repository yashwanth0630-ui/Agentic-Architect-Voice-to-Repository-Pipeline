import { z } from 'zod';

export const MetaSchema = z.object({
  timestamp: z.string().datetime(),
  requestId: z.string().uuid()
});

export const SuccessResponseSchema = <T extends z.ZodTypeAny>(dataSchema: T) =>
  z.object({
    success: z.literal(true),
    data: dataSchema,
    meta: MetaSchema
  });

export const ErrorDetailSchema = z.object({
  field: z.string().optional(),
  message: z.string()
});

export const ErrorResponseSchema = z.object({
  success: z.literal(false),
  error: z.object({
    code: z.string(),
    message: z.string(),
    details: z.array(ErrorDetailSchema).default([])
  })
});

export type Meta = z.infer<typeof MetaSchema>;
export type ErrorDetail = z.infer<typeof ErrorDetailSchema>;
export type ErrorResponse = z.infer<typeof ErrorResponseSchema>;
