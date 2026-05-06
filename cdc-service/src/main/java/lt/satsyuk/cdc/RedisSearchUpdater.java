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

    private final ReactiveStringRedisTemplate redis;
    private final String redisPrefix;
    private final Duration clearIndexTimeout;
    private final ReentrantLock clearLock = new ReentrantLock();
    private final Condition clearCondition = clearLock.newCondition();
    private int inFlightUpdates;
    private int pendingClears;
    private boolean clearInProgress;

    public RedisSearchUpdater(
            ReactiveStringRedisTemplate redis,
            @Value("${autocomplete.redis-prefix:" + RedisKeys.AUTOCOMPLETE_PREFIX + "}") String redisPrefix,
            @Value("${autocomplete.clear-index-timeout:PT5M}") Duration clearIndexTimeout
    ) {
        String normalizedPrefix = redisPrefix == null ? "" : redisPrefix.trim();
        if (normalizedPrefix.isEmpty()) {
            throw new IllegalArgumentException("autocomplete.redis-prefix must not be blank");
        }
        this.redis = redis;
        this.redisPrefix = normalizedPrefix;
        this.clearIndexTimeout = clearIndexTimeout;
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
        if (!beginUpdate()) {
            log.warn("Interrupted while waiting to update Redis index for query='{}'", normalized);
            return;
        }
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
        long startedAtNanos = System.nanoTime();
        String pattern = redisPrefix + "*";
        beginClear();
        try {
            long elapsedNanos = System.nanoTime() - startedAtNanos;
            long remainingNanos = clearIndexTimeout.toNanos() - elapsedNanos;
            if (remainingNanos <= 0L) {
                throw new IllegalStateException("Timed out before Redis clear phase started");
            }

            redis.delete(redis.scan(ScanOptions.scanOptions().match(pattern).count(100).build()))
                    .doOnSuccess(count -> log.debug("Redis index cleared {} key(s) for pattern '{}'", count, pattern))
                    .doOnError(ex -> log.error("Failed to clear Redis index for pattern '{}' with timeout {}", pattern, clearIndexTimeout, ex))
                    .block(Duration.ofNanos(remainingNanos));
        } finally {
            finishClear();
        }
    }

    private boolean beginUpdate() {
        clearLock.lock();
        try {
            while (clearInProgress || pendingClears > 0) {
                try {
                    clearCondition.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            inFlightUpdates++;
            return true;
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
            long remainingNanos = clearIndexTimeout.toNanos();
            while (clearInProgress || inFlightUpdates > 0) {
                if (remainingNanos <= 0L) {
                    pendingClears--;
                    clearCondition.signalAll();
                    throw new IllegalStateException("Timed out waiting for in-flight updates before clearing Redis index");
                }
                try {
                    remainingNanos = clearCondition.awaitNanos(remainingNanos);
                } catch (InterruptedException ex) {
                    pendingClears--;
                    clearCondition.signalAll();
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting to clear Redis index", ex);
                }
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

