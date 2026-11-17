# Final architecture review

1. **100 million users.** Separate user count from retained links and peak RPS. Start regional primary and horizontally
   scaled read pools; shard only after measured primary/index/I/O limits. User population alone does not size the
   service.

2. **10 million redirects/s.** Distribute across regions and cache/edge fleets, isolate analytics ingestion and shard
   lookup authority. Derive instance counts from measured per-pod throughput. Current app has no demonstrated capacity
   at this target.

3. **Base62.** Compact alphanumeric path codes avoid Base64 slash/padding and use fewer characters than hex; case
   sensitivity must be preserved by proxies.

4. **Global uniqueness.** Single primary unique domain/code constraint currently arbitrates all assignments.
   Multi-region writes need a global reservation authority or disjoint generated prefixes and a consistent alias
   directory.

5. **Two nodes generate same code.** One insert succeeds; other retries on conflict without overwriting, up to eight
   attempts. No assumption that CSPRNG alone proves uniqueness.

6. **Custom alias collision.** Atomic unique constraint returns 409; reservation survives deletion so old bookmarks
   cannot reach a new owner.

7. **Random or distributed ID.** Random ten-character codes minimize worker/clock coordination and enumeration.
   Snowflake IDs trade retry probability for operational clock and lease discipline.

8. **Read heavy.** Links are created once and shared repeatedly; popularity and viral spikes dominate reads. The 100:1
   assumption requires trace validation.

9. **Redis.** Cache-aside immutable decision snapshot, short TTL, no negative cache. Always check state/expiry; memory
   and eviction policies need workload testing.

10. **Redis failure.** Redirects use primary, writes requiring limiter fail closed. Bound pool and ingress; avoid a
    fallback storm overwhelming the authority.

11. **Stampede.** Current pool caps concurrency, but external shedding is needed. Add single-flight, bounded waiters and
    carefully tested TTL jitter before high-scale use.

12. **Hot links.** Regional/local/edge replication reduces one Redis hot key; stricter invalidation complicates caching.
    Measure key distribution before enabling long TTLs.

13. **Global invalidation.** Current system waits for TTL expiry. Future revocation stream needs per-region
    acknowledgements and freshness leases; worldwide immediate invalidation is not claimed.

14. **Blocked stale redirects.** Strict primary mode avoids cached ACTIVE snapshots. Even that cannot revoke Location
    already sent. Abuse response can stop ingress and drain in-flight work.

15. **301 or 302.** Use 302 by default for mutable links; 307 preserves method. 301/308 suit irrevocably immutable
    destinations with explicit cache policy.

16. **Browser cache.** Permanent redirects can bypass the service indefinitely. No server invalidation guarantees
    browser revalidation; changes and takedowns become ineffective.

17. **Idempotency.** Store fingerprint, response and link in one transaction under tenant lock. Exact key/payload replay
    returns original result; changed payload conflicts.

18. **Durability.** Commit before acknowledgement, fsync/synchronous_commit, fenced synchronous HA standby, PITR and
    restore drills. Async cross-region replication does not imply zero RPO.

19. **Partition/shard PostgreSQL.** Use evidence of index/memory/write/I/O limits. Time partitioning does not
    automatically preserve domain/code uniqueness; routing and reservation directory are necessary.

20. **Migrate to shards.** Introduce stable domain/code routing, backfill with checksums, compare dual reads, fence old
    writes, then cut over shard ownership. Avoid dual writable masters.

21. **Active-active.** Generated regional namespaces are straightforward; custom aliases, quotas and idempotency still
    need consistent ownership or consensus. During partitions reject conflicting writes.

22. **Uniqueness across regions.** Reserve region prefixes permanently or use globally consistent directory. UUID
    primary keys alone do not solve alias uniqueness.

23. **Billions of events.** Use partitioned durable ingestion, batching and columnar aggregate store; dimension
    cardinality and retention dominate cost. Local queue intentionally does not solve this scale.

24. **Asynchronous analytics.** Keeps aggregation latency off redirect path, accepting loss or eventual delivery
    depending on broker choice. Zero-loss append before response adds a network durability boundary.

25. **Duplicate clicks.** Future event UUID dedup within a retained window, transactional aggregate and dedup writes.
    Kafka transactions alone cannot deduplicate all browser retries or external sinks.

26. **Security.** Signed issuer/audience JWT, tenant/owner scope, quotas, bounded payloads and validated schemes. Public
    operation additionally requires reporting/reputation, WAF and incident staffing.

27. **Phishing.** Syntax checks do not identify deception. Integrate reputation feeds and appeal/takedown workflows; no
    destination fetch during redirect. Previews require hardened SSRF controls.

28. **Expired links.** Check timestamp on every request; preserve code reservation. Index/batch archive jobs can scrub
    payloads later without making codes reusable.

29. **Historical links.** Archive destination payloads according to privacy policy while retaining small permanent
    reservation tombstones. Shard the directory when measured scale demands it.

30. **Accurate latency benchmark.** Measure at regional ingress without following redirects, seed known codes,
    distinguish hits/misses and offered/achieved rates, record dropped iterations, CPU/GC/pool/network and generator
    saturation.
