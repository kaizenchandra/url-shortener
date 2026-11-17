package com.example.urlshortener.infrastructure.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

@Component
public class Analytics {
    private final ArrayBlockingQueue<UUID> queue = new ArrayBlockingQueue<>(10000);
    private final JdbcTemplate db;
    private final MeterRegistry metrics;

    public Analytics(JdbcTemplate db, MeterRegistry metrics) {
        this.db = db;
        this.metrics = metrics;
    }

    public void click(UUID id) {
        metrics.counter(queue.offer(id) ? "analytics_events_published_total" : "analytics_events_failed_total").increment();
    }

    @Scheduled(fixedDelay = 250)
    public void flush() {
        var batch = new java.util.ArrayList<UUID>();
        queue.drainTo(batch, 1000);
        var counts = new java.util.HashMap<UUID, Long>();
        batch.forEach(id -> counts.merge(id, 1L, Long::sum));
        counts.forEach((id, n) -> {
            try {
                db.update("INSERT INTO click_counts VALUES (?,date_trunc('hour',now()),?) ON CONFLICT(link_id,hour) DO UPDATE SET clicks=click_counts.clicks+excluded.clicks", id, n);
            } catch (Exception e) {
                metrics.counter("analytics_events_failed_total").increment(n);
            }
        });
    }

    @Scheduled(fixedDelay = 3600000)
    public void retain() {
        db.update("DELETE FROM click_counts WHERE hour < now()-interval '30 days'");
    }
}
