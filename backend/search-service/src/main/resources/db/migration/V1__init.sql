-- Создаём таблицу для агрегированных частот поисковых запросов
CREATE TABLE IF NOT EXISTS search_stats (
    query TEXT PRIMARY KEY,
    frequency BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Индекс для ускорения сортировки по частоте
CREATE INDEX IF NOT EXISTS idx_search_stats_frequency
    ON search_stats (frequency DESC);
