# ADR 1: Correctness baseline

Accepted: modular monolith, Spring JDBC rather than JPA for explicit conflict handling, ten-character secure random
Base62, primary unique domain/code constraint, never-reused reservations, tenant-serialized create transactions,
versioned updates, short-TTL cache, best-effort aggregate analytics. Kafka is deferred until durable event volume
justifies its operational burden.

Consequences: tenant hotspot limits write throughput; primary fallback requires shedding; no exactly-once or lossless
clicks; optional strict redirect mode trades cache speed for stronger revocation. Global scale is an extension needing
different storage and analytics topology, not a property conferred by Kubernetes alone.
