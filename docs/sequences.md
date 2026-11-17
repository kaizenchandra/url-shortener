# Sequence flows

All database calls are network calls. BEGIN/COMMIT denotes a synchronous application transaction; Redis calls are
synchronous except the analytics worker, which runs independently. Network and pool wait dominate normal latency.

## Create

```mermaid
sequenceDiagram
 participant C as Client
 participant A as Application
 participant D as Dependencies
 C->>A: Validate JWT, URL, rate limit
 A->>D: BEGIN; lock tenant; quota; insert random code; insert idempotency response and audit; COMMIT
 D-->>A: Success or bounded failure
 A-->>C: 201 after commit
```

Unique constraint; bounded 8 attempts; DB latency dominates.

## Redirect

```mermaid
sequenceDiagram
 participant C as Client
 participant A as Application
 participant D as Dependencies
 C->>A: Validate host/code
 A->>D: Redis GET; on miss SELECT primary; SET with remaining TTL
 D-->>A: Success or bounded failure
 A-->>C: 302/307 after state/expiry check; queue.offer async
```

Cache failure falls back; DB failure 503; queue failure drops event.

## Custom alias

```mermaid
sequenceDiagram
 participant C as Client
 participant A as Application
 participant D as Dependencies
 C->>A: Validate reserved words and verified domain
 A->>D: BEGIN; tenant lock; INSERT ON CONFLICT; COMMIT
 D-->>A: Success or bounded failure
 A-->>C: 201 or 409
```

Atomic domain/code uniqueness prevents double reservation.

## Update destination

```mermaid
sequenceDiagram
 participant C as Client
 participant A as Application
 participant D as Dependencies
 C->>A: Validate owner and version
 A->>D: BEGIN; SELECT owner; UPDATE WHERE version; audit; COMMIT
 D-->>A: Success or bounded failure
 A-->>C: 200 new version
```

Concurrent update gets 409; old cache ages out.

## Expire

```mermaid
sequenceDiagram
 participant C as Client
 participant A as Application
 participant D as Dependencies
 C->>A: Time advances past expiresAt
 A->>D: No write needed; cache snapshot includes expiresAt
 D-->>A: Success or bounded failure
 A-->>C: 410 at next resolution
```

Checked locally on every request; clock synchronization required.

## Block malicious link

```mermaid
sequenceDiagram
 participant C as Client
 participant A as Application
 participant D as Dependencies
 C->>A: Authenticate tenant admin
 A->>D: BEGIN; conditional UPDATE BLOCKED; audit; COMMIT
 D-->>A: Success or bounded failure
 A-->>C: 200 management; future redirects 451 after visibility
```

Strict mode primary lookup; in-flight requests can finish.

## Cache propagation

```mermaid
sequenceDiagram
 participant C as Client
 participant A as Application
 participant D as Dependencies
 C->>A: Commit destination or status change
 A->>D: No eviction; cached snapshot expires after TTL
 D-->>A: Success or bounded failure
 A-->>C: Subsequent miss reads primary
```

Soft staleness bound; network delay and in-flight work remain.

## Analytics

```mermaid
sequenceDiagram
 participant C as Client
 participant A as Application
 participant D as Dependencies
 C->>A: Redirect handler queue.offer UUID
 A->>D: Async drain up to 1000; aggregate; upsert hourly
 D-->>A: Success or bounded failure
 A-->>C: Redirect returns independently
```

Queue overflow/process crash/flush failure loses counts.

## Cache outage

```mermaid
sequenceDiagram
 participant C as Client
 participant A as Application
 participant D as Dependencies
 C->>A: Redis GET timeout
 A->>D: Primary SELECT within bounded DB pool
 D-->>A: Success or bounded failure
 A-->>C: Redirect if DB healthy; otherwise 503
```

External load shedding required; no infinite retries.

## Database failure

```mermaid
sequenceDiagram
 participant C as Client
 participant A as Application
 participant D as Dependencies
 C->>A: Cache miss or management write
 A->>D: DB timeout/transaction rollback
 D-->>A: Success or bounded failure
 A-->>C: 503; valid cache hits can continue
```

Failover fencing and synchronous standby required for zero acknowledged loss.
