package com.example.urlshortener.application;

import com.example.urlshortener.domain.Codes;
import com.example.urlshortener.domain.Destination;
import com.example.urlshortener.domain.ShortLink;
import com.example.urlshortener.infrastructure.persistence.Links;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class Management {
    private final io.micrometer.core.instrument.MeterRegistry metrics;
    private final Links links;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final String domain;
    private final String publicBaseUrl;

    public Management(io.micrometer.core.instrument.MeterRegistry metrics, Links links, TransactionTemplate tx, ObjectMapper json, @org.springframework.beans.factory.annotation.Value("${shortener.domain}") String domain, @org.springframework.beans.factory.annotation.Value("${shortener.public-base-url:}") String publicBaseUrl) {
        this.metrics = metrics;
        this.links = links;
        this.tx = tx;
        this.json = json;
        this.domain = domain;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    public static ResponseStatusException error(int status, String message) {
        return new ResponseStatusException(HttpStatus.valueOf(status), message);
    }

    public ShortLink owned(UUID id, Identity i) {
        var l = links.get(id).orElseThrow(() -> error(404, "Link not found"));
        if (!l.tenant().equals(i.tenant()) || (!i.admin() && !l.owner().equals(i.subject())))
            throw error(404, "Link not found");
        return l;
    }

    public String create(Create c, String key, Identity i) {
        new Destination(c.originalUrl());
        if (c.customAlias() != null) Codes.alias(c.customAlias());
        if (c.expiresAt() != null && !c.expiresAt().isAfter(Instant.now()))
            throw new IllegalArgumentException("Expiration must be in future");
        if (key != null && !key.matches("[A-Za-z0-9_.:-]{1,128}"))
            throw new IllegalArgumentException("Invalid idempotency key");
        String d = c.domain() == null ? domain : c.domain().toLowerCase(Locale.ROOT);
        return tx.execute(t -> {
            var db = links.database();
            db.update("INSERT INTO tenants(id) VALUES (?) ON CONFLICT DO NOTHING", i.tenant());
            long quota = db.queryForObject("SELECT max_links FROM tenants WHERE id=? FOR UPDATE", Long.class, i.tenant());
            db.update("INSERT INTO link_owners VALUES (?,?) ON CONFLICT DO NOTHING", i.tenant(), i.subject());
            String fingerprint = hash(c);
            if (key != null) {
                var old = db.queryForList("SELECT fingerprint,response FROM idempotency_records WHERE tenant=? AND owner=? AND key=?", i.tenant(), i.subject(), key);
                if (!old.isEmpty()) {
                    if (!fingerprint.equals(old.getFirst().get("fingerprint")))
                        throw error(409, "Idempotency payload mismatch");
                    return (String) old.getFirst().get("response");
                }
            }
            if (!d.equals(domain) && db.queryForObject("SELECT count(*) FROM custom_domains WHERE domain=? AND tenant=? AND verified", Long.class, d, i.tenant()) == 0)
                throw error(403, "Domain not verified");
            if (db.queryForObject("SELECT count(*) FROM short_links WHERE tenant=?", Long.class, i.tenant()) >= quota)
                throw error(429, "Tenant lifetime creation quota reached");
            UUID id = UUID.randomUUID();
            String code = null;
            for (int n = 0; n < 8; n++) {
                String candidate = c.customAlias() == null ? Codes.random() : c.customAlias();
                int inserted = db.update("INSERT INTO short_links(id,tenant,owner,domain,code,destination,status,expires_at) VALUES (?,?,?,?,?,?,'ACTIVE',?) ON CONFLICT(domain,code) DO NOTHING", id, i.tenant(), i.subject(), d, candidate, c.originalUrl(), c.expiresAt() == null ? null : Timestamp.from(c.expiresAt()));
                if (inserted == 1) {
                    code = candidate;
                    break;
                }
                metrics.counter("short_code_collisions_total").increment();
                if (c.customAlias() != null) throw error(409, "Alias already reserved");
            }
            if (code == null) throw error(503, "Code generation exhausted; retry later");
            var l = links.get(id).orElseThrow();
            String response;
            try {
                response = json.writeValueAsString(new Created(l.id(), l.code(), (d.equals(domain) && !publicBaseUrl.isBlank() ? publicBaseUrl : "https://" + d) + "/" + l.code(), l.destination(), l.createdAt(), l.expiresAt(), l.version()));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            if (key != null)
                db.update("INSERT INTO idempotency_records(tenant,owner,key,fingerprint,link_id,response) VALUES (?,?,?,?,?,?)", i.tenant(), i.subject(), key, fingerprint, id, response);
            db.update("INSERT INTO link_audit_events(link_id,actor,action) VALUES (?,?,'CREATE')", id, i.subject());
            return response;
        });
    }

    private String hash(Create c) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(c).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public ShortLink patch(UUID id, Patch p, Identity i) {
        if (p.version() == null) throw new IllegalArgumentException("version required");
        if (p.originalUrl() != null) new Destination(p.originalUrl());
        return tx.execute(t -> {
            var l = owned(id, i);
            var status = p.status() == null ? l.status() : p.status();
            l.transition(status, i.admin());
            int n = links.database().update("UPDATE short_links SET destination=?,status=?,expires_at=?,version=version+1 WHERE id=? AND version=?", p.originalUrl() == null ? l.destination() : p.originalUrl(), status.name(), p.expiresAt() == null ? (l.expiresAt() == null ? null : Timestamp.from(l.expiresAt())) : Timestamp.from(p.expiresAt()), id, p.version());
            if (n != 1) throw error(409, "Version conflict");
            links.database().update("INSERT INTO link_audit_events(link_id,actor,action) VALUES (?,?,?)", id, i.subject(), status.name());
            return links.get(id).orElseThrow();
        });
    }

    public List<ShortLink> list(Identity i, int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 100)
            throw new IllegalArgumentException("Invalid pagination");
        return links.database().query("SELECT * FROM short_links WHERE tenant=? AND owner=? ORDER BY created_at DESC,id LIMIT ? OFFSET ?", Links.MAPPER, i.tenant(), i.subject(), size, page * size);
    }

    public record Identity(String tenant, String subject, boolean admin) {
    }

    public record Create(String originalUrl, String customAlias, Instant expiresAt, String domain) {
    }

    public record Patch(String originalUrl, ShortLink.Status status, Instant expiresAt, Long version) {
    }

    public record Created(UUID id, String shortCode, String shortUrl, String originalUrl, Instant createdAt,
                          Instant expiresAt, long version) {
    }
}
