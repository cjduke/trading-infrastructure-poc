# Systematic Trading Engine — Data & Analysis Pipeline

A production-grade, decoupled algorithmic trading engine written in Java and Spring Boot. 

This repository demonstrates the foundational infrastructure for a systematic trading system. It uses **Hexagonal Architecture (Ports and Adapters)** to ingest market data from external sources (e.g., Yahoo Finance), normalize it, store it in a time-series database, and calculate analytical indicators without the core business logic ever knowing *where* the data came from or *where* it is going.

## 🏗️ Architecture & Design Patterns
The system is built on strict separation of concerns:
*   **Core (The Hexagon):** `IngestionService` and `AnalysisService` orchestrate the flow.
*   **Ports:** Interfaces (`MarketDataProvider`, `BarRepository`, `BarReader`, `IndicatorRepository`, `RunTracker`, `InstrumentCache`) define the boundaries.
*   **Adapters:** Implementations for Yahoo Finance (REST), QuestDB (Time-Series JDBC), and PostgreSQL (Relational/Audit).
*   **Patterns Used:** Hexagonal Architecture, Adapter, Repository, Builder (for immutable domain objects), Dependency Injection.
*   **All UML Design and ER Diagram documents including an Overall Design presentation document are in `/docs/`.

## 🛢️ Database Design
The system uses a dual-database approach to separate transactional state from high-volume time-series data:
*   **PostgreSQL:** Handles reference data (instrument UUIDs, symbology mapping) and operational audit logs (`ops.engine_runs`). *Schema available in `/db/postgres_schema.sql`.*
*   **QuestDB:** Handles high-throughput OHLCV bar storage (`bars`) and computed metrics (`indicator_history`) with deduplication and monthly partitioning. *Schema available in `/db/questdb_schema.sql`.*

## 🚀 Running the Project
1. Ensure PostgreSQL and QuestDB are running locally (Docker or native).
2. Run the SQL scripts in `/db/` to create the necessary schemas.
3. Copy `application.yml.example` to `application.yml` and add your database credentials.
4. Run the pipeline: 
   ```bash
   ./mvnw spring-boot:run