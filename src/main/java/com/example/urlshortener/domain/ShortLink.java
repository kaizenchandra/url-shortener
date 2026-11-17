package com.example.urlshortener.domain;

import java.time.Instant;
import java.util.UUID;

public record ShortLink(UUID id, String tenant, String owner, String domain, String code, String destination,
                        Status status, Instant createdAt, Instant expiresAt, long version) {
    public boolean expired() {
        return expiresAt != null && !expiresAt.isAfter(Instant.now());
    }

    public void transition(Status next, boolean admin) {
        if (status == Status.DELETED || (status == Status.BLOCKED && !admin) || (next == Status.BLOCKED && !admin))
            throw new IllegalArgumentException("Forbidden lifecycle transition");
    }

    public enum Status {ACTIVE, DISABLED, BLOCKED, DELETED}
}
