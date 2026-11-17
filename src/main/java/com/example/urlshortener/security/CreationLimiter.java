package com.example.urlshortener.security;

import com.example.urlshortener.application.Management;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Component
public class CreationLimiter {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end; return n", Long.class);
    private final StringRedisTemplate redis;

    public CreationLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void check(String tenant, String subject) {
        try {
            for (var pair : List.of(new String[]{tenant, "600"}, new String[]{tenant + ":" + subject, "60"})) {
                var key = "create-rate:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pair[0].getBytes(StandardCharsets.UTF_8)));
                Long n = redis.execute(SCRIPT, List.of(key));
                if (n == null || n > Long.parseLong(pair[1])) throw Management.error(429, "Creation rate exceeded");
            }
        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw Management.error(503, "Creation limiter unavailable");
        }
    }
}
