package lt.satsyuk.cdc;

import lt.satsyuk.common.util.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;


import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class RedisSearchUpdater {

    private static final Logger log = LoggerFactory.getLogger(RedisSearchUpdater.class);
    private static final Duration CLEAR_INDEX_TIMEOUT = Duration.ofSeconds(30);

    private final ReactiveStringRedisTemplate redis;
    private final String redisPrefix;
    private final ReentrantLock clearLock = new ReentrantLock();
    private final Condition clearCondition = clearLock.newCondition();
    private int inFlightUpdates;
    private int pendingClears;
    private boolean clearInProgress;

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

        String normalized = normalizeQuery(query);
        if (normalized.isBlank()) {
            return;
        }
        log.debug("Updating Redis index for query='{}', count={}", normalized, count);
        beginUpdate();
        try {
            Flux.range(1, normalized.length())
                    .map(i -> normalized.substring(0, i))
                    .flatMap(prefix -> {
                        String key = redisPrefix + prefix;
                        return redis.opsForZSet()
                                .add(key, normalized, count)
                                .then();
                    })
                    .then()
                    .doOnSuccess(ignored -> log.debug("Redis index updated for '{}'", normalized))
                    .doOnError(ex -> log.error("Failed to update Redis index", ex))
                    .doFinally(signalType -> finishUpdate())
                    .subscribe(
                            ignored -> {
                            },
                            ignored -> {
                            }
                    );
        } catch (RuntimeException ex) {
            finishUpdate();
            log.error("Failed to schedule Redis index update", ex);
        }
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
        beginClear();
        try {
            redis.delete(redis.scan(ScanOptions.scanOptions().match(pattern).count(100).build()))
                    .doOnSuccess(count -> log.debug("Redis index cleared {} key(s) for pattern '{}'", count, pattern))
                    .block(CLEAR_INDEX_TIMEOUT);
        } finally {
            finishClear();
        }
    }

    private void beginUpdate() {
        clearLock.lock();
        try {
            while (clearInProgress || pendingClears > 0) {
                clearCondition.awaitUninterruptibly();
            }
            inFlightUpdates++;
        } finally {
            clearLock.unlock();
        }
    }

    private void finishUpdate() {
        clearLock.lock();
        try {
            inFlightUpdates--;
            if (inFlightUpdates == 0) {
                clearCondition.signalAll();
            }
        } finally {
            clearLock.unlock();
        }
    }

    private void beginClear() {
        clearLock.lock();
        try {
            pendingClears++;
            while (clearInProgress || inFlightUpdates > 0) {
                clearCondition.awaitUninterruptibly();
            }
            pendingClears--;
            clearInProgress = true;
        } finally {
            clearLock.unlock();
        }
    }

    private void finishClear() {
        clearLock.lock();
        try {
            clearInProgress = false;
            clearCondition.signalAll();
        } finally {
            clearLock.unlock();
        }
    }

    private static String normalizeQuery(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}

