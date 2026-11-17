# Capacity estimates (not measurements)

Assume average destination 500 B, row 800 B including metadata/overhead, indexes 250 B/link, cached object 1 KiB
(allocator/key/JSON estimate), HTTP redirect 800 B including headers, click event 200 B before compression. WAL,
replicas, bloat and backups are additional. Actual lengths and PostgreSQL fillfactor must be measured.

| Stored links   | Row bytes GB | Index GB | Redis for hottest 1% GB |
|----------------|-------------:|---------:|------------------------:|
| 100,000,000    |           80 |       25 |                   1.024 |
| 1,000,000,000  |          800 |      250 |                   10.24 |
| 10,000,000,000 |         8000 |     2500 |                   102.4 |

| Redirect RPS | 90% miss RPS | 99% miss RPS | 99.9% miss RPS | Egress Gbit/s | Full event TB/day |
|-------------:|-------------:|-------------:|---------------:|--------------:|------------------:|
|    1,000,000 |      100,000 |       10,000 |          1,000 |           6.4 |             17.28 |
|    5,000,000 |      500,000 |       50,000 |          5,000 |            32 |              86.4 |
|   10,000,000 |    1,000,000 |      100,000 |         10,000 |            64 |             172.8 |

DB reads = RPS × (1-hit ratio); writes 10k–100k/s create 8–80 MB/s row bytes plus indexes, WAL, audit, idempotency and
replica traffic. At 100:1, 1M reads imply 10k creates, 10M imply 100k. These rates require sharding and operational
validation beyond this implementation. TTL two seconds may greatly reduce hit ratio for broad sparse workloads. Redis
hottest 1% memory is a planning illustration, not a way to infer 99% hit ratio without trace replay.

Analytics raw growth = RPS × 200 × 86400 bytes/day. Three Kafka replicas would triple retained bytes before compression,
plus cross-AZ bandwidth; Kafka is not shipped. Current aggregate growth upper bound is active link-hours × row/index
size, and queue capacity is only 10k clicks per pod. Current worker drains at most 1000 every 250 ms plus DB time:
nominal ceiling below 4000 events/s/pod, explicitly unsuitable for million-RPS analytics completeness. Drop rate must be
monitored. A Kafka ingestion fleet or sampled analytics is required at target scale.

Instance count = ceil(regional RPS / measured sustainable RPS per pod / target utilization), then add AZ failure
headroom. No measured per-pod rate is available. Cross-region replication is WAL bytes/s × remote replicas; async
replication adds nonzero RPO. Include 3x database copies, backup retention and rebuild free space in budgeting. Network
egress, cross-AZ Redis calls, primary write IOPS, managed HA premiums and analytics retention dominate cost. No vendor
capacity or price is assumed.

Zipf skew improves hit ratio but causes hot-key contention; use replicated local caches/edge only when invalidation
policy allows. Viral bursts can exceed one Redis shard's network budget even at high hits. Short TTLs induce miss waves;
add bounded single-flight and TTL jitter only after validating consistency. Strict mode sends every redirect to primary
and cannot plausibly achieve the scale target on one database.

| Base62 length |                Capacity |
|---------------|------------------------:|
| 6             |          56,800,235,584 |
| 7             |       3,521,614,606,208 |
| 8             |     218,340,105,584,896 |
| 10            | 839,299,365,868,340,224 |

Ten characters provide about 59.5 bits. At ten billion retained assignments, occupancy is about 1.19e-8; a collision
somewhere is nevertheless virtually certain, so database checks remain mandatory.
