-- Лог всех поисковых запросов
CREATE TABLE IF NOT EXISTS search_log (
    id BIGSERIAL PRIMARY KEY,
    query TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Индекс по времени (для аналитики)
CREATE INDEX IF NOT EXISTS idx_search_log_created_at
    ON search_log (created_at DESC);