-- ============================================================================
-- Traders Engine — PostgreSQL Schema
-- ============================================================================
-- Database: traders
-- Schemas:  reference, signal, web, ops
--
-- This file is idempotent — safe to run multiple times.
-- Run order: schemas first, then tables in dependency order, then indexes.
--
-- For a clean rebuild, uncomment the DROP block below and run once.
-- ============================================================================

-- ============================================================================
-- OPTIONAL: CLEAN RESET
-- Uncomment to wipe everything and start fresh.
-- ============================================================================
-- DROP SCHEMA IF EXISTS reference CASCADE;
-- DROP SCHEMA IF EXISTS signal    CASCADE;
-- DROP SCHEMA IF EXISTS web       CASCADE;
-- DROP SCHEMA IF EXISTS ops       CASCADE;


-- ============================================================================
-- SCHEMAS
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS reference;
CREATE SCHEMA IF NOT EXISTS signal;
CREATE SCHEMA IF NOT EXISTS web;
CREATE SCHEMA IF NOT EXISTS ops;


-- ============================================================================
-- REFERENCE SCHEMA
-- The analytical anchor. Instruments, sources, symbology, synthetics.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- reference.data_sources
-- Registry of every place data comes from.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reference.data_sources (
  source_id       TEXT PRIMARY KEY,
  display_name    TEXT NOT NULL,
  source_type     TEXT NOT NULL,
  base_url        TEXT,
  is_active       BOOLEAN NOT NULL DEFAULT TRUE,
  metadata        JSONB NOT NULL DEFAULT '{}',
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


-- ----------------------------------------------------------------------------
-- reference.instruments
-- Canonical list of everything the engine can talk about.
-- Includes real instruments and synthetics (ratios, indices, spreads).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reference.instruments (
  instrument_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  canonical_symbol  TEXT NOT NULL UNIQUE,
  display_name      TEXT NOT NULL,
  asset_class       TEXT NOT NULL,
  base_currency     TEXT,
  quote_currency    TEXT,
  venue             TEXT,
  is_synthetic      BOOLEAN NOT NULL DEFAULT FALSE,
  is_active         BOOLEAN NOT NULL DEFAULT TRUE,
  metadata          JSONB NOT NULL DEFAULT '{}',
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_instruments_asset_class
  ON reference.instruments(asset_class)
  WHERE is_active;


-- ----------------------------------------------------------------------------
-- reference.instrument_sources
-- Symbology map. Source-specific symbols → canonical instrument.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reference.instrument_sources (
  id              BIGSERIAL PRIMARY KEY,
  instrument_id   UUID NOT NULL REFERENCES reference.instruments(instrument_id),
  source_id       TEXT NOT NULL REFERENCES reference.data_sources(source_id),
  source_symbol   TEXT NOT NULL,
  priority        INT NOT NULL DEFAULT 100,
  is_active       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (source_id, source_symbol)
);

CREATE INDEX IF NOT EXISTS idx_instrument_sources_lookup
  ON reference.instrument_sources(instrument_id, source_id)
  WHERE is_active;


-- ----------------------------------------------------------------------------
-- reference.synthetic_instruments
-- Definition of computed instruments (ratios, indices, spreads).
-- One-to-one extension of reference.instruments where is_synthetic = TRUE.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reference.synthetic_instruments (
  instrument_id   UUID PRIMARY KEY REFERENCES reference.instruments(instrument_id),
  formula_type    TEXT NOT NULL,
  components      JSONB NOT NULL,
  parameters      JSONB NOT NULL DEFAULT '{}',
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


-- ============================================================================
-- SIGNAL SCHEMA
-- Signals produced by the engine, their readings, and their risk blocks.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- signal.signals
-- Every signal the engine emits. WAIT, SIGNAL, INVALID.
-- Self-referential chain via parent_signal_id / superseded_by.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS signal.signals (
  signal_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  schema_version        TEXT NOT NULL DEFAULT '1.0',
  module                TEXT NOT NULL,
  instrument_id         UUID NOT NULL REFERENCES reference.instruments(instrument_id),
  timeframe             TEXT NOT NULL,

  ts_event              TIMESTAMPTZ NOT NULL,
  ts_emit               TIMESTAMPTZ NOT NULL,

  state                 TEXT NOT NULL,
  direction             TEXT NOT NULL,

  confidence_total      SMALLINT NOT NULL,
  confidence_env        SMALLINT NOT NULL,
  confidence_stretch    SMALLINT NOT NULL,
  confidence_evidence   SMALLINT NOT NULL,
  confidence_risk       SMALLINT NOT NULL,

  headline              TEXT NOT NULL,
  narrative             TEXT NOT NULL,
  narrative_source      TEXT NOT NULL DEFAULT 'TEMPLATE',

  reference_version     TEXT NOT NULL,
  source_data_hash      TEXT NOT NULL,

  parent_signal_id      UUID REFERENCES signal.signals(signal_id),
  superseded_by         UUID REFERENCES signal.signals(signal_id),

  context_tags          TEXT[] NOT NULL DEFAULT '{}',
  metadata              JSONB NOT NULL DEFAULT '{}',

  created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_signals_instrument_time
  ON signal.signals(instrument_id, ts_event DESC);

CREATE INDEX IF NOT EXISTS idx_signals_state
  ON signal.signals(state)
  WHERE state <> 'INVALID';

CREATE INDEX IF NOT EXISTS idx_signals_module_state
  ON signal.signals(module, state, ts_event DESC);

CREATE INDEX IF NOT EXISTS idx_signals_public_track
  ON signal.signals(ts_emit DESC)
  WHERE state IN ('SIGNAL', 'INVALID');


-- ----------------------------------------------------------------------------
-- signal.signal_indicators
-- Readings attached to each signal. One row per reading per signal.
-- Includes indicator values, correlation checks, regime scores, composites.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS signal.signal_indicators (
  id                BIGSERIAL PRIMARY KEY,
  signal_id         UUID NOT NULL REFERENCES signal.signals(signal_id) ON DELETE CASCADE,
  name              TEXT NOT NULL,
  value             DOUBLE PRECISION NOT NULL,
  period            INT,
  threshold         DOUBLE PRECISION,
  position          TEXT,
  percentile_rank   DOUBLE PRECISION,
  quality           TEXT NOT NULL DEFAULT 'FRESH',
  source            TEXT NOT NULL,
  computed_at       TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_signal_indicators_signal
  ON signal.signal_indicators(signal_id);

CREATE INDEX IF NOT EXISTS idx_signal_indicators_name
  ON signal.signal_indicators(name);


-- ----------------------------------------------------------------------------
-- signal.signal_risk
-- The fourth stage of the framework, persisted. One row per signal.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS signal.signal_risk (
  signal_id            UUID PRIMARY KEY REFERENCES signal.signals(signal_id) ON DELETE CASCADE,
  invalidation_level   DOUBLE PRECISION,
  invalidation_reason  TEXT,
  target_level         DOUBLE PRECISION,
  target_reason        TEXT,
  reward_risk_ratio    DOUBLE PRECISION
);


-- ============================================================================
-- WEB SCHEMA
-- Users, subscriptions, content. The PHP application's domain.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- web.users
-- Account identity.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS web.users (
  user_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email           TEXT NOT NULL UNIQUE,
  display_name    TEXT,
  password_hash   TEXT NOT NULL,
  role            TEXT NOT NULL DEFAULT 'subscriber',
  is_active       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_login_at   TIMESTAMPTZ
);


-- ----------------------------------------------------------------------------
-- web.subscriptions
-- Current subscription state. One row per subscription (past and present).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS web.subscriptions (
  subscription_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id             UUID NOT NULL REFERENCES web.users(user_id),
  tier                TEXT NOT NULL,
  status              TEXT NOT NULL,
  started_at          TIMESTAMPTZ NOT NULL,
  current_period_end  TIMESTAMPTZ,
  cancelled_at        TIMESTAMPTZ,
  external_ref        TEXT,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_user
  ON web.subscriptions(user_id)
  WHERE status = 'active';

CREATE INDEX IF NOT EXISTS idx_subscriptions_status
  ON web.subscriptions(status, current_period_end);


-- ----------------------------------------------------------------------------
-- web.subscription_events
-- Append-only log of every subscription change.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS web.subscription_events (
  id                BIGSERIAL PRIMARY KEY,
  subscription_id   UUID NOT NULL REFERENCES web.subscriptions(subscription_id),
  event_type        TEXT NOT NULL,
  previous_tier     TEXT,
  new_tier          TEXT,
  event_at          TIMESTAMPTZ NOT NULL,
  payload           JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_subscription_events_subscription
  ON web.subscription_events(subscription_id, event_at DESC);


-- ----------------------------------------------------------------------------
-- web.content
-- Articles, book excerpts, case studies, tutorials.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS web.content (
  content_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  author_id       UUID REFERENCES web.users(user_id),
  slug            TEXT NOT NULL UNIQUE,
  title           TEXT NOT NULL,
  summary         TEXT,
  body            TEXT NOT NULL,
  content_type    TEXT NOT NULL,
  visibility      TEXT NOT NULL DEFAULT 'public',
  published_at    TIMESTAMPTZ,
  status          TEXT NOT NULL DEFAULT 'draft',
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_content_published
  ON web.content(published_at DESC)
  WHERE status = 'published';

CREATE INDEX IF NOT EXISTS idx_content_author
  ON web.content(author_id)
  WHERE author_id IS NOT NULL;


-- ----------------------------------------------------------------------------
-- web.content_signal_links
-- The bridge between editorial content and analytical signals.
-- Many-to-many junction.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS web.content_signal_links (
  content_id     UUID NOT NULL REFERENCES web.content(content_id),
  signal_id      UUID NOT NULL REFERENCES signal.signals(signal_id),
  link_context   TEXT,
  PRIMARY KEY (content_id, signal_id)
);

CREATE INDEX IF NOT EXISTS idx_content_signal_links_signal
  ON web.content_signal_links(signal_id);


-- ============================================================================
-- OPS SCHEMA
-- Operational observability: engine runs, data quality, audit trail.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- ops.engine_runs
-- Log of every execution of the engine.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ops.engine_runs (
  run_id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  module                  TEXT NOT NULL,
  started_at              TIMESTAMPTZ NOT NULL,
  completed_at            TIMESTAMPTZ,
  status                  TEXT NOT NULL,
  instruments_processed   INT,
  signals_emitted         INT,
  error_message           TEXT,
  metadata                JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_engine_runs_module_time
  ON ops.engine_runs(module, started_at DESC);


-- ----------------------------------------------------------------------------
-- ops.data_quality_events
-- Log of data problems: gaps, spikes, staleness, outages.
-- Feeds the `quality` field on signal_indicators.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ops.data_quality_events (
  id              BIGSERIAL PRIMARY KEY,
  source_id       TEXT NOT NULL REFERENCES reference.data_sources(source_id),
  instrument_id   UUID REFERENCES reference.instruments(instrument_id),
  event_type      TEXT NOT NULL,
  severity        TEXT NOT NULL,
  detected_at     TIMESTAMPTZ NOT NULL,
  details         JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_data_quality_time
  ON ops.data_quality_events(detected_at DESC);

CREATE INDEX IF NOT EXISTS idx_data_quality_instrument
  ON ops.data_quality_events(instrument_id, detected_at DESC);


-- ----------------------------------------------------------------------------
-- ops.audit_log
-- Append-only record of every meaningful action.
-- Polymorphic target (target_type + target_id).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ops.audit_log (
  id             BIGSERIAL PRIMARY KEY,
  actor_id       UUID REFERENCES web.users(user_id),
  actor_type     TEXT NOT NULL,
  action         TEXT NOT NULL,
  target_type    TEXT NOT NULL,
  target_id      TEXT NOT NULL,
  payload        JSONB NOT NULL DEFAULT '{}',
  occurred_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_actor
  ON ops.audit_log(actor_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_target
  ON ops.audit_log(target_type, target_id);

CREATE INDEX IF NOT EXISTS idx_audit_recent
  ON ops.audit_log(occurred_at DESC);


-- ============================================================================
-- END OF SCHEMA
-- ============================================================================
-- 15 tables across 4 schemas.
--
-- reference: data_sources, instruments, instrument_sources, synthetic_instruments
-- signal:    signals, signal_indicators, signal_risk
-- web:       users, subscriptions, subscription_events, content, content_signal_links
-- ops:       engine_runs, data_quality_events, audit_log
--
-- Indexes:
--   reference: 2 deliberate + automatic ones
--   signal:    5 deliberate + automatic ones
--   web:       6 deliberate + automatic ones
--   ops:       6 deliberate + automatic ones
-- ============================================================================