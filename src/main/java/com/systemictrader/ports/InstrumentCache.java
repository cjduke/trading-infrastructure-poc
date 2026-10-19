package com.systemictrader.ports;

import java.util.UUID;

public interface InstrumentCache {
    /**
     * Looks up the canonical UUID for a given source and symbol.
     */
    UUID getInstrumentId(String sourceId, String sourceSymbol);
}