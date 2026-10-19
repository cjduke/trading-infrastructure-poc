package com.systemictrader.core;

import com.systemictrader.domain.Bar;
import com.systemictrader.domain.RawBar;
import com.systemictrader.ports.BarRepository;
import com.systemictrader.ports.InstrumentCache;
import com.systemictrader.ports.MarketDataProvider;
import com.systemictrader.ports.RunTracker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class IngestionService {

    private final MarketDataProvider dataProvider;
    private final BarRepository barRepository;
    private final RunTracker runTracker;
    private final InstrumentCache instrumentCache;

    // Dependency Injection via Constructor
    public IngestionService(MarketDataProvider dataProvider,
                            BarRepository barRepository,
                            RunTracker runTracker,
                            InstrumentCache instrumentCache) {
        this.dataProvider = dataProvider;
        this.barRepository = barRepository;
        this.runTracker = runTracker;
        this.instrumentCache = instrumentCache;
    }

    /**
     * The main pipeline execution.
     */
    public void run(String sourceId, String sourceSymbol, String timeframe, Instant startTime, Instant endTime) {
        
        // 1. Start tracking the run
        UUID runId = runTracker.startRun("INGESTION", null);
        System.out.println("Started ingestion run: " + runId);

        try {
            // 2. Get the canonical internal UUID from Postgres
            UUID instrumentId = instrumentCache.getInstrumentId(sourceId, sourceSymbol);
            if (instrumentId == null) {
                throw new IllegalArgumentException("Instrument not found in database: " + sourceSymbol);
            }

            // 3. Fetch raw data from the provider (Yahoo, Binance, etc.)
            System.out.println("Fetching data for " + sourceSymbol + "...");
            List<RawBar> rawBars = dataProvider.fetchBars(sourceSymbol, timeframe, startTime, endTime);
            System.out.println("Fetched " + rawBars.size() + " bars.");

            // 4. Map RawBar to Canonical Bar using the Builder pattern
            List<Bar> canonicalBars = new ArrayList<>();
            for (RawBar raw : rawBars) {
                Bar bar = new Bar.Builder()
                        .timestamp(raw.openTime())
                        .instrumentId(instrumentId)
                        .symbol(sourceSymbol) // Or use a mapped canonical symbol
                        .timeframe(timeframe)
                        .open(raw.open())
                        .high(raw.high())
                        .low(raw.low())
                        .close(raw.close())
                        .volume(raw.volume())
                        .sourceId(sourceId)
                        .quality("FRESH")
                        .build();
                canonicalBars.add(bar);
            }

            // 5. Save the canonical bars to the database (QuestDB)
            System.out.println("Saving bars to database...");
            barRepository.saveBars(canonicalBars);

            // 6. Mark the run as successful
            runTracker.completeRun(runId, 1, 0);
            System.out.println("Run " + runId + " completed successfully.");

        } catch (Exception e) {
            // 7. If anything fails, log the error and fail gracefully
            System.err.println("Ingestion failed: " + e.getMessage());
            runTracker.failRun(runId, e.getMessage());
            throw new RuntimeException("Ingestion pipeline failed", e);
        }
    }
}