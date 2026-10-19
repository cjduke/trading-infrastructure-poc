package com.systemictrader.ports;

import java.util.Map;
import java.util.UUID;

public interface RunTracker {
    /**
     * Starts a run and returns a unique run ID.
     */
    UUID startRun(String moduleName, Map<String, Object> metadata);
    
    /**
     * Marks a run as complete.
     */
    void completeRun(UUID runId, int instrumentsProcessed, int signalsEmitted);
    
    /**
     * Marks a run as failed.
     */
    void failRun(UUID runId, String errorMessage);
}