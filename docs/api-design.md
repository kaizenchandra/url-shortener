# API contract

All management routes require JWT issuer, audience and signature validation. Tenant and subject derive only from signed
claims. Administrative role is tenant scoped. TLS and host allowlisting are ingress responsibilities; forwarded headers
are ignored by the app. All JSON bodies limited to 16 KiB. URL length 4096. HTTP(S), absolute host, no credentials or
control characters; ordinary redirects never fetch destination contents.

| Route                               | Result                                                                            |
|-------------------------------------|-----------------------------------------------------------------------------------|
| POST /api/v1/links                  | 201 JSON with id, shortCode, shortUrl, originalUrl, createdAt, expiresAt, version |
| GET /api/v1/links/{id}              | 200 owner-visible record; otherwise 404                                           |
| GET /api/v1/links?page=0&size=20    | 200 records, max size 100; offset pagination                                      |
| PATCH /api/v1/links/{id}            | body originalUrl/status/expiresAt/version; 200 or version conflict 409            |
| DELETE /api/v1/links/{id}?version=0 | 204 tombstone or 409                                                              |
| GET /api/v1/links/{id}/analytics    | 200 hourly totals, last 30 days                                                   |
| GET /{code}                         | 302/307 Location and no-store                                                     |

POST accepts `originalUrl`, optional `customAlias`, `expiresAt` and `domain`. Idempotency-Key is 1–128 ASCII
letters/digits/underscore/dot/colon/hyphen. Same key and payload returns exact original 201 body. Same key changed
payload is 409. Aliases 3–32 ASCII characters, first alphanumeric, remaining alphanumeric/underscore/hyphen; reserved
words matched ignoring case. Codes are case sensitive. No alias rename or reassignment.

Invalid body/URL 400, unauthenticated 401, unverified domain or invalid identity claims 403, unknown link 404,
alias/version/idempotency conflict 409, expired/disabled/deleted 410, oversized body 413, rate/quota 429, blocked 451,
storage failure 503 with Retry-After. Infrastructure errors must not masquerade as unknown links. Invalid stored
destination fails closed with 400 and should alert operators. Errors omit database internals.

302 is temporary; 307 preserves method but only GET resolution is exposed. no-store is conservative for mutable and
revocable links. Immutable opt-in links could use 301/308 and explicit max-age, accepting that browsers may never
recontact the service for updates/blocking. That policy is not implemented.

Creation limiter: fixed 60-second windows, 60 attempts per subject and 600 per tenant, distributed Redis atomic
counters. Boundary bursts possible; no precise sliding-window guarantee. Lifetime tenant quota defaults to 10,000
reservations including tombstones and expired links; operator changes tenant max_links. This is not a billing quota
reset system.
