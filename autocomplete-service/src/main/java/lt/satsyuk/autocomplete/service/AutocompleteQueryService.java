package lt.satsyuk.autocomplete.service;

import lt.satsyuk.common.util.RedisKeys;
import lt.satsyuk.autocomplete.model.AutocompleteEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Locale;

@Service
public class AutocompleteQueryService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final String redisPrefix;

    public AutocompleteQueryService(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${autocomplete.redis-prefix:" + RedisKeys.AUTOCOMPLETE_PREFIX + "}") String redisPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.redisPrefix = redisPrefix;
    }

    public Flux<AutocompleteEntry> suggest(String prefix, int limit) {
        if (prefix == null) {
            return Flux.empty();
        }

        String normalizedPrefix = prefix.trim().toLowerCase(Locale.ROOT);
        if (normalizedPrefix.isBlank()) {
            return Flux.empty();
        }

        if (limit <= 0) {
            return Flux.empty();
        }

        Range<Long> range = Range.closed(0L, (long) limit - 1);
        return redisTemplate.opsForZSet()
                .reverseRange(redisPrefix + normalizedPrefix, range)
                .map(q -> new AutocompleteEntry(q, 0.0));
    }
}