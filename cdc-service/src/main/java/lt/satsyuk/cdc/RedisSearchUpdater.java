package lt.satsyuk.cdc;

import lt.satsyuk.common.util.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

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

        String normalized = query.toLowerCase();
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
                        null,
                        ex -> log.error("Failed to update Redis index", ex),
                        () -> log.debug("Redis index updated for '{}'", normalized)
                );
    }
}