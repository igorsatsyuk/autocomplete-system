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
-- Lowercasing is applied to all trimmed keys so historical rows do not remain
-- split by case after runtime normalization.

CREATE TEMP TABLE tmp_search_stats_normalized AS
WITH normalized AS (
    SELECT
        lower(btrim(query, E' \t\n\r\f')) AS query,
        frequency,
        updated_at
    FROM search_stats
    WHERE query IS NOT NULL
)
SELECT
    query,
    SUM(frequency) AS frequency,
    MAX(updated_at) AS updated_at
FROM normalized
WHERE query <> ''
GROUP BY query;

TRUNCATE TABLE search_stats;

INSERT INTO search_stats (query, frequency, updated_at)
SELECT query, frequency, updated_at
FROM tmp_search_stats_normalized;

DROP TABLE tmp_search_stats_normalized;
