CREATE TABLE tenants
(
    id        varchar(128) PRIMARY KEY,
    max_links bigint NOT NULL DEFAULT 10000 CHECK (max_links > 0)
);
CREATE TABLE link_owners
(
    tenant  varchar(128) REFERENCES tenants (id),
    subject varchar(128),
    PRIMARY KEY (tenant, subject)
);
CREATE TABLE custom_domains
(
    domain   varchar(253) PRIMARY KEY,
    tenant   varchar(128) REFERENCES tenants (id),
    verified boolean NOT NULL DEFAULT false
);
CREATE TABLE short_links
(
    id          uuid PRIMARY KEY,
    tenant      varchar(128)            NOT NULL,
    owner       varchar(128)            NOT NULL,
    domain      varchar(253)            NOT NULL,
    code        varchar(32) COLLATE "C" NOT NULL,
    destination varchar(4096)           NOT NULL,
    status      varchar(16)             NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED', 'BLOCKED', 'DELETED')),
    created_at  timestamptz             NOT NULL DEFAULT now(),
    expires_at  timestamptz,
    version     bigint                  NOT NULL DEFAULT 0,
    FOREIGN KEY (tenant, owner) REFERENCES link_owners (tenant, subject),
    UNIQUE (domain, code)
);
CREATE INDEX links_owner ON short_links (tenant, owner, created_at DESC, id);
CREATE TABLE idempotency_records
(
    tenant      varchar(128) REFERENCES tenants (id),
    owner       varchar(128) NOT NULL,
    key         varchar(128),
    fingerprint char(64)     NOT NULL,
    link_id     uuid REFERENCES short_links (id),
    response    text         NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant, owner, key)
);
CREATE TABLE link_audit_events
(
    id      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    link_id uuid REFERENCES short_links (id),
    actor   varchar(128) NOT NULL,
    action  varchar(32)  NOT NULL,
    at      timestamptz  NOT NULL DEFAULT now()
);
CREATE TABLE click_counts
(
    link_id uuid REFERENCES short_links (id),
    hour    timestamptz NOT NULL,
    clicks  bigint      NOT NULL,
    PRIMARY KEY (link_id, hour)
);
