package com.example.urlshortener;

import com.example.urlshortener.api.LinkController;
import com.example.urlshortener.application.Management;
import com.example.urlshortener.domain.ShortLink;
import com.example.urlshortener.infrastructure.cache.RedirectCache;
import com.example.urlshortener.infrastructure.messaging.Analytics;
import com.example.urlshortener.infrastructure.persistence.Links;
import com.example.urlshortener.security.CreationLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RedirectTest {
    ShortLink link(ShortLink.Status status, Instant expiration) {
        return new ShortLink(UUID.randomUUID(), "t", "u", "localhost", "abc", "https://example.com", status, Instant.now(), expiration, 0);
    }

    @Test
    void redisFailureFallsBackButDatabaseFailureDoesNotBecomeNotFound() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RuntimeException("timeout"));
        var links = mock(Links.class);
        var l = link(ShortLink.Status.ACTIVE, null);
        when(links.resolve("localhost", "abc")).thenReturn(Optional.of(l));
        var cache = new RedirectCache(redis, new ObjectMapper().findAndRegisterModules(), links, new SimpleMeterRegistry(), false);
        assertEquals(l, cache.resolve("localhost", "abc"));
        when(links.resolve("localhost", "abc")).thenThrow(new IllegalStateException("database failed"));
        assertThrows(IllegalStateException.class, () -> cache.resolve("localhost", "abc"));
    }

    @Test
    void cacheRoundTripAndStrictBypass() throws Exception {
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        var json = new ObjectMapper().findAndRegisterModules();
        var l = link(ShortLink.Status.ACTIVE, null);
        when(ops.get("shortlink:localhost:abc")).thenReturn(json.writeValueAsString(l));
        var links = mock(Links.class);
        var meter = new SimpleMeterRegistry();
        assertEquals(l, new RedirectCache(redis, json, links, meter, false).resolve("localhost", "abc"));
        verifyNoInteractions(links);
        when(links.resolve("localhost", "abc")).thenReturn(Optional.of(l));
        assertEquals(l, new RedirectCache(redis, json, links, meter, true).resolve("localhost", "abc"));
        verify(links).resolve("localhost", "abc");
    }

    @Test
    void unavailableLinksNeverPublishAnalytics() {
        var cache = mock(RedirectCache.class);
        var analytics = mock(Analytics.class);
        var controller = new LinkController(mock(CreationLimiter.class), mock(Management.class), mock(Links.class), cache, analytics, new SimpleMeterRegistry(), "localhost", 302);
        var request = new MockHttpServletRequest();
        request.setServerName("localhost");
        for (var status : ShortLink.Status.values()) {
            when(cache.resolve("localhost", "abc")).thenReturn(link(status, Instant.now().minusSeconds(1)));
            assertThrows(ResponseStatusException.class, () -> controller.redirect("abc", request));
        }
        verifyNoInteractions(analytics);
        when(cache.resolve("localhost", "abc")).thenReturn(link(ShortLink.Status.ACTIVE, null));
        var response = controller.redirect("abc", request);
        assertEquals(302, response.getStatusCode().value());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals("https://example.com", response.getHeaders().getFirst("Location"));
        verify(analytics).click(any());
    }
}
