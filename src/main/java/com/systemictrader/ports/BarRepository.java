package com.systemictrader.ports;

import com.systemictrader.domain.Bar;
import java.util.List;

public interface BarRepository {
    /**
     * Saves a list of canonical bars to the time-series database.
     */
    void saveBars(List<Bar> bars);
}
