package com.systemictrader.tradingengine;

import com.systemictrader.core.IngestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@SpringBootApplication
@ComponentScan(basePackages = "com.systemictrader") // Tells Spring to scan our new packages
public class TradingEngineApplication implements CommandLineRunner {

    @Autowired
    private IngestionService ingestionService;

    public static void main(String[] args) {
        SpringApplication.run(TradingEngineApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("==================================================");
        System.out.println(" STARTING JAVA INGESTION PIPELINE");
        System.out.println("==================================================");

        String sourceId = "yfinance";
        String sourceSymbol = "BTC-USD";
        String timeframe = "D1";
        
        // Fetch the last 30 days
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(30, ChronoUnit.DAYS);

        // Execute the pipeline!
        ingestionService.run(sourceId, sourceSymbol, timeframe, startTime, endTime);

        System.out.println("==================================================");
        System.out.println(" PIPELINE FINISHED");
        System.out.println("==================================================");
    }
}