# Operations

Prerequisites: TLS ingress with allowed Host list, trusted OIDC issuer, private PostgreSQL/Redis networking, managed
multi-AZ primary with synchronous standby, backups/PITR, secret manager, metrics authentication, image registry. Create
`shortener-secrets` externally with DATABASE_PASSWORD and other secret overrides; never check credentials into Git. Edit
ConfigMap hosts, build/push an immutable image and replace image reference before
`kubectl apply -f helm/kubernetes.yaml`. Manifests are raw Kubernetes YAML, not a Helm chart. Repository-root
`.github/workflows/url-shortener.yml` runs verification from this subdirectory; the subproject includes a copy for
extraction.

Database commits rely on PostgreSQL fsync and synchronous_commit enabled. Local durable volume survives container
restart but is not HA. Zero acknowledged-write loss requires synchronous failover quorum and correct fencing; async DR
permits loss. Proposed RPO: zero within AZ failover, <=5 min remote disaster; RTO <=15 min regional failover, <=4h
restore. These are objectives needing drills. HA handles component loss; DR restores after regional/data corruption
events.

Redis incident: monitor cache error and miss rates, shed traffic at ingress to measured DB budget, avoid retry storms;
creation fails closed. Strict mode is stronger revocation with more DB load. Do not blindly toggle it under heavy
traffic. DB incident: preserve fencing, inspect primary health and connection-pool wait, promote only qualified standby;
return 503 for uncached resolution. Never restore a writable stale primary alongside a promoted writer. Cache flush on
recovery/restore avoids stale routing.

Abuse: authenticated tenant admin PATCH status BLOCKED with version. In bounded mode some redirects continue briefly;
for strict response stop traffic or enable authoritative mode on all pools and drain old pods. Existing browser
permanent caches cannot be revoked. Public shortening needs external reputation feeds, reporting/takedown staffing,
creation approval for untrusted tenants, and egress-free redirects. URL syntax validation alone does not prevent
phishing. Domain verification requires DNS TXT challenge and periodic revalidation by an operator; prevent reassignment
on tenant transfer. If future previews/reputation clients fetch URLs, add private/reserved IP denial after DNS
resolution, pin validated addresses, validate every redirect hop, cap bytes/time/hops and enforce egress policy to
resist DNS rebinding/SSRF.

Privacy: current counts contain no IPs, visitor IDs, referrers, user agents or geography. Hourly counters retained 30
days by worker. Ownership and destination data remain until explicit operator erasure: replace destination with a safe
placeholder and retain reservation tombstone to prevent reuse; backups follow separate expiry and legal hold.
Audit/idempotency stores also contain sensitive references and responses; define erasure process across both before
public launch. No unique visitor approximation or consent flow exists.

Migrations: expand/contract, additive nullable columns, backfill in bounded batches, concurrent indexes via separate
nontransactional migration. One migration job before rollout preferred; startup Flyway is suitable locally but large
production migrations should not block every pod. Rollback application only across compatible schema; forward-fix data
migrations. Test migration with old/new binaries before rolling. Reserve storage for WAL/bloat and autovacuum;
tombstones grow indefinitely.

Backups: enable PITR, encrypt, restrict access, daily restoration to isolated cluster, verify reservation uniqueness and
sampled destinations/idempotency then measure recovery duration. Route only after fencing old primary and flushing
caches. Test secret rotation and issuer outages. JWKS rotation/cache behavior depends on resource-server defaults; do
not rely on an unreachable issuer at cold start.

Metrics: HTTP server and Hikari/JVM metrics supplement explicit counters. Configure histogram publication for
`redirect_latency_seconds` and HTTP timers for p99 panels. Prometheus normalizes the custom timer to
`redirect_latency_seconds_seconds`; dashboard uses that name. No code, URL or tenant metric labels. Alert on 5xx
error-budget burn (14.4x over 1h/5m and 6x over 6h/30m), p99 >20ms with sufficient sample volume, cache errors,
analytics loss, pool saturation, replication lag and disk growth. Synthetic regional probes must include ingress and
verify Location without following destination. Use trace sampling and redact URLs/tokens in logs. OTel bridge is
present; configure exporter endpoint and validate traces before release.

Load shedding and cache stampede admission control are external prerequisites, not hidden application guarantees. Fixed
DB pool 32 is a starting bound, not tuning evidence; sum pools across replicas must fit the database budget. Redis uses
100ms timeout and 200ms connect deadline, DB socket 2s/statement 1.5s/lock 1s, pool wait 1s. Virtual threads do not
remove these bottlenecks. Graceful shutdown allows 20s; analytics queue is not durable or drained transactionally.
