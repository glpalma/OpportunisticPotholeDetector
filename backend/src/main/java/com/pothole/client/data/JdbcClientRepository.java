package com.pothole.client.data;

import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;

import com.pothole.client.domain.ClientRegistry;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcClientRepository implements ClientRegistry {

    private final JdbcClient jdbc;

    JdbcClientRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(String clientId, Instant lastSeenAt, Integer queueSize) {
        jdbc.sql("""
                INSERT INTO clients (client_id, last_seen_at, queue_size)
                VALUES (:clientId, :lastSeenAt, :queueSize)
                ON CONFLICT (client_id) DO UPDATE
                SET last_seen_at = EXCLUDED.last_seen_at,
                    queue_size = COALESCE(EXCLUDED.queue_size, clients.queue_size)
                """)
                .param("clientId", clientId)
                .param("lastSeenAt", lastSeenAt.atOffset(ZoneOffset.UTC))
                .param("queueSize", queueSize, Types.INTEGER)
                .update();
    }
}
