# Executed verification

Environment: macOS ARM64, Oracle Java 21.0.13, Maven 3.10.0. No Docker daemon, PostgreSQL server, Redis server or k6
available locally.

Executed command (sandbox-compatible Maven cache locking workaround):

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Daether.syncContext.named.factory=noop -o -f url-shortener/pom.xml verify
```

Result: Java compilation, test compilation, eight unit tests and executable Spring Boot JAR packaging passed. Five
DomainTest methods and three RedirectTest methods. Mockito uses subclass mock maker to avoid requiring inline
instrumentation in the sandbox. This does not validate real database transaction correctness.

Integration command attempted:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -Daether.syncContext.named.factory=noop -o -Pintegration -f url-shortener/pom.xml test
```

Result: failed Testcontainers startup because `/var/run/docker.sock` is not listening. DatabaseIntegrationTest cases did
not execute. They cover real Flyway schema startup, alias races, idempotency replay/conflict, ownership, optimistic
version conflict, Redis round-trip/TTL propagation and unauthenticated HTTP denial. They remain unverified until run in
Docker-enabled CI.

No live application startup against external services, deployment, benchmark, failover, backup restore or global
propagation test was executed. The definition of done is therefore not fully satisfied. Production release gates include
integration-suite success, full HTTP/JWT authorization contracts, collision injection, idempotency races, update/delete
races, fault drills, external abuse controls, stronger analytics if completeness matters, and realistic capacity
validation.

Earlier compilation errors (ProblemDetail argument type and ambiguous Container import) were fixed. Initial Mockito
inline attach failed in the sandbox; subclass mocking resolved it. No throughput or p99 measurements are claimed.
