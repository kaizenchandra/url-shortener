package com.example.urlshortener.api;

import com.example.urlshortener.application.Management;
import com.example.urlshortener.application.Management.Create;
import com.example.urlshortener.application.Management.Identity;
import com.example.urlshortener.application.Management.Patch;
import com.example.urlshortener.domain.Destination;
import com.example.urlshortener.domain.ShortLink;
import com.example.urlshortener.infrastructure.cache.RedirectCache;
import com.example.urlshortener.infrastructure.messaging.Analytics;
import com.example.urlshortener.infrastructure.persistence.Links;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
public class LinkController {
    private final com.example.urlshortener.security.CreationLimiter limiter;
    private final Management service;
    private final Links links;
    private final RedirectCache cache;
    private final Analytics analytics;
    private final MeterRegistry metrics;
    private final String domain;
    private final int redirect;

    public LinkController(com.example.urlshortener.security.CreationLimiter limiter, Management service, Links links, RedirectCache cache, Analytics analytics, MeterRegistry metrics, @org.springframework.beans.factory.annotation.Value("${shortener.domain}") String domain, @org.springframework.beans.factory.annotation.Value("${shortener.redirect-status:302}") int redirect) {
        this.limiter = limiter;
        this.service = service;
        this.links = links;
        this.cache = cache;
        this.analytics = analytics;
        this.metrics = metrics;
        this.domain = domain;
        if (redirect != 302 && redirect != 307) throw new IllegalArgumentException("Redirect must be 302 or 307");
        this.redirect = redirect;
    }

    private Identity identity(Jwt jwt) {
        String tenant = jwt.getClaimAsString("tenant_id"), subject = jwt.getSubject();
        if (tenant == null || subject == null || tenant.length() > 128 || subject.length() > 128)
            throw Management.error(403, "Invalid identity claims");
        return new Identity(tenant, subject, jwt.getClaimAsStringList("roles") != null && jwt.getClaimAsStringList("roles").contains("admin"));
    }

    @PostMapping(value = "/api/v1/links", produces = "application/json")
    public ResponseEntity<String> create(@RequestBody Create c, @RequestHeader(value = "Idempotency-Key", required = false) String key, @AuthenticationPrincipal Jwt jwt) {
        var i = identity(jwt);
        limiter.check(i.tenant(), i.subject());
        metrics.counter("url_creation_requests_total").increment();
        try {
            return ResponseEntity.status(201).body(service.create(c, key, i));
        } catch (RuntimeException e) {
            metrics.counter("url_creation_failures_total").increment();
            throw e;
        }
    }

    @GetMapping("/api/v1/links/{id}")
    public ShortLink get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.owned(id, identity(jwt));
    }

    @GetMapping("/api/v1/links")
    public List<ShortLink> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal Jwt jwt) {
        return service.list(identity(jwt), page, size);
    }

    @PatchMapping("/api/v1/links/{id}")
    public ShortLink patch(@PathVariable UUID id, @RequestBody Patch p, @AuthenticationPrincipal Jwt jwt) {
        return service.patch(id, p, identity(jwt));
    }

    @DeleteMapping("/api/v1/links/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam long version, @AuthenticationPrincipal Jwt jwt) {
        service.patch(id, new Patch(null, ShortLink.Status.DELETED, null, version), identity(jwt));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/links/{id}/analytics")
    public Object analytics(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.owned(id, identity(jwt));
        return links.database().queryForList("SELECT hour,clicks FROM click_counts WHERE link_id=? ORDER BY hour DESC LIMIT 720", id);
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code, jakarta.servlet.http.HttpServletRequest request) {
        var sample = io.micrometer.core.instrument.Timer.start(metrics);
        try {
            metrics.counter("redirect_requests_total").increment();
            if (!code.matches("[a-zA-Z0-9_-]{1,32}")) throw Management.error(404, "Unknown link");
            String host = request.getServerName().toLowerCase(Locale.ROOT);
            var l = cache.resolve(host, code);
            if (l == null) {
                metrics.counter("redirect_not_found_total").increment();
                throw Management.error(404, "Unknown link");
            }
            if (l.expired() || l.status() != ShortLink.Status.ACTIVE)
                throw Management.error(l.status() == ShortLink.Status.BLOCKED ? 451 : 410, "Link unavailable");
            new Destination(l.destination());
            analytics.click(l.id());
            return ResponseEntity.status(redirect).header("Location", l.destination()).header("Cache-Control", "no-store").build();
        } finally {
            sample.stop(metrics.timer("redirect_latency_seconds"));
        }
    }
}
