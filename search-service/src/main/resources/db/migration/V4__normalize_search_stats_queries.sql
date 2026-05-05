-- Normalize historical keys to avoid split counters after trim-based aggregation.
--
-- Operational notes:
--   1. The TRUNCATE below fires a Debezium event with op="t"; the CDC service
--      calls RedisSearchUpdater.clearIndex() which now BLOCKS the Kafka consumer
--      thread until all autocomplete:* keys are deleted from Redis.  Subsequent
--      INSERT events then rebuild the index so there is no ZSet entry race.
--   2. Autocomplete responses will be empty from the moment of TRUNCATE until
--      CDC has processed all re-inserted rows.  Plan accordingly for large tables.
--   3. The Kafka Streams state store is versioned (search-counts-v2) to avoid
--      stale trim-inconsistent state being carried across this migration.
--
-- SQL lower() depends on DB collation and can diverge from Java Locale.ROOT for
-- non-ASCII text, especially in locales like Turkish. As best-effort for consistency:
--   1. Trim all queries (Java String.trim() compat: [\u0000-\u0020])
--   2. Apply lower() to normalize case, accepting a small locale-driven drift
--      for rare non-ASCII cases vs. Java Locale.ROOT
--   3. Group/aggregate by lowercase query to merge split counters

CREATE TEMP TABLE tmp_search_stats_normalized AS
WITH normalized AS (
    SELECT
        -- Java String.trim() removes leading/trailing chars in [\u0000-\u0020].
        regexp_replace(query, E'^[\\000-\\040]+|[\\000-\\040]+$', '', 'g') AS trimmed_query,
        frequency,
        updated_at
    FROM search_stats
    WHERE query IS NOT NULL
)
SELECT
    lower(trimmed_query) AS query,
    SUM(frequency) AS frequency,
    MAX(updated_at) AS updated_at
FROM normalized
WHERE trimmed_query <> ''
GROUP BY lower(trimmed_query);

TRUNCATE TABLE search_stats;

INSERT INTO search_stats (query, frequency, updated_at)
SELECT query, frequency, updated_at
FROM tmp_search_stats_normalized;

DROP TABLE tmp_search_stats_normalized;
