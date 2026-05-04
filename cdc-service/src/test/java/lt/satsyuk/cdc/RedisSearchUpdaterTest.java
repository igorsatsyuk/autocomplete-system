package lt.satsyuk.cdc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisSearchUpdaterTest {

    @Mock
    private ReactiveStringRedisTemplate redis;

    @Mock
    private ReactiveZSetOperations<String, String> zSetOperations;

    private RedisSearchUpdater updater;

    @BeforeEach
    void setUp() {
        updater = new RedisSearchUpdater(redis, "autocomplete:");
    }

    @Test
    void normalizesQueryBeforeWritingRedisPrefixes() {
        when(redis.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.add(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyDouble()))
                .thenReturn(Mono.just(Boolean.TRUE));

        updater.updateQueryScore("  JaVa  ", 5L);

        verify(zSetOperations, atLeastOnce()).add(ArgumentMatchers.anyString(), ArgumentMatchers.eq("java"), ArgumentMatchers.eq(5.0));
    }

    @Test
    void ignoresBlankQueryAfterTrim() {
        updater.updateQueryScore("   ", 5L);

        verify(redis, never()).opsForZSet();
    }

    @Test
    void swallowsRedisErrorsWithoutThrowing() {
        when(redis.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.add(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyDouble()))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));

        assertThatCode(() -> updater.updateQueryScore("java", 3L)).doesNotThrowAnyException();
    }
}

