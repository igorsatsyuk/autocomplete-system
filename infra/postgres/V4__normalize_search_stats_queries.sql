-- Normalize historical keys to avoid split counters after trim-based aggregation.
--
-- Operational notes:
--   1. The TRUNCATE below fires a Debezium event with op="t"; the CDC service
--      calls RedisSearchUpdater.clearIndex() which now BLOCKS the Kafka consumer
--      thread until all keys matching the configured autocomplete Redis prefix
--      are deleted. Subsequent
--      INSERT events then rebuild the index so there is no ZSet entry race.
--   2. Autocomplete responses will be empty from the moment of TRUNCATE until
--      CDC has processed all re-inserted rows.  Plan accordingly for large tables.
--   3. The default Kafka Streams state store is versioned (search-counts-v2) to
--      avoid stale trim-inconsistent state being carried across this migration.
--      If SEARCH_STREAMS_STATE_STORE/search.streams.state-store is overridden,
--      use a fresh store name during rollout.
--      If SEARCH_STREAMS_APPLICATION_ID/search.streams.application-id is overridden,
--      use a fresh application id during rollout as well.
--
-- Normalize historical rows using the same high-level contract as runtime:
-- trim first, then lowercase, then drop effectively blank keys.
--   1. Trim all queries (Java String.trim() compat: [\u0000-\u0020])
--   2. Lowercase with PostgreSQL lower(); this keeps the migration portable
--      across installations where ICU collations are unavailable
--   3. Drop rows that are effectively blank under Java isBlank() semantics
--      (including Unicode space separators like U+2003 EM SPACE)

-- Ensure the collation name exists across environments:
-- prefer ICU root collation; if ICU is unavailable, create a libc C fallback
-- with the same name so the migration remains executable.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_collation WHERE collname = 'und-x-icu') THEN
        BEGIN
            EXECUTE 'CREATE COLLATION "und-x-icu" (provider = icu, locale = ''und'', deterministic = false)';
        EXCEPTION
            WHEN undefined_object OR feature_not_supported THEN
                EXECUTE 'CREATE COLLATION "und-x-icu" (provider = libc, locale = ''C'', deterministic = true)';
        END;
    END IF;
END$$;

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
        lower(trimmed_query) AS query,
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
  AND regexp_replace(
      query,
      '['
      || chr(9) || chr(10) || chr(11) || chr(12) || chr(13)
      || chr(28) || chr(29) || chr(30) || chr(31)
      || chr(32)
      || chr(133) || chr(5760)
      || chr(8192) || chr(8193) || chr(8194) || chr(8195) || chr(8196)
      || chr(8197) || chr(8198) || chr(8200) || chr(8201)
      || chr(8202) || chr(8232) || chr(8233) || chr(8287)
      || chr(12288)
      || ']',
      '',
      'g'
  ) <> ''
GROUP BY query;

TRUNCATE TABLE search_stats;

INSERT INTO search_stats (query, frequency, updated_at)
SELECT query, frequency, updated_at
FROM tmp_search_stats_normalized;

DROP TABLE tmp_search_stats_normalized;
