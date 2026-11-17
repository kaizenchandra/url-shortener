package com.example.urlshortener.domain;

import java.security.SecureRandom;
import java.util.Set;

public final class Codes {
    public static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> RESERVED = Set.of("api", "actuator", "admin", "health", "login", "metrics", "robots.txt", "favicon.ico");

    private Codes() {
    }

    public static String random() {
        var b = new StringBuilder();
        for (int i = 0; i < 10; i++) b.append(ALPHABET.charAt(RANDOM.nextInt(62)));
        return b.toString();
    }

    public static String alias(String s) {
        if (s == null || !s.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{2,31}") || RESERVED.contains(s.toLowerCase(java.util.Locale.ROOT)))
            throw new IllegalArgumentException("Invalid or reserved alias");
        return s;
    }

    public static String encode(long n) {
        if (n < 0) throw new IllegalArgumentException();
        var b = new StringBuilder();
        do {
            b.append(ALPHABET.charAt((int) (n % 62)));
            n /= 62;
        } while (n > 0);
        return b.reverse().toString();
    }

    public static long decode(String s) {
        if (s == null || s.isEmpty()) throw new IllegalArgumentException();
        long n = 0;
        for (char c : s.toCharArray()) {
            int v = ALPHABET.indexOf(c);
            if (v < 0) throw new IllegalArgumentException();
            n = Math.addExact(Math.multiplyExact(n, 62), v);
        }
        return n;
    }
}
