package com.example.urlshortener;

import com.example.urlshortener.application.Management;
import com.example.urlshortener.application.Management.Create;
import com.example.urlshortener.application.Management.Identity;
import com.example.urlshortener.application.Management.Patch;
import com.example.urlshortener.domain.ShortLink;
import com.example.urlshortener.infrastructure.cache.RedirectCache;
import com.example.urlshortener.infrastructure.persistence.Links;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
class DatabaseIntegrationTest {
    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:17-alpine");
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    @Autowired
    Management service;
    @Autowired
    Links links;
    @Autowired
    RedirectCache cache;
    @Autowired
    ObjectMapper json;
    @Autowired
    MockMvc mvc;
    Identity identity = new Identity("tenant", "owner", false);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        r.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "https://issuer.invalid");
    }

    @Test
    void durableIdempotencyAndOwnership() throws Exception {
        var req = new Create("https://example.com", null, null, null);
        String one = service.create(req, "key", identity);
        assertEquals(one, service.create(req, "key", identity));
        assertThrows(Exception.class, () -> service.create(new Create("https://other.com", null, null, null), "key", identity));
        UUID id = UUID.fromString(json.readTree(one).get("id").asText());
        assertThrows(Exception.class, () -> service.owned(id, new Identity("other", "owner", false)));
        var l = service.owned(id, identity);
        assertEquals(l, cache.resolve(l.domain(), l.code()));
        assertEquals(l, cache.resolve(l.domain(), l.code()));
        service.patch(id, new Patch(null, ShortLink.Status.DISABLED, null, 0L), identity);
        assertThrows(Exception.class, () -> service.patch(id, new Patch(null, ShortLink.Status.ACTIVE, null, 0L), identity));
        Thread.sleep(2200);
        assertEquals(ShortLink.Status.DISABLED, cache.resolve(l.domain(), l.code()).status());
    }

    @Test
    void aliasRace() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            var jobs = new ArrayList<Callable<Boolean>>();
            for (int n = 0; n < 16; n++)
                jobs.add(() -> {
                    try {
                        service.create(new Create("https://example.com", "race-alias", null, null), null, identity);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });
            long successes = 0;
            for (var f : executor.invokeAll(jobs)) if (f.get()) successes++;
            assertEquals(1, successes);
            assertEquals(1L, links.database().queryForObject("SELECT count(*) FROM short_links WHERE code='race-alias'", Long.class));
        }
    }

    @Test
    void unauthenticatedManagementDenied() throws Exception {
        mvc.perform(get("/api/v1/links")).andExpect(status().isUnauthorized());
    }
}
