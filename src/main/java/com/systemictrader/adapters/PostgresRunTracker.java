package com.systemictrader.adapters;

import com.systemictrader.ports.RunTracker;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class PostgresRunTracker implements RunTracker {

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public PostgresRunTracker(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public UUID startRun(String moduleName, Map<String, Object> metadata) {
        String sql = """
            INSERT INTO ops.engine_runs (
                module, started_at, status, metadata
            ) VALUES (?, ?, 'running', ?::jsonb)
            RETURNING run_id
        """;

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, moduleName);
            pstmt.setTimestamp(2, Timestamp.from(Instant.now()));
            // For simplicity, metadata as a JSON string. In a real app, use a proper JSON serializer.
            pstmt.setString(3, metadata == null ? "{}" : "{}");

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    UUID runId = (UUID) rs.getObject("run_id");
                    System.out.println("[RunTracker] Started run: " + runId);
                    return runId;
                }
            }
            throw new RuntimeException("Insert did not return a run_id");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to start run in Postgres", e);
        }
    }

    @Override
    public void completeRun(UUID runId, int instrumentsProcessed, int signalsEmitted) {
        String sql = """
            UPDATE ops.engine_runs
            SET completed_at = ?, status = 'success', instruments_processed = ?, signals_emitted = ?
            WHERE run_id = ?
        """;

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setTimestamp(1, Timestamp.from(Instant.now()));
            pstmt.setInt(2, instrumentsProcessed);
            pstmt.setInt(3, signalsEmitted);
            pstmt.setObject(4, runId);
            pstmt.executeUpdate();

            System.out.println("[RunTracker] Completed run: " + runId);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to complete run in Postgres", e);
        }
    }

    @Override
    public void failRun(UUID runId, String errorMessage) {
        String sql = """
            UPDATE ops.engine_runs
            SET completed_at = ?, status = 'failure', error_message = ?
            WHERE run_id = ?
        """;

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setTimestamp(1, Timestamp.from(Instant.now()));
            pstmt.setString(2, errorMessage);
            pstmt.setObject(3, runId);
            pstmt.executeUpdate();

            System.err.println("[RunTracker] Failed run: " + runId + " — " + errorMessage);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to record failure in Postgres", e);
        }
    }
}