package com.systemictrader.domain;

import java.time.Instant;

/**
 * Represents the raw, unvalidated data coming directly from a source like Yahoo.
 */
public record RawBar(
    String symbol,
    String timeframe,
    Instant openTime,
    double open,
    double high,
    double low,
    double close,
    double volume,
    Instant closeTime
) {}
