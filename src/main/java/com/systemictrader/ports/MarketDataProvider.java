package com.systemictrader.ports;

import com.systemictrader.domain.RawBar;
import java.time.Instant;
import java.util.List;

public interface MarketDataProvider {
    /**
     * Fetches raw bars from a specific source.
     */
    List<RawBar> fetchBars(String symbol, String timeframe, Instant startTime, Instant endTime);
}