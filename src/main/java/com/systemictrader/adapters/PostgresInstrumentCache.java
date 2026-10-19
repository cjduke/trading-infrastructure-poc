package com.systemictrader.adapters;

import com.systemictrader.ports.InstrumentCache;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PostgresInstrumentCache implements InstrumentCache {

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public PostgresInstrumentCache(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public UUID getInstrumentId(String sourceId, String sourceSymbol) {
        String sql = """
            SELECT i.instrument_id
            FROM reference.instruments i
            JOIN reference.instrument_sources s ON s.instrument_id = i.instrument_id
            WHERE s.source_id = ? AND s.source_symbol = ? AND s.is_active = TRUE
            LIMIT 1
        """;

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sourceId);
            pstmt.setString(2, sourceSymbol);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject("instrument_id");
                }
            }
            return null; // Not found

        } catch (SQLException e) {
            throw new RuntimeException("Failed to look up instrument", e);
        }
    }
}