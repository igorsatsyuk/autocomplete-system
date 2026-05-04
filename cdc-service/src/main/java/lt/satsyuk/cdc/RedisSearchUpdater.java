package lt.satsyuk.cdc;

import lt.satsyuk.common.util.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


import java.util.Locale;

@Service
public class RedisSearchUpdater {

    private static final Logger log = LoggerFactory.getLogger(RedisSearchUpdater.class);

    private final ReactiveStringRedisTemplate redis;
    private final String redisPrefix;

    public RedisSearchUpdater(
            ReactiveStringRedisTemplate redis,
            @Value("${autocomplete.redis-prefix:" + RedisKeys.AUTOCOMPLETE_PREFIX + "}") String redisPrefix
    ) {
        this.redis = redis;
        this.redisPrefix = redisPrefix;
    }

    public void updateQueryScore(String query, long count) {
        if (query == null || query.isBlank()) {
            return;
        }

        String normalized = query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return;
        }
        log.debug("Updating Redis index for query='{}', count={}", normalized, count);

        Flux.range(1, normalized.length())
                .map(i -> normalized.substring(0, i))
                .flatMap(prefix -> {
                    String key = redisPrefix + prefix;
                    return redis.opsForZSet()
                            .add(key, normalized, count)
                            .then();
                })
                .then()
                .subscribe(
                        ignored -> {
                        },
                        ex -> log.error("Failed to update Redis index", ex),
                        () -> log.debug("Redis index updated for '{}'", normalized)
                );
    }

    /**
     * Deletes all Redis autocomplete keys matching the configured prefix.
     * <p>
     * Uses SCAN to avoid blocking Redis on large key spaces, and blocks
     * the calling thread until deletion is complete.  This intentional
     * synchronous block prevents a race where incoming Debezium INSERT
     * events (arriving immediately after a TRUNCATE event) would call
     * {@link #updateQueryScore} before the stale keys have been removed.
     * </p>
     */
    public void clearIndex() {
        String pattern = redisPrefix + "*";
        redis.delete(redis.scan(ScanOptions.scanOptions().match(pattern).count(100).build()))
                .onErrorResume(ex -> {
                    log.error("Failed to clear Redis index", ex);
                    return Mono.just(0L);
                })
                .doOnSuccess(count -> log.debug("Redis index cleared {} key(s) for pattern '{}'", count, pattern))
                .block();
    }
}