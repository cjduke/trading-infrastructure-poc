package com.systemictrader.adapters;

import com.systemictrader.domain.Bar;
import com.systemictrader.ports.BarRepository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

public class QuestDBBarRepository implements BarRepository {

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public QuestDBBarRepository(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public void saveBars(List<Bar> bars) {
        if (bars == null || bars.isEmpty()) {
            return;
        }

        // The SQL statement to insert a bar. 
        // We use a standard INSERT. QuestDB will handle the append.
        String sql = """
            INSERT INTO bars (
                ts, instrument_id, symbol, timeframe, open, high, low, close, volume, source_id, quality
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        // try-with-resources ensures the connection and statement are closed automatically
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (Bar bar : bars) {
                // QuestDB expects a standard SQL Timestamp (without timezone)
                // We convert the Java Instant to a Timestamp.
                pstmt.setTimestamp(1, Timestamp.from(bar.getTs()));
                
                // Set the UUID. The JDBC driver maps Java UUID to Postgres/QuestDB UUID.
                pstmt.setObject(2, bar.getInstrumentId());
                
                pstmt.setString(3, bar.getSymbol());
                pstmt.setString(4, bar.getTimeframe());
                pstmt.setDouble(5, bar.getOpen());
                pstmt.setDouble(6, bar.getHigh());
                pstmt.setDouble(7, bar.getLow());
                pstmt.setDouble(8, bar.getClose());
                pstmt.setDouble(9, bar.getVolume());
                pstmt.setString(10, bar.getSourceId());
                pstmt.setString(11, bar.getQuality());

                pstmt.addBatch(); // Add to the batch instead of executing immediately
            }

            // Execute all inserts in one go
            int[] updateCounts = pstmt.executeBatch();
            System.out.println("Successfully inserted " + updateCounts.length + " bars into QuestDB.");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save bars to QuestDB", e);
        }
    }
}