# URL shortener

Java 21 / Spring Boot 3.5.16 modular monolith. PostgreSQL owns domain/code reservations, Redis caches resolutions for
two seconds, and a bounded in-process queue aggregates hourly clicks. This is an implementation baseline, **not a
validated million-RPS or production-ready deployment**.

## Run

```sh
cd url-shortener
export JAVA_HOME=$(/usr/libexec/java_home -v 21) # macOS; select Java 21 elsewhere
mvn verify
mvn -Pintegration verify # requires Docker; fails rather than silently skipping
cp .env.example .env
# Edit .env and set both passwords (generate each with openssl rand -hex 24).
docker compose up --build -d --remove-orphans --wait
bash tools/smoke.sh
```

Compose starts a local Keycloak realm at http://localhost:9001/realms/shortener.
The admin console is http://localhost:9001 (username `admin`, password from
`KEYCLOAK_ADMIN_PASSWORD`). The imported development client issues tokens with
audience `shortener`, a service-account `sub`, and `tenant_id=local-dev`.

Get a local token after Keycloak is ready:

```sh
TOKEN=$(bash tools/token.sh)
```

The issuer stays `http://localhost:9001/realms/shortener` for both host and container
validation; Compose fetches signing keys through the internal Keycloak service URL.
Keycloak uses development mode and ephemeral storage; local realm changes reset on
container recreation. The bundled client secret is for local development only.
Production requires a separately managed Keycloak deployment and client credentials.
Optional top-level `roles: [admin]` permits blocking/unblocking within the token's tenant.

```sh
RESPONSE=$(curl --fail-with-body -sS http://localhost:8081/api/v1/links \
 -H "Authorization: Bearer $TOKEN" \
 -H 'Content-Type: application/json' -H 'Idempotency-Key: example-1' \
 -d '{"originalUrl":"https://example.com"}') &&
CODE=$(printf '%s' "$RESPONSE" | python3 -c 'import json, sys; print(json.load(sys.stdin)["shortCode"])') &&
curl -i "http://localhost:8081/$CODE"
```

The final request should return `302` with `Location: https://example.com`.
Use the actual `shortCode` from the create response; the literal path
`/RETURNED_CODE` has no saved link and returns `404`. Unknown codes also return `404`.

For host development against Compose dependencies, start `docker compose up -d postgres redis keycloak`,
export `DATABASE_PASSWORD`, `DATABASE_URL=jdbc:postgresql://localhost:5433/shortener`, `REDIS_PORT=6380`,
and `JWT_ISSUER=http://localhost:9001/realms/shortener`, then run `mvn spring-boot:run` (host app port 8080). Flyway
migrates automatically. `DATABASE_URL`, `DATABASE_USER`, `REDIS_HOST`, `SHORT_DOMAIN`, `SHORT_BASE_URL`,
`STRICT_REDIRECTS`, `REDIRECT_STATUS` override defaults. Production requires TLS termination, trusted host routing,
external secret management and managed HA storage.

## Source map

`domain/`: codes, URL validation and lifecycle. `application/`: tenant transaction, idempotency, quotas, optimistic
management. `infrastructure/persistence/`: JDBC access; `cache/`: Redis; `messaging/`: lossy async counts. `api/`: HTTP
adapters and size limits. `security/`: JWT and Redis creation limiter. `src/test/`: unit and Docker integration tests.
`docker/`, `helm/`, `benchmarks/`, `docs/`: operational assets.

## Semantics

Each create produces a new random ten-character code, except replay of the same owner/tenant-scoped idempotency key and
payload. Aliases are case sensitive and never reassigned. DELETE is a tombstone. Expiration is a derived state checked
on every redirect. PATCH requires `version`; aliases and domains are immutable. Null PATCH fields preserve existing
values; expiration cannot currently be cleared. Default 302 with no-store supports mutable links; 307 is configurable.

## Verification and limitations

See [verification](docs/verification.md) for actual results.
See [architecture](docs/architecture.md), [capacity](docs/capacity-planning.md), [API](docs/api-design.md), [runbook](docs/runbook.md), [failure analysis](docs/failures.md),
and [review](docs/review.md).

Not implemented: automated reputation feeds, domain verification endpoint, distributed durable analytics, unique
visitor/device/referrer/country dimensions, QR codes, shard routing, global invalidation, local hot-key cache, cache
single-flight. Custom domains must be verified by an operator using DNS ownership evidence before inserting a verified
row. These are explicit scope gaps against the full specification. Deployment manifests are templates, not an executed
deployment.

The Compose host ports default to app 8081, Keycloak 9001, PostgreSQL 5433, Redis 6380,
so the rate-limiter stack can stay running. Override them with `APP_PORT`, `KEYCLOAK_PORT`,
`POSTGRES_PORT`, and `REDIS_PORT`. For custom smoke targets, export `BASE_URL` (use
`localhost` for the default short domain) and `KEYCLOAK_URL`. Scripts also need Python 3 and curl.
The smoke test asserts the full link lifecycle and tombstones its test link; it can be repeated.
The local realm permits HTTP for Docker development; production Keycloak must require HTTPS.

Compose returns usable short URLs at `http://localhost:8081`. Host runs can set
`SHORT_BASE_URL=http://localhost:8080`; without an override the public URL defaults to HTTPS on `SHORT_DOMAIN`.
