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
-- Lowercasing during migration is applied only to ASCII keys.
-- Non-ASCII keys are kept as-is to avoid locale/collation-dependent rewrites
-- that may diverge from Java String.toLowerCase(Locale.ROOT).

CREATE TEMP TABLE tmp_search_stats_normalized AS
WITH trimmed AS (
    SELECT
        btrim(query, E' \t\n\r\f') AS trimmed_query,
        frequency,
        updated_at
    FROM search_stats
    WHERE query IS NOT NULL
), normalized AS (
    SELECT
        CASE
            WHEN octet_length(trimmed_query) = char_length(trimmed_query)
                THEN lower(trimmed_query)
            ELSE trimmed_query
        END AS query,
        frequency,
        updated_at
    FROM trimmed
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
