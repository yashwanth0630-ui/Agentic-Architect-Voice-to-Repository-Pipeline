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
