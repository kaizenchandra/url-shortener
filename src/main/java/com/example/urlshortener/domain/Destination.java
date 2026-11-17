package com.example.urlshortener.domain;

import java.net.URI;

public record Destination(String value) {
    public Destination {
        if (value == null || value.length() > 4096 || value.chars().anyMatch(c -> c <= 32 || c == 127))
            throw new IllegalArgumentException("Invalid URL length or characters");
        URI u;
        try {
            u = URI.create(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL");
        }
        if (!("https".equalsIgnoreCase(u.getScheme()) || "http".equalsIgnoreCase(u.getScheme())) || u.getHost() == null || u.getUserInfo() != null || (u.getPort() != -1 && (u.getPort() < 1 || u.getPort() > 65535)))
            throw new IllegalArgumentException("Only absolute HTTP(S) URLs without credentials are allowed");
    }
}
