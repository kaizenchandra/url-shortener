package com.example.urlshortener.infrastructure.persistence;

import com.example.urlshortener.domain.ShortLink;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class Links {
    public static final RowMapper<ShortLink> MAPPER = (r, n) -> new ShortLink(r.getObject("id", UUID.class), r.getString("tenant"), r.getString("owner"), r.getString("domain"), r.getString("code"), r.getString("destination"), ShortLink.Status.valueOf(r.getString("status")), r.getTimestamp("created_at").toInstant(), r.getTimestamp("expires_at") == null ? null : r.getTimestamp("expires_at").toInstant(), r.getLong("version"));
    private final JdbcTemplate db;

    public Links(JdbcTemplate db) {
        this.db = db;
    }

    public JdbcTemplate database() {
        return db;
    }

    public Optional<ShortLink> resolve(String domain, String code) {
        return db.query("SELECT * FROM short_links WHERE domain=? AND code=?", MAPPER, domain, code).stream().findFirst();
    }

    public Optional<ShortLink> get(UUID id) {
        return db.query("SELECT * FROM short_links WHERE id=?", MAPPER, id).stream().findFirst();
    }
}
