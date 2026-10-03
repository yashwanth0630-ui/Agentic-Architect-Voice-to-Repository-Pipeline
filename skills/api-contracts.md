# Skill: API Design & Error Contracts

## Response Schema Format
All JSON responses from internal endpoints must conform to this schema:

### Success Response
```json
{
  "success": true,
  "data": {},
  "meta": {
    "timestamp": "ISO-8601 string",
    "requestId": "uuid-v4"
  }
}
```

### Error Response (RFC 7807 Pattern)
```json
{
  "success": false,
  "error": {
    "code": "RESOURCE_NOT_FOUND",
    "message": "Human-readable explanation",
    "details": []
  }
}
```

## Authentication & Headers
- Client calls must supply bearer authorization: `Authorization: Bearer <token>`.
- Internal service-to-service calls require the `X-Internal-Secret` header.
- Correlation IDs must be passed or generated via `X-Request-Id` for distributed trace logging.

## Standard Error Codes
- `INVALID_ARGUMENT` (400): Malformed payload or validation schema failure.
- `UNAUTHENTICATED` (401): Missing or expired bearer token.
- `PERMISSION_DENIED` (403): Caller lacks necessary ACL scope.
- `RESOURCE_NOT_FOUND` (404): Requested entity does not exist.
- `CONFLICT` (409): State conflict or duplicate unique key.
- `INTERNAL_ERROR` (500): Unhandled exception; stack trace hidden from client, logged to trace aggregator.
