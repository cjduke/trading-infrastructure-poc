package com.systemictrader.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * The canonical, internal representation of an OHLCV bar.
 */
public class Bar {
    private final Instant ts;
    private final UUID instrumentId;
    private final String symbol;
    private final String timeframe;
    private final double open;
    private final double high;
    private final double low;
    private final double close;
    private final double volume;
    private final String sourceId;
    private final String quality;

    private Bar(Builder builder) {
        this.ts = builder.ts;
        this.instrumentId = builder.instrumentId;
        this.symbol = builder.symbol;
        this.timeframe = builder.timeframe;
        this.open = builder.open;
        this.high = builder.high;
        this.low = builder.low;
        this.close = builder.close;
        this.volume = builder.volume;
        this.sourceId = builder.sourceId;
        this.quality = builder.quality;
    }

    // Getters
    public Instant getTs() { return ts; }
    public UUID getInstrumentId() { return instrumentId; }
    public String getSymbol() { return symbol; }
    public String getTimeframe() { return timeframe; }
    public double getOpen() { return open; }
    public double getHigh() { return high; }
    public double getLow() { return low; }
    public double getClose() { return close; }
    public double getVolume() { return volume; }
    public String getSourceId() { return sourceId; }
    public String getQuality() { return quality; }

    public static class Builder {
        private Instant ts;
        private UUID instrumentId;
        private String symbol;
        private String timeframe;
        private double open;
        private double high;
        private double low;
        private double close;
        private double volume;
        private String sourceId;
        private String quality = "FRESH"; // Default

        public Builder timestamp(Instant ts) { this.ts = ts; return this; }
        public Builder instrumentId(UUID id) { this.instrumentId = id; return this; }
        public Builder symbol(String sym) { this.symbol = sym; return this; }
        public Builder timeframe(String tf) { this.timeframe = tf; return this; }
        public Builder open(double o) { this.open = o; return this; }
        public Builder high(double h) { this.high = h; return this; }
        public Builder low(double l) { this.low = l; return this; }
        public Builder close(double c) { this.close = c; return this; }
        public Builder volume(double v) { this.volume = v; return this; }
        public Builder sourceId(String src) { this.sourceId = src; return this; }
        public Builder quality(String q) { this.quality = q; return this; }

        public Bar build() {
            // We can add validation here later
            return new Bar(this);
        }
    }
}