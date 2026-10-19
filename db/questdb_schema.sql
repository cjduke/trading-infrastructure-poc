-- ============================================================================
-- Traders Engine — QuestDB Schema
-- ============================================================================
-- Database: qdb  (QuestDB's implicit default database)
-- Tables:   bars, indicator_history, ticks
--
-- QuestDB does not support schemas, foreign keys, or multi-database setups.
-- Everything lives in the single implicit `qdb` database.
--
-- This file is idempotent — safe to run multiple times.
-- Run order: bars, indicator_history, ticks (no dependencies between them).
--
-- IMPORTANT — QuestDB vs Postgres differences:
--   • No CREATE SCHEMA / USE statement
--   • No foreign keys
--   • No JSONB (limited JSON support only)
--   • DELETE FROM ... WHERE is NOT supported for row-level deletes
--   • Tables are append-only by design
--
-- To reset a table:
--   • ALTER TABLE <table> DROP PARTITION LIST '<yyyy-MM>'  (past partitions)
--   • DROP TABLE <table>; then recreate                   (current partition / all data)
-- ============================================================================


-- ============================================================================
-- TABLE 1: bars
-- ============================================================================
-- OHLCV bars across all instruments and timeframes.
-- One row per instrument per timeframe per bar.
--
-- ts = bar CLOSE time (UTC). This is the designated timestamp.
-- Partitioned by MONTH — suitable for daily/4h/1h/15m/1m bars accumulated
-- over years.
-- ============================================================================

CREATE TABLE IF NOT EXISTS bars (
  ts            TIMESTAMP,
  instrument_id UUID,
  symbol        SYMBOL,
  timeframe     SYMBOL,
  open          DOUBLE,
  high          DOUBLE,
  low           DOUBLE,
  close         DOUBLE,
  volume        DOUBLE,
  source_id     SYMBOL,
  quality       SYMBOL
) TIMESTAMP(ts) PARTITION BY MONTH;


-- ============================================================================
-- TABLE 2: indicator_history
-- ============================================================================
-- Every computed reading over time — the continuous history of every metric
-- the engine calculates.
--
-- Distinct from signal.signal_indicators in Postgres:
--   • signal_indicators  → readings ATTACHED to a signal (discrete moments)
--   • indicator_history  → ALL readings, continuously (dense time series)
--
-- ts = the bar close time the reading is based on (not computation time).
-- Partitioned by MONTH.
--
-- Sentinel values (QuestDB has no true NULL):
--   period = 0            → not applicable
--   threshold = 0         → no threshold for this reading
--   percentile_rank = -1  → not computed
-- ============================================================================

CREATE TABLE IF NOT EXISTS indicator_history (
  ts              TIMESTAMP,
  instrument_id   UUID,
  symbol          SYMBOL,
  timeframe       SYMBOL,
  name            SYMBOL,
  value           DOUBLE,
  period          INT,
  threshold       DOUBLE,
  position        SYMBOL,
  percentile_rank DOUBLE,
  quality         SYMBOL,
  source          SYMBOL
) TIMESTAMP(ts) PARTITION BY MONTH;


-- ============================================================================
-- TABLE 3: ticks
-- ============================================================================
-- Raw market events: quotes, trades, and combined ticks.
-- One row per market event from one source.
--
-- ts = event time (when the tick occurred in the market).
-- Partitioned by DAY — tick volume is orders of magnitude higher than bars.
--
-- tick_type distinguishes shape:
--   'quote' → bid, ask, bid_size, ask_size populated; last = NaN
--   'trade' → last, last_size populated; bid, ask = NaN
--   'both'  → all price fields populated
--
-- Non-applicable numeric fields are written as NaN.
-- ============================================================================

CREATE TABLE IF NOT EXISTS ticks (
  ts            TIMESTAMP,
  instrument_id UUID,
  symbol        SYMBOL,
  source_id     SYMBOL,
  bid           DOUBLE,
  ask           DOUBLE,
  last          DOUBLE,
  bid_size      DOUBLE,
  ask_size      DOUBLE,
  last_size     DOUBLE,
  tick_type     SYMBOL,
  quality       SYMBOL
) TIMESTAMP(ts) PARTITION BY DAY;


-- ============================================================================
-- END OF SCHEMA
-- ============================================================================
-- 3 tables, no foreign keys, no schemas.
--
-- Partitioning summary:
--   bars               → MONTH   (daily/4h bars accumulate over years)
--   indicator_history  → MONTH   (similar volume to bars)
--   ticks              → DAY     (high-volume event data)
--
-- Design principles applied:
--   • Design is what the system must be; coding is how it becomes that.
--   • Tables exist in the design whether or not code writes to them yet.
--   • Timestamps are UTC event times, not ingestion times.
--   • SYMBOL for repeated string values (dictionary-encoded, fast filters).
--   • instrument_id + symbol stored alongside each other for convenience.
--   • No cross-database foreign keys — integrity lives in the application.
--
-- Relationship to Postgres:
--   • instrument_id values match reference.instruments.instrument_id
--   • symbol values match reference.instruments.canonical_symbol
--   • source_id values match reference.data_sources.source_id
--   • Referential integrity is enforced by the ingestion/analysis code,
--     not by the database.
-- ============================================================================