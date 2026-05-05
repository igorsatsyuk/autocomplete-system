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
-- SQL lower() depends on DB collation and can diverge from Java Locale.ROOT
-- for non-ASCII text (for example Turkish locale rules). To avoid rewriting
-- historical rows into keys that runtime services will never use:
--   1. Trim all queries (Java String.trim() compat: [\u0000-\u0020])
--   2. Apply lower() only to ASCII-safe values
--   3. Keep non-ASCII values trim-normalized only (no locale-sensitive rewrite)

CREATE TEMP TABLE tmp_search_stats_normalized AS
WITH normalized AS (
    SELECT
        -- Java String.trim() removes leading/trailing chars in [\u0000-\u0020].
        regexp_replace(query, E'^[\\000-\\040]+|[\\000-\\040]+$', '', 'g') AS trimmed_query,
        frequency,
        updated_at
    FROM search_stats
    WHERE query IS NOT NULL
), collapsed AS (
    SELECT
        CASE WHEN trimmed_query ~ '^[\\000-\\177]*$' THEN lower(trimmed_query) ELSE trimmed_query END AS query,
        frequency,
        updated_at
    FROM normalized
)
SELECT
    query,
    SUM(frequency) AS frequency,
    MAX(updated_at) AS updated_at
FROM collapsed
WHERE query <> ''
GROUP BY query;

TRUNCATE TABLE search_stats;

INSERT INTO search_stats (query, frequency, updated_at)
SELECT query, frequency, updated_at
FROM tmp_search_stats_normalized;

DROP TABLE tmp_search_stats_normalized;
