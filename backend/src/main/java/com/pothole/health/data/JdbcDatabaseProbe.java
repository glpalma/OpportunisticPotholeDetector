package com.pothole.health.data;

import com.pothole.health.domain.DatabaseProbe;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcDatabaseProbe implements DatabaseProbe {

    private final JdbcClient jdbc;

    JdbcDatabaseProbe(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean selectOne() {
        try {
            jdbc.sql("SELECT 1").query(Integer.class).single();
            return true;
        } catch (DataAccessException ex) {
            return false;
        }
    }
}
