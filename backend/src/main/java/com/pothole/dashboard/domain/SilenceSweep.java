package com.pothole.dashboard.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

import com.pothole.config.PotholeProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SilenceSweep {

    private static final Logger log = LoggerFactory.getLogger(SilenceSweep.class);

    private final JdbcClient jdbc;

    private final Clock clock;

    private final PotholeProperties properties;

    public SilenceSweep(JdbcClient jdbc, Clock clock, PotholeProperties properties) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.properties = properties;
    }

    public int apply() {
        var cutoff = clock.instant().minus(Duration.ofDays(properties.getSilenceWindowDays()));
        return jdbc.sql("""
                UPDATE potholes
                SET status = 'fixed'
                WHERE status = 'open'
                  AND last_seen_at <= :cutoff
                """)
                .param("cutoff", cutoff.atOffset(ZoneOffset.UTC))
                .update();
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "America/Sao_Paulo")
    void scheduled() {
        int updated = apply();
        log.info("Silence sweep marked {} open potholes fixed", updated);
    }
}
