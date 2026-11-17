# Reproducible load tests

No throughput measurements have been collected. k6 and a distributed generator fleet are external prerequisites. Seed
real codes via authenticated API and supply CODES. Do not use unknown default code for a successful redirect benchmark.

```sh
k6 run -e BASE_URL=http://localhost:8080 -e CODES=actualCode -e RPS=10000 benchmarks/redirect.js
# Repeat RPS=100000, 500000, 1000000, 5000000, 10000000 with distributed generators.
k6 run -e TOKEN="$TOKEN" -e RPS=1 benchmarks/create.js
```

Script uses arrival-rate scheduling and fails on dropped iterations: offered rate is not evidence of achieved rate. For
distributed execution split total RPS across generators and merge histograms, never average p99s. Creation quota/rate
limits require explicitly provisioned benchmark tenants and approved configuration changes for higher write rates; a
single default tenant cannot generate 10k/s.

Use one code for hot key, many seeded codes for broad popularity; test expired links separately with 410 success
semantics. Two-second TTL naturally exercises misses; strict mode measures all-primary workload. Mixed read/write runs
execute both scripts concurrently. Analytics enabled/disabled comparison requires separate deployment/configuration or
code change (no runtime toggle currently). Use Toxiproxy/network policies to degrade Redis/DB; failover and pod
termination need staging infrastructure. Capture achieved successful redirects/s, status/error rates, p50/p95/p99, k6
dropped iterations, pod CPU/RSS/GC, Redis latency/hits, PostgreSQL QPS/pool waits, network egress and event drops. Kafka
lag is applicable only after broker implementation.

Keep generator and app resources, region/AZ placement, data-set size, destination length, connection reuse, TLS and run
duration in result metadata. Warm-up and steady-state intervals must be distinct. Do not infer production capacity from
a laptop or loopback-only test.
