package com.example.urlshortener.infrastructure.cache;

import com.example.urlshortener.domain.ShortLink;
import com.example.urlshortener.infrastructure.persistence.Links;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedirectCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Links links;
    private final MeterRegistry metrics;
    private final boolean strict;

    public RedirectCache(StringRedisTemplate redis, ObjectMapper json, Links links, MeterRegistry metrics, @org.springframework.beans.factory.annotation.Value("${shortener.strict:false}") boolean strict) {
        this.redis = redis;
        this.json = json;
        this.links = links;
        this.metrics = metrics;
        this.strict = strict;
    }

    public ShortLink resolve(String domain, String code) {
        String key = "shortlink:" + domain + ":" + code;
        if (!strict) try {
            String v = redis.opsForValue().get(key);
            if (v != null) {
                metrics.counter("redirect_cache_hits_total").increment();
                return json.readValue(v, ShortLink.class);
            }
        } catch (Exception e) {
            metrics.counter("redirect_cache_errors_total").increment();
        }
        metrics.counter("redirect_cache_misses_total").increment();
        long started = System.nanoTime();
        var l = links.resolve(domain, code).orElse(null);
        // Never refresh the TTL with data from an arbitrarily delayed database read.
        long remaining = 2000 - (System.nanoTime() - started) / 1_000_000;
        if (!strict && l != null && remaining > 0) try {
            redis.opsForValue().set(key, json.writeValueAsString(l), Duration.ofMillis(remaining));
        } catch (Exception e) {
            metrics.counter("redirect_cache_errors_total").increment();
        }
        return l;
    }
}
