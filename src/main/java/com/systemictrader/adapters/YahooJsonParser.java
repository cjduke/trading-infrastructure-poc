package com.systemictrader.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.systemictrader.domain.RawBar;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class YahooJsonParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<RawBar> parse(String jsonBody, String symbol, String timeframe) throws Exception {
        List<RawBar> bars = new ArrayList<>();
        JsonNode root = mapper.readTree(jsonBody);

        // Navigate the JSON tree
        JsonNode result = root.path("chart").path("result").path(0);
        if (result.isMissingNode()) {
            return bars; // No data
        }

        JsonNode timestamps = result.path("timestamp");
        JsonNode quote = result.path("indicators").path("quote").path(0);

        if (timestamps.isMissingNode() || quote.isMissingNode()) {
            return bars;
        }

        JsonNode opens = quote.path("open");
        JsonNode highs = quote.path("high");
        JsonNode lows = quote.path("low");
        JsonNode closes = quote.path("close");
        JsonNode volumes = quote.path("volume");

        // Iterate through the parallel arrays
        for (int i = 0; i < timestamps.size(); i++) {
            
            // Yahoo sometimes returns null values for certain days. Skip those.
            if (opens.get(i).isNull() || closes.get(i).isNull()) {
                continue;
            }

            Instant openTime = Instant.ofEpochSecond(timestamps.get(i).asLong());
            // We'll use a standardized close time for daily bars (e.g., +1 day for D1)
            // For a real implementation, use the exact close time from the API if available.
            Instant closeTime = openTime.plusSeconds(86400); 

            RawBar bar = new RawBar(
                symbol,
                timeframe,
                openTime,
                opens.get(i).asDouble(),
                highs.get(i).asDouble(),
                lows.get(i).asDouble(),
                closes.get(i).asDouble(),
                volumes.get(i).isNull() ? 0.0 : volumes.get(i).asDouble(),
                closeTime
            );
            bars.add(bar);
        }

        return bars;
    }
}