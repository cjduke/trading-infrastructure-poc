package com.systemictrader.config;

import com.systemictrader.adapters.PostgresInstrumentCache;
import com.systemictrader.adapters.PostgresRunTracker;
import com.systemictrader.adapters.QuestDBBarRepository;
import com.systemictrader.adapters.YahooBarProvider;
import com.systemictrader.core.IngestionService;
import com.systemictrader.ports.BarRepository;
import com.systemictrader.ports.InstrumentCache;
import com.systemictrader.ports.MarketDataProvider;
import com.systemictrader.ports.RunTracker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfig {

    @Value("${engine.databases.postgres.url}")
    private String postgresUrl;

    @Value("${engine.databases.postgres.username}")
    private String postgresUser;

    @Value("${engine.databases.postgres.password}")
    private String postgresPassword;

    @Value("${engine.databases.questdb.url}")
    private String questDbUrl;

    @Value("${engine.databases.questdb.username}")
    private String questDbUser;

    @Value("${engine.databases.questdb.password}")
    private String questDbPassword;

    // --- The Adapters ---

    @Bean
    public MarketDataProvider marketDataProvider() {
        return new YahooBarProvider();
    }

    @Bean
    public BarRepository barRepository() {
        return new QuestDBBarRepository(questDbUrl, questDbUser, questDbPassword);
    }

    @Bean
    public RunTracker runTracker() {
        return new PostgresRunTracker(postgresUrl, postgresUser, postgresPassword);
    }

    @Bean
    public InstrumentCache instrumentCache() {
        return new PostgresInstrumentCache(postgresUrl, postgresUser, postgresPassword);
    }

    // --- The Core ---

    @Bean
    public IngestionService ingestionService(
            MarketDataProvider provider,
            BarRepository repository,
            RunTracker tracker,
            InstrumentCache cache) {
        
        // Here is the wiring. The Core doesn't know what the adapters are.
        return new IngestionService(provider, repository, tracker, cache);
    }
}