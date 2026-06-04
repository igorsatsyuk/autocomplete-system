-- Индекс для LIKE 'prefix%' (используется Debezium + RedisSearch updater)
CREATE INDEX IF NOT EXISTS idx_search_stats_query_prefix
    ON search_stats (query text_pattern_ops);