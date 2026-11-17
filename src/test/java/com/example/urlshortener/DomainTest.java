package com.example.urlshortener;

import com.example.urlshortener.domain.Codes;
import com.example.urlshortener.domain.Destination;
import com.example.urlshortener.domain.ShortLink;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DomainTest {
    @Test
    void base62() {
        for (long n : new long[]{0, 1, 61, 62, 999999, Long.MAX_VALUE}) assertEquals(n, Codes.decode(Codes.encode(n)));
        assertThrows(IllegalArgumentException.class, () -> Codes.decode("!"));
        assertThrows(ArithmeticException.class, () -> Codes.decode("ZZZZZZZZZZZZZZZZZ"));
    }

    @Test
    void randomCodes() {
        var codes = new HashSet<String>();
        for (int i = 0; i < 10000; i++) {
            var code = Codes.random();
            assertTrue(code.matches("[0-9a-zA-Z]{10}"));
            assertTrue(codes.add(code));
        }
    }

    @Test
    void aliases() {
        assertEquals("Summer-sale", Codes.alias("Summer-sale"));
        for (String s : List.of("api", "ADMIN", "a", "x/y", "a b"))
            assertThrows(IllegalArgumentException.class, () -> Codes.alias(s));
    }

    @Test
    void destinations() {
        new Destination("https://example.com/a?b=c#d");
        for (String s : List.of("javascript:alert(1)", "data:text/html,x", "file:///etc/passwd", "https://u:p@example.com", "https://example.com/\r\nLocation:x", "/relative", "http://x:99999"))
            assertThrows(IllegalArgumentException.class, () -> new Destination(s));
    }

    @Test
    void lifecycle() {
        var l = new ShortLink(UUID.randomUUID(), "t", "u", "x", "abc", "https://x", ShortLink.Status.BLOCKED, Instant.now(), Instant.now().minusSeconds(1), 0);
        assertTrue(l.expired());
        assertThrows(IllegalArgumentException.class, () -> l.transition(ShortLink.Status.ACTIVE, false));
        l.transition(ShortLink.Status.ACTIVE, true);
    }
}
