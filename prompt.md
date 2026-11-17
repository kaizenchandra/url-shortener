# MASTER PROMPT: Design and Develop a Production-Grade Google Drive / Dropbox-like File Storage and Synchronization System

## 1. ROLE AND ENGINEERING RESPONSIBILITIES

Act as a **Principal Software Engineer, Distinguished Distributed Systems Engineer, Solution Architect, Java 21 Expert,
Cloud Storage Architect, and Site Reliability Engineer** with 15+ years of experience designing and operating
large-scale distributed systems.

Your task is to **design, develop, test, benchmark, document, and deploy a complete Google Drive / Dropbox-like File
Storage and Synchronization System** using Java 21 and Spring Boot.

Approach this as a real enterprise engineering project, not merely a system design interview exercise.

Your responsibilities include:

1. Requirements analysis and architecture design
2. High-Level Design (HLD)
3. Low-Level Design (LLD)
4. Database and object storage design
5. Distributed file upload and download
6. Resumable and multipart transfers
7. File and folder management
8. File synchronization across devices
9. File versioning and conflict resolution
10. Sharing and access control
11. Reliability and disaster recovery
12. Observability and performance engineering
13. Automated testing and verification
14. Containerization and cloud deployment
15. Complete source code and documentation

Do not stop after architecture diagrams, pseudocode, or illustrative examples.

**Deliver an executable, maintainable, tested implementation with complete source files, infrastructure configuration,
and documentation.**

### Mandatory engineering principles

- Use Java 21 and a verified compatible Spring Boot release.
- Follow existing repository conventions if a project is provided.
- Apply DDD, SOLID, Clean Architecture, and Hexagonal Architecture pragmatically.
- Prefer cohesive modules over unnecessary microservices.
- Use clear domain boundaries and explicit interfaces.
- Preserve correctness and data integrity across failures.
- Treat authorization, concurrency, transactions, idempotency, retries, and timeouts as first-class concerns.
- Do not introduce technologies without a concrete architectural justification.
- Avoid hardcoded secrets and sensitive information in logs.
- Never fabricate executed tests, performance measurements, or deployment results.
- Explain important design decisions and trade-offs.
- Make reasonable implementation decisions independently and document assumptions.

---

# PART A — PRODUCT REQUIREMENTS

## 2. PRIMARY OBJECTIVE

Build a distributed file storage and synchronization platform similar to Google Drive or Dropbox.

Users must be able to:

- Upload files
- Download files
- Create folders
- Rename and move files
- Delete and restore files
- Synchronize files across devices
- View file versions
- Restore previous versions
- Share files and folders
- Manage access permissions
- Search files
- View storage usage
- Resume interrupted uploads
- Recover from transient failures

Example scenario:

A user uploads a 5 GB video from a desktop application.

The system should:

1. Authenticate the user.
2. Validate storage quota and permissions.
3. Create a resumable upload session.
4. Divide or accept the file in multiple parts.
5. Transfer parts directly to object storage where appropriate.
6. Validate part integrity.
7. Finalize the multipart upload.
8. Atomically publish the new file version in metadata.
9. Update storage accounting.
10. Generate synchronization events.
11. Notify other registered devices.
12. Allow another device to download the new file.
13. Recover correctly if an upload or notification fails.

The design must work for both small files and multi-gigabyte files.

---

## 3. FUNCTIONAL REQUIREMENTS

Implement the following core features.

### 3.1 Identity and user management

- User authentication
- User profiles
- User-owned storage namespace
- Tenant or organization support
- Device registration
- Storage quotas
- User access control
- Account deactivation

Use Spring Security and standards-based OAuth2/OIDC integration where appropriate.

### 3.2 File and folder operations

Support:

- Upload
- Download
- List
- Rename
- Move
- Copy
- Delete
- Restore
- Search
- Folder creation
- Folder hierarchy traversal
- File metadata inspection

Define API contracts and transaction semantics for every operation.

### 3.3 Large-file handling

Support:

- Multipart uploads
- Chunked uploads
- Resumable transfers
- Parallel chunk uploads
- Upload cancellation
- Upload expiration
- Upload status inspection
- Part-level checksums
- Final file integrity verification

Avoid buffering entire large files in JVM heap.

### 3.4 File synchronization

Synchronize file and folder changes between:

- Web application
- Desktop client
- Mobile client
- Multiple devices belonging to the same user

Support:

- Incremental synchronization
- Change cursors
- Offline changes
- Conflict detection
- Conflict resolution
- Reconnection recovery
- Duplicate event handling
- Deleted-file propagation
- Renamed/moved-file propagation

### 3.5 File versioning

Support:

- Immutable file versions
- Version metadata
- Previous-version download
- Version restoration
- Version retention policies
- Concurrent modification detection
- Optimistic locking
- Version-aware cache invalidation

### 3.6 Sharing

Support:

- Private files
- User-to-user sharing
- Folder sharing
- Read-only access
- Edit access
- Revocation
- Expiring share links
- Password-protected sharing where justified
- Public link sharing subject to policy

Implement inherited folder permissions with explicit precedence rules.

### 3.7 Search

Support:

- Filename search
- File type filtering
- Owner filtering
- Date-range filtering
- Folder-scoped search
- Pagination

Evaluate PostgreSQL indexes before introducing Elasticsearch or OpenSearch.

### 3.8 Background operations

Consider asynchronous tasks for:

- Virus scanning
- File preview generation
- Thumbnail generation
- Media metadata extraction
- Search indexing
- Audit events
- Notification dispatch
- Orphaned object cleanup
- Expired upload cleanup

Introduce Kafka only where durable asynchronous processing or independent scaling justifies it.

---

# PART B — NON-FUNCTIONAL REQUIREMENTS

## 4. SCALE, PERFORMANCE, AND AVAILABILITY

Use these as design targets requiring explicit validation.

| Dimension                     | Initial Target                                        |
|-------------------------------|-------------------------------------------------------|
| Registered users              | 100 million                                           |
| Daily active users            | 10 million                                            |
| Stored files                  | 10 billion                                            |
| Total logical storage         | 10 PB or more                                         |
| Concurrent upload sessions    | 100,000                                               |
| Metadata API throughput       | 100,000 requests/second                               |
| Peak synchronization activity | 1 million change deliveries/second                    |
| Metadata read latency         | p99 below 50 ms within a region                       |
| Availability                  | 99.99% target for core APIs                           |
| Large-file transfers          | Streaming/resumable                                   |
| File versioning               | Configurable retention                                |
| Deployment                    | Multi-AZ, with multi-region extension                 |
| Data durability               | Explicit object-store and metadata durability targets |
| Recovery                      | Defined RPO and RTO                                   |

Treat each number as an assumption to verify.

Do not imply that a single Spring Boot service, PostgreSQL instance, or Redis cluster automatically supports these
targets.

Distinguish:

- Logical storage from physical storage
- Metadata traffic from file-transfer bandwidth
- File events from device notification deliveries
- User-facing availability from storage durability
- Average throughput from burst throughput

Define measurable SLOs and workload assumptions.

---

# PART C — HIGH-LEVEL SYSTEM DESIGN

## 5. ARCHITECTURE OPTIONS

Compare three architectures.

### Option A — Modular Monolith

One Spring Boot application contains:

- File metadata management
- Folder management
- Upload orchestration
- Sharing
- Synchronization APIs
- Version management

Object storage stores file bytes independently.

### Option B — Domain-Oriented Services

Separate services where justified:

- Metadata Service
- Upload Service
- Download/Access Service
- Synchronization Service
- Sharing Service
- Notification Service
- Background Processing Workers

### Option C — Globally Distributed Storage Platform

Introduce:

- Regional metadata services
- Regional object storage
- Global traffic routing
- Cross-region replication
- Regional synchronization infrastructure
- Disaster recovery
- Data residency controls

Evaluate each option based on complexity, reliability, scalability, and consistency.

**Choose the simplest architecture that can be implemented correctly while preserving a credible scale-out path.**

Do not create separate microservices merely for presentation purposes.

---

## 6. REFERENCE ARCHITECTURE

Consider the following logical components:

1. Client applications
2. API Gateway
3. Authentication and authorization
4. Metadata Service
5. Upload Session Manager
6. Object Storage Adapter
7. Amazon S3-compatible object storage
8. PostgreSQL metadata database
9. Redis metadata/cache layer
10. Synchronization Change Log
11. Synchronization API
12. Notification infrastructure
13. Kafka event backbone, when justified
14. Background processing workers
15. Search functionality
16. Monitoring and tracing infrastructure

Provide a Mermaid architecture diagram showing:

- Client-to-API communication
- Control plane versus data plane
- Direct-to-object-storage transfers
- Metadata persistence
- Change-log generation
- Event publishing
- Device synchronization
- Background processing
- Failure recovery paths

Explain why large files should generally be transferred directly between the client and object storage using short-lived
scoped credentials or pre-signed URLs rather than proxying all bytes through Spring Boot.

Explain exceptions where proxying is justified.

---

# PART D — CAPACITY PLANNING

## 7. STORAGE AND TRAFFIC ESTIMATION

Calculate and document:

1. New files created per day
2. Average and percentile file sizes
3. Total storage growth per year
4. Metadata row count
5. Metadata index growth
6. Versioning storage overhead
7. Multipart upload temporary storage
8. Upload network bandwidth
9. Download network bandwidth
10. CDN egress
11. PostgreSQL transaction volume
12. Redis cache size
13. Kafka event throughput
14. Synchronization fan-out
15. Background worker throughput

Create scenarios for:

- 1 million users
- 10 million users
- 100 million users
- 1 billion users as a long-term theoretical scale

Use explicit formulas and assumptions.

For example:

`Daily upload bandwidth = daily uploaded bytes / active upload seconds`

`Notification deliveries = committed changes × average subscribed devices`

Consider:

- Skewed file sizes
- Hot shared files
- Many small files
- Folder containing millions of entries
- Large enterprise tenants
- Highly active synchronized folders

Do not fabricate vendor capacity claims.

---

# PART E — METADATA DESIGN

## 8. DATABASE SCHEMA

Use PostgreSQL as the initial metadata source of truth.

Design schemas for:

- users
- devices
- file_nodes
- file_versions
- blobs
- upload_sessions
- upload_parts
- sharing_permissions
- share_links
- storage_quotas
- quota_reservations
- change_log
- outbox_events
- audit_records

Only create tables required by the chosen implementation.

### File metadata

Model:

- File ID
- Owner / tenant ID
- Parent folder ID
- Filename
- Node type
- File status
- Current version
- Size
- Content type
- Checksum
- Created timestamp
- Updated timestamp
- Deleted timestamp
- Optimistic lock version

### Folder hierarchy

Compare:

1. Adjacency list
2. Materialized path
3. Nested sets
4. Closure table

Evaluate:

- Folder traversal
- Subtree moves
- Rename performance
- Permission inheritance
- Cycle prevention
- Large-folder pagination

Select a practical approach and document the trade-offs.

### Data integrity

Enforce:

- Valid parent-child relationships
- No folder cycles
- Deterministic name uniqueness policies
- Correct quota accounting
- Immutable published file versions
- Valid state transitions
- Referential integrity
- Safe concurrent moves and deletes

Implement Flyway migrations and appropriate indexes.

---

# PART F — FILE UPLOAD ARCHITECTURE

## 9. RESUMABLE MULTIPART UPLOAD

Design an end-to-end upload protocol.

Example:

1. `POST /api/v1/uploads`
2. Create upload session
3. Reserve storage quota
4. Initiate multipart upload with object storage
5. Generate scoped part-upload URLs
6. Client uploads parts
7. Client reports or server verifies part completion
8. Validate uploaded parts
9. Complete multipart upload
10. Verify final object metadata and integrity
11. Publish file version
12. Commit quota usage
13. Write synchronization change record
14. Return completed file metadata

### Required APIs

- `POST /api/v1/uploads`
- `GET /api/v1/uploads/{uploadId}`
- `POST /api/v1/uploads/{uploadId}/parts`
- `POST /api/v1/uploads/{uploadId}/complete`
- `POST /api/v1/uploads/{uploadId}/abort`

Define precise request and response schemas.

### Important correctness requirements

- Upload-session idempotency
- Part-number validation
- Multipart part-size constraints
- Missing-part detection
- Duplicate-part handling
- Part checksum verification
- Safe retry behavior
- Expiration
- Cancellation
- Concurrent completion attempts
- Quota reservation
- Object cleanup
- Recovery after partial failure

Do not assume arbitrary chunk sizes are compatible with every object-storage provider.

Verify multipart behavior against the selected provider's documentation.

---

## 10. UPLOAD STATE MACHINE

Design an explicit state machine, such as:

- INITIATED
- UPLOADING
- COMPLETING
- VERIFYING
- COMPLETED
- ABORTING
- ABORTED
- FAILED
- EXPIRED

Define allowed transitions and transaction boundaries.

Analyze these failures:

1. Client crashes halfway through upload.
2. Network disconnects during a part upload.
3. Client retries the same part.
4. Upload completes in object storage but PostgreSQL transaction fails.
5. Metadata commits but client receives a timeout.
6. Upload session expires during processing.
7. Two clients complete the same upload concurrently.
8. Object storage returns an ambiguous completion response.
9. Quota reservation expires unexpectedly.
10. Virus scanning fails after the file is uploaded.

Provide idempotent recovery and reconciliation mechanisms.

Never assume that PostgreSQL and object storage participate in one atomic distributed transaction.

---

# PART G — FILE DOWNLOAD ARCHITECTURE

## 11. SECURE DOWNLOADS

Design:

1. User authentication
2. Permission evaluation
3. File state validation
4. Version selection
5. Download token creation
6. Pre-signed URL issuance
7. Direct object storage download
8. Optional CDN distribution

Support:

- Large-file streaming
- HTTP Range requests
- Partial downloads
- Resume
- Content-Disposition
- File integrity metadata
- Version-aware downloads

Explain how access revocation interacts with existing pre-signed URLs and CDN caches.

Provide bounded URL expiration and policies for immediate revocation scenarios.

Avoid exposing private object-storage buckets publicly.

---

# PART H — FILE SYNCHRONIZATION ENGINE

## 12. SYNCHRONIZATION REQUIREMENTS

Design a robust synchronization protocol supporting multiple devices.

Example:

- Device A uploads `report.pdf`.
- The server commits a new file version.
- A synchronization change is recorded.
- Device B receives a notification.
- Device B fetches changes using its cursor.
- Device B downloads the new file.
- Device B updates its local filesystem.

Support:

- Incremental synchronization
- Initial full synchronization
- Offline-to-online reconciliation
- Cursor pagination
- Change ordering
- Duplicate delivery
- Client retries
- Device restart
- Event replay
- Gap detection
- Deleted-file propagation
- Rename/move propagation

### Critical rule

Treat notifications as hints, not the authoritative change history.

Use a durable, cursor-based server-side change log as the source of synchronization truth.

---

## 13. CHANGE LOG AND CURSOR DESIGN

Evaluate:

- Database-generated sequence IDs
- Transactional change records
- Per-user change streams
- Per-namespace ordering
- Log sequence numbers
- Hybrid logical clocks

Select a design that provides clearly defined ordering guarantees.

Address the important difference between:

- ID allocation order
- Database commit order
- Event publication order
- Client observation order

Do not assume a PostgreSQL sequence automatically represents transaction commit order.

Design a safe cursor protocol where a client cannot permanently miss a committed change because another transaction
allocated an earlier sequence number but committed later.

Possible approaches include serialized per-namespace sequence allocation or a properly designed transactional
publication pipeline.

Define:

- Cursor format
- Cursor durability
- Cursor expiration
- Snapshot boundaries
- Pagination semantics
- Replay behavior
- Duplicate suppression
- Gap recovery

Implement tests for concurrent transactions committing out of order.

---

## 14. CLIENT SYNC API

Implement APIs such as:

- `GET /api/v1/sync/changes?cursor=...`
- `POST /api/v1/devices`
- `GET /api/v1/devices`
- `POST /api/v1/sync/ack`
- `GET /api/v1/sync/snapshot`

Provide example request/response payloads.

Include:

- Cursor
- Change ID
- Entity ID
- Change type
- File version
- Parent ID
- Timestamp
- Tombstone indicator

Ensure APIs are scoped to authenticated users and authorized namespaces.

### Synchronization transport

Compare:

- Polling
- Long polling
- Server-Sent Events
- WebSockets
- Push notifications

Choose a suitable initial strategy.

Explain connection scaling, reconnection, backpressure, and missed-message recovery.

Implement at least one functional synchronization transport.

---

# PART I — CONFLICT RESOLUTION

## 15. CONCURRENT MODIFICATIONS

Consider:

1. Two devices edit the same file while offline.
2. Two users rename the same file.
3. One device deletes a file while another modifies it.
4. A folder is moved while files inside it are being updated.
5. Two devices create files with the same name.
6. A shared file is modified after access is revoked.

Compare:

- Last-write-wins
- Optimistic concurrency control
- Conflict-copy creation
- Explicit user resolution
- CRDT-based collaboration
- Operational transformation

Avoid introducing CRDT or OT complexity for binary file synchronization unless collaborative editing is explicitly
required.

### Recommended baseline

Use version-aware optimistic concurrency checks for metadata and file revisions.

For conflicting binary edits, preserve both versions or create a conflict copy according to a documented policy.

Implement:

- ETag or version-based preconditions
- HTTP 409/412 behavior
- Deterministic conflict handling
- Audit records
- Conflict recovery

Do not silently overwrite concurrent changes.

---

# PART J — FILE VERSIONING AND DEDUPLICATION

## 16. FILE VERSIONING

Implement immutable file versions.

Each published version must reference an immutable object-storage key or immutable content identity.

Support:

- Version creation
- Version listing
- Historical downloads
- Version restore
- Retention
- Garbage collection
- Storage accounting

Ensure a restored version creates a new current version or follows an explicitly documented safe restoration policy.

Avoid overwriting stored blobs in place.

---

## 17. CONTENT DEDUPLICATION

Evaluate:

1. No deduplication
2. Per-user deduplication
3. Per-tenant deduplication
4. Global content-addressable deduplication
5. Chunk-level deduplication

Consider:

- SHA-256 hashing
- Reference counts
- Storage savings
- CPU cost
- Privacy leakage
- Cross-tenant side channels
- Encryption compatibility
- Garbage collection

Implement deduplication only when justified.

Do not assume object-storage ETags are universally reliable cryptographic content hashes.

If implementing deduplication, define a safe reference-counting and garbage-collection protocol.

---

# PART K — SHARING AND ACCESS CONTROL

## 18. SHARING MODEL

Support:

- Owner
- Viewer
- Editor
- Administrator

Define permission inheritance through folder hierarchies.

Implement APIs such as:

- `POST /api/v1/files/{id}/shares`
- `GET /api/v1/files/{id}/shares`
- `DELETE /api/v1/files/{id}/shares/{shareId}`
- `POST /api/v1/files/{id}/share-links`

Address:

- Share token security
- Permission propagation
- Revocation
- Expiration
- Folder inheritance
- Tenant isolation
- Ownership transfer
- Access auditing

### Critical security scenarios

- A user guesses another file ID.
- An editor attempts an owner-only operation.
- A shared folder is moved.
- A permission is revoked during upload.
- A user accesses an old pre-signed URL.
- A file is restored after its original owner loses access.

Enforce authorization server-side for every metadata operation.

Do not rely only on frontend checks.

---

# PART L — SECURITY

## 19. SECURITY REQUIREMENTS

Implement or document:

1. OAuth2/OIDC authentication
2. Spring Security authorization
3. Tenant isolation
4. Object-storage access restrictions
5. Encryption in transit
6. Encryption at rest
7. KMS integration
8. Pre-signed URL scope and expiration
9. Malware scanning
10. Upload content validation
11. File size limits
12. Quota enforcement
13. Rate limiting
14. Audit logging
15. Sensitive metadata protection
16. Secure deletion policy
17. Compliance and retention considerations
18. Replay protection
19. Abuse detection
20. Least-privilege IAM policies

Discuss customer-managed encryption keys as an advanced capability.

Never include production credentials in code.

Do not expose raw internal object keys unnecessarily.

---

# PART M — TECHNOLOGY STACK

## 20. MANDATORY TECHNOLOGIES

Use:

| Component            | Technology                               |
|----------------------|------------------------------------------|
| Language             | Java 21                                  |
| Framework            | Spring Boot, verified compatible version |
| Build                | Maven                                    |
| REST API             | Spring Web MVC                           |
| Security             | Spring Security                          |
| Metadata persistence | PostgreSQL                               |
| Data access          | Spring Data JPA or JDBC, as justified    |
| Database migrations  | Flyway                                   |
| Cache                | Redis / Lettuce                          |
| Object storage       | Amazon S3-compatible API                 |
| Local object storage | MinIO or equivalent                      |
| Messaging            | Apache Kafka when justified              |
| Metrics              | Micrometer                               |
| Tracing              | OpenTelemetry                            |
| Unit testing         | JUnit 5 / Mockito                        |
| Integration testing  | Testcontainers                           |
| Performance testing  | k6 or Gatling                            |
| Containers           | Docker                                   |
| Orchestration        | Kubernetes                               |
| Monitoring           | Prometheus / Grafana                     |

Verify the selected Spring Boot, Spring Cloud, AWS SDK, Testcontainers, and Kafka client versions against official
compatibility information.

Use AWS SDK for Java v2 for S3-compatible operations unless a justified alternative is selected.

Evaluate Java 21 virtual threads for metadata and upload-orchestration endpoints.

Do not assume that virtual threads make file transfers faster.

Avoid proxying large binary payloads through JVM heap.

---

# PART N — PROJECT STRUCTURE

## 21. SOURCE CODE ORGANIZATION

Start with a focused structure such as:

```text
drive-clone/
├── pom.xml
├── README.md
├── docs/
│   ├── architecture.md
│   ├── capacity-planning.md
│   ├── synchronization.md
│   ├── security.md
│   ├── runbook.md
│   └── decisions/
├── src/
│   ├── main/
│   │   ├── java/com/example/drive/
│   │   │   ├── api/
│   │   │   ├── application/
│   │   │   ├── domain/
│   │   │   ├── infrastructure/
│   │   │   │   ├── persistence/
│   │   │   │   ├── objectstorage/
│   │   │   │   ├── cache/
│   │   │   │   └── messaging/
│   │   │   ├── security/
│   │   │   └── configuration/
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/
│   └── test/
│       └── java/
├── sync-client/
├── benchmarks/
├── docker/
├── helm/
└── .github/workflows/
```

Do not leave the client synchronization requirement as an undocumented placeholder.

Implement a minimal functional synchronization client or test harness that:

- Stores its last cursor
- Fetches incremental changes
- Applies file changes locally
- Handles duplicate events
- Detects conflicts
- Recovers after reconnection

The client can be a Java 21 CLI application to avoid unnecessary UI complexity.

---

# PART O — IMPLEMENTATION REQUIREMENTS

## 22. COMPLETE BACKEND IMPLEMENTATION

Build the following capabilities.

### Core metadata

- File/folder CRUD
- Folder hierarchy
- Pagination
- Search
- Optimistic concurrency

### Object storage

- Upload session creation
- Multipart URL generation
- Part verification
- Upload completion
- Abort and cleanup
- Secure download URL generation

### Synchronization

- Durable change log
- Cursor-based incremental sync
- Initial snapshot
- Client registration
- Change notifications
- Conflict resolution

### Versioning

- Immutable blob references
- File revisions
- Version history
- Version restoration
- Retention

### Sharing

- Role-based permissions
- Folder inheritance
- Share links
- Revocation

### Reliability

- Transactional outbox when needed
- Idempotency
- Background reconciliation
- Failure handling
- Metrics
- Health checks

Produce complete Java classes, configurations, migrations, and automated tests.

---

# PART P — DISTRIBUTED TRANSACTIONS

## 23. METADATA AND OBJECT STORAGE CONSISTENCY

Explicitly address why a PostgreSQL transaction cannot atomically commit with an S3 multipart upload.

Design a recoverable workflow.

For example:

1. Create durable upload session.
2. Reserve user quota.
3. Upload parts to object storage.
4. Complete the multipart upload.
5. Verify the resulting object.
6. Commit metadata and change-log records atomically in PostgreSQL.
7. Transition upload session to completed.
8. Reconcile orphaned objects after failures.

Define:

- Idempotent completion
- State transition ownership
- Recovery after ambiguous responses
- Cleanup of incomplete multipart uploads
- Compensation
- Object existence verification
- Quota release
- Duplicate retry handling

Use outbox events to bridge committed database state and asynchronous publication where justified.

Do not use a distributed transaction coordinator without a compelling reason.

---

# PART Q — RELIABILITY AND FAILURE SCENARIOS

## 24. REQUIRED PRODUCTION FAILURE ANALYSIS

Analyze at least these 25 scenarios:

1. Upload interrupted halfway through.
2. Multipart upload completes but metadata commit fails.
3. Metadata commits but response is lost.
4. Two devices update the same file concurrently.
5. Redis becomes unavailable.
6. Object storage becomes unavailable.
7. PostgreSQL primary fails.
8. Kafka consumer crashes.
9. Sync event is delivered twice.
10. Sync notification is lost.
11. Device reconnects after one month offline.
12. Change cursor expires.
13. User deletes a folder containing millions of files.
14. User moves a folder concurrently with child updates.
15. Storage quota is exceeded during parallel uploads.
16. Share permission is revoked during a download.
17. File is deleted while a download is active.
18. Virus scanning identifies a malicious file.
19. Upload session expires during completion.
20. Object storage returns an ambiguous timeout.
21. A regional outage occurs.
22. Client clock differs from server clock.
23. A stale cache exposes old metadata.
24. Database migration conflicts with an older application version.
25. Background garbage collection runs during version restoration.

For each scenario provide:

- Failure mechanism
- Data integrity risk
- User-visible impact
- Detection signals
- Prevention
- Recovery strategy
- Automated test
- Residual risk

---

# PART R — TEST STRATEGY

## 25. UNIT TESTS

Cover:

- Filename validation
- Folder rules
- Folder cycle detection
- Upload state transitions
- Version conflict detection
- Quota reservation
- Permission inheritance
- Cursor handling
- Link expiration
- Checksum validation
- Conflict-copy naming
- Retry and idempotency rules

## 26. INTEGRATION TESTS

Use Testcontainers where practical for:

- PostgreSQL
- Redis
- Kafka
- S3-compatible object storage

Verify:

- Metadata transactions
- Multipart upload workflows
- Object-storage operations
- Permission enforcement
- File versioning
- Outbox publishing
- Incremental synchronization
- Client cursor persistence
- Concurrent requests

## 27. CONCURRENCY TESTS

Specifically verify:

- Parallel uploads against one storage quota
- Concurrent rename/move
- Folder cycle prevention under racing moves
- Concurrent version publication
- Two clients completing the same upload
- Out-of-order transaction commits
- Duplicate notification handling
- Concurrent permission changes
- Cleanup versus file restore

Use real database constraints and transaction tests rather than relying exclusively on mocks.

## 28. FAILURE AND RECOVERY TESTS

Simulate:

- Database connection loss
- Redis timeout
- Object-storage outage
- Partial multipart failures
- Kafka failure
- Pod termination
- Network partition
- Lost API response
- Duplicate message delivery
- Stale sync cursor
- Reconciliation after restart

Document which failure simulations were actually executed.

---

# PART S — PERFORMANCE ENGINEERING

## 29. BENCHMARKS

Create reproducible performance workloads for:

### Metadata

- 1,000 requests/second
- 10,000 requests/second
- 100,000 requests/second

### Uploads

- Small files
- Medium files
- Multi-GB files
- High-concurrency multipart uploads

### Downloads

- Hot files
- Cold files
- Parallel downloads
- Range requests

### Synchronization

- 1,000 devices
- 10,000 devices
- 100,000 devices
- 1 million change deliveries/second as a scale target

Measure:

- p50/p95/p99 latency
- Throughput
- Error rate
- Database CPU
- Connection pool utilization
- Redis hit rate
- Storage API latency
- Kafka consumer lag
- Object-storage bandwidth
- Memory usage
- Garbage collection pauses
- Sync lag
- Event delivery lag
- Recovery duration

Never fabricate results.

If target-scale tests cannot be run, provide scripts and clearly label the performance claims as unverified.

---

# PART T — MULTI-REGION AND DISASTER RECOVERY

## 30. GLOBAL DEPLOYMENT

Design an extension for regional storage and synchronization.

Analyze:

1. Regional object storage
2. Cross-region replication
3. Global metadata routing
4. Single-writer versus multi-writer regions
5. Conflict management
6. Regional caches
7. Data residency
8. Regional failure
9. Recovery point objective
10. Recovery time objective

Compare:

- Single-region multi-AZ
- Active-passive multi-region
- Active-active multi-region

Explain the impact of asynchronous replication on newly uploaded files, version ordering, and share permissions.

Do not claim strong cross-region consistency without an explicit coordination mechanism.

---

# PART U — DEPLOYMENT

## 31. DOCKER AND KUBERNETES

Provide:

- Multi-stage Dockerfile
- Local Docker Compose environment
- PostgreSQL
- Redis
- S3-compatible local object storage
- Kafka, if implemented
- Kubernetes manifests or Helm chart
- Resource requests and limits
- Horizontal Pod Autoscaler
- PodDisruptionBudget
- Readiness/liveness probes
- Graceful shutdown
- Secure configuration management
- CI/CD workflow

For local development, make the full upload/download/synchronization flow reproducible without requiring a paid cloud
account.

Use cloud deployment adapters that can target AWS or GCP.

---

# PART V — OBSERVABILITY

## 32. METRICS AND ALERTING

Expose metrics such as:

- Upload sessions created
- Upload success/failure rate
- Multipart completion latency
- Download authorization latency
- Metadata API latency
- Database transaction failures
- Storage API failures
- Active upload sessions
- Orphaned objects
- Quota reservation failures
- Sync change-log lag
- Device cursor lag
- Notification failures
- Outbox backlog
- Kafka consumer lag
- Reconciliation failures

Implement structured logs and tracing.

Avoid sensitive filenames, file contents, raw share tokens, and personal identifiers in unrestricted logs or metrics.

Provide a Grafana dashboard specification and alert recommendations.

---

# PART W — DELIVERABLES

## 33. REQUIRED OUTPUTS

Produce all of the following.

### Deliverable 1 — Requirements and Assumptions

- Functional requirements
- Non-functional requirements
- User scenarios
- Scale assumptions
- Acceptance criteria

### Deliverable 2 — High-Level Design

- Architecture diagrams
- Deployment topology
- Component responsibilities
- Storage architecture
- Data flows
- Capacity planning

### Deliverable 3 — Low-Level Design

- UML class diagram
- Sequence diagrams
- Domain entities
- Database schema
- Upload state machine
- Sync protocol
- API contracts

### Deliverable 4 — Complete Backend

- Java 21 project
- Spring Boot APIs
- PostgreSQL persistence
- Object storage integration
- Multipart upload handling
- File/folder management
- Sync change log
- Versioning
- Sharing
- Authorization

### Deliverable 5 — Synchronization Client

- Runnable Java 21 client
- Cursor persistence
- Initial snapshot
- Incremental synchronization
- Conflict handling
- Reconnect recovery

### Deliverable 6 — Automated Tests

- Unit tests
- Integration tests
- Concurrency tests
- Security tests
- Failure recovery tests

### Deliverable 7 — Infrastructure

- Docker Compose
- Dockerfile
- Kubernetes or Helm
- CI/CD
- Monitoring configuration

### Deliverable 8 — Performance Testing

- Load-test scripts
- Benchmark setup
- Actual results where executed
- Capacity analysis
- Scalability limitations

### Deliverable 9 — Documentation

- README
- Architecture decisions
- API documentation
- Local setup
- Deployment guide
- Security guide
- Synchronization protocol
- Incident runbook

---

# PART X — IMPLEMENTATION PHASES

## 34. END-TO-END EXECUTION PLAN

Execute the project incrementally.

**Phase 1 — Requirements and Architecture**

Define requirements, choose architecture, design metadata storage, object storage, and synchronization consistency.

**Phase 2 — Core Metadata**

Implement users, files, folders, repositories, migrations, validation, and basic authorization.

**Phase 3 — File Transfer**

Implement resumable multipart upload, secure download, upload lifecycle, quotas, and reconciliation.

**Phase 4 — Versioning and Concurrency**

Implement immutable file versions, optimistic concurrency, restore, and conflict behavior.

**Phase 5 — Synchronization**

Implement durable change logs, cursor APIs, initial snapshots, incremental sync, and a minimal Java synchronization
client.

**Phase 6 — Sharing and Security**

Implement permissions, inheritance, sharing, revocation, and security controls.

**Phase 7 — Asynchronous Processing**

Add outbox-driven events, notifications, and background workers where needed.

**Phase 8 — Reliability**

Implement recovery workflows, timeouts, retries, reconciliation, observability, and operational safeguards.

**Phase 9 — Deployment**

Implement Docker Compose, Kubernetes manifests, and CI/CD.

**Phase 10 — Verification and Optimization**

Run unit/integration/concurrency tests, execute available benchmarks, investigate bottlenecks, and fix significant
defects.

For each phase:

- Implement the required files.
- Run the available build and tests.
- Fix failures before continuing.
- Maintain functional compatibility.
- Preserve existing working code.
- State verification evidence.
- Document unresolved risks.

Do not stop at planning.

If repository and command execution tools are available, create and verify the project directly.

If tools are unavailable, provide complete source files, file paths, and runnable commands. Do not claim commands were
executed.

---

# PART Y — MANDATORY SYSTEM DESIGN REVIEW

## 35. ANSWER THESE ARCHITECTURE QUESTIONS

1. How would you design Google Drive for 100 million users?
2. How do you store petabytes of files?
3. Why separate metadata from binary object storage?
4. How do pre-signed URLs reduce backend bandwidth?
5. How do multipart and resumable uploads work?
6. What happens if multipart completion succeeds but metadata commit fails?
7. How do you prevent lost updates from two devices?
8. How does Dropbox-style incremental synchronization work?
9. How do you ensure clients do not miss changes?
10. How do you handle out-of-order commits?
11. How do you resolve offline editing conflicts?
12. How do you propagate file deletion across devices?
13. How do you implement file versioning?
14. When is content-addressable storage useful?
15. How do you perform safe deduplication?
16. How do you enforce storage quotas under concurrency?
17. How do you move folders containing millions of files?
18. How do you avoid folder hierarchy cycles?
19. How do inherited permissions work?
20. How do you revoke a public share link?
21. What happens to an already-issued download URL after revocation?
22. How do you prevent data leakage across tenants?
23. How do you support multi-region storage?
24. What consistency guarantees exist during cross-region failover?
25. How do you handle a client offline for several months?
26. How do you safely expire old synchronization cursors?
27. How do you synchronize a million changes efficiently?
28. How do you avoid duplicate event processing?
29. How do you recover orphaned storage objects?
30. How do you scale analytics and background processing?
31. How do you perform garbage collection safely?
32. How do you guarantee file integrity?
33. How do you prevent upload abuse and malicious files?
34. How do you measure synchronization lag?
35. How do you benchmark the platform scientifically?

For each answer, discuss correctness, performance, scalability, failure behavior, and operational trade-offs.

---

# PART Z — DEFINITION OF DONE

## 36. ACCEPTANCE CRITERIA

The implementation is considered complete only when:

- The Java 21 application builds.
- Core file/folder APIs work.
- PostgreSQL migrations execute successfully.
- File bytes are stored in S3-compatible object storage.
- Multipart upload and resume behavior are functional.
- Download authorization is enforced.
- Metadata and object-storage reconciliation is implemented.
- File versions are immutable.
- Concurrent modification behavior is tested.
- Storage quota enforcement is concurrency-safe.
- A functional incremental synchronization API exists.
- A runnable synchronization client exists.
- No committed changes are silently skipped by the cursor protocol.
- Folder operations preserve hierarchy integrity.
- Sharing permissions are enforced.
- Expired/revoked access is handled according to documented guarantees.
- Automated tests cover meaningful failure paths.
- Docker-based local execution is reproducible.
- Deployment configuration is provided.
- Operational metrics are exposed.
- Load tests are reproducible.
- Scale claims are supported by actual measurements or explicitly labeled estimates.
- Remaining limitations and production risks are documented.

A passing local build is not sufficient evidence for production readiness.

---

# FINAL EXECUTION INSTRUCTIONS

Treat this as an enterprise-grade product implementation.

Prioritize these outcomes in order:

1. **Data integrity:** No acknowledged file is silently lost or corrupted under the documented durability model.
2. **Synchronization correctness:** Devices can recover a consistent view of committed file and folder changes.
3. **Security:** Files are accessible only to authorized identities.
4. **Resilience:** Interrupted uploads and partial failures recover predictably.
5. **Performance:** Metadata operations are efficient, and large binary transfers bypass unnecessary application hops.
6. **Scalability:** The architecture supports horizontal growth without requiring premature decomposition.
7. **Maintainability:** Code has clear boundaries, focused abstractions, and comprehensive tests.
8. **Observability:** Production failures and synchronization delays are diagnosable.
9. **Reproducibility:** The entire solution can be built and exercised locally.

Start with a concise architecture assessment and choose the recommended implementation approach.

Then execute the implementation phases and produce the complete deliverables.

At the end report:

- What was implemented
- Why the architecture was selected
- Exact build/run commands
- Tests actually executed
- Benchmark results, if measured
- Known limitations
- Remaining production risks

**Do not stop after system design. Build the File Storage and Synchronization System end to end, including backend,
synchronization client, tests, infrastructure, and documentation.**