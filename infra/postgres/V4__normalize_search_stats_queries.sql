-- Normalize historical keys to avoid split counters after trim-based aggregation.
CREATE TEMP TABLE tmp_search_stats_normalized AS
SELECT
    lower(btrim(query)) AS query,
    SUM(frequency) AS frequency,
    MAX(updated_at) AS updated_at
FROM search_stats
WHERE query IS NOT NULL
  AND lower(btrim(query)) <> ''
GROUP BY lower(btrim(query));

TRUNCATE TABLE search_stats;

INSERT INTO search_stats (query, frequency, updated_at)
SELECT query, frequency, updated_at
FROM tmp_search_stats_normalized;

DROP TABLE tmp_search_stats_normalized;
