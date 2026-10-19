package com.systemictrader.adapters;

import com.systemictrader.domain.RawBar;
import com.systemictrader.ports.MarketDataProvider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

public class YahooBarProvider implements MarketDataProvider {

    private final HttpClient httpClient;
    private final YahooJsonParser jsonParser;
    private final String baseUrl = "https://query1.finance.yahoo.com/v8/finance/chart/";

    public YahooBarProvider() {
        this.httpClient = HttpClient.newHttpClient();
        this.jsonParser = new YahooJsonParser(); // <-- Instantiate the parser
    }

    @Override
    public List<RawBar> fetchBars(String symbol, String timeframe, Instant startTime, Instant endTime) {
        try {
            long period1 = startTime.getEpochSecond();
            long period2 = endTime.getEpochSecond();
            String yahooInterval = translateTimeframe(timeframe);
            
            String urlString = String.format("%s%s?interval=%s&period1=%d&period2=%d",
                    baseUrl, symbol, yahooInterval, period1, period2);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .header("User-Agent", "Mozilla/5.0") 
                    .GET()
                    .build();

            System.out.println("Sending request to Yahoo: " + urlString);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Yahoo API returned status code: " + response.statusCode());
            }

            // Use the real parser
            return jsonParser.parse(response.body(), symbol, timeframe);

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch data from Yahoo for " + symbol, e);
        }
    }

    private String translateTimeframe(String timeframe) {
        return switch (timeframe) {
            case "D1" -> "1d";
            case "H1" -> "1h";
            case "M15" -> "15m";
            case "M5" -> "5m";
            default -> throw new IllegalArgumentException("Unsupported timeframe: " + timeframe);
        };
    }
}