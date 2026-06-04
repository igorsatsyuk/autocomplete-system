package lt.satsyuk.autocomplete.service;

import lt.satsyuk.autocomplete.model.AutocompleteEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutocompleteQueryServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveZSetOperations<String, String> zSetOperations;

    private AutocompleteQueryService service;

    @BeforeEach
    void setUp() {
        service = new AutocompleteQueryService(redisTemplate, "autocomplete:");
    }

    @Test
    void returnsSuggestionsFromConfiguredRedisPrefixWithTrimmedLowercaseKey() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRangeWithScores(eq("autocomplete:ja"), ArgumentMatchers.<Range<Long>>any()))
                .thenReturn(Flux.just(
                        new DefaultTypedTuple<>("java", 7.0),
                        new DefaultTypedTuple<>("javascript", 3.0)
                ));

        StepVerifier.create(service.suggest("  Ja  ", 2))
                .expectNext(new AutocompleteEntry("java", 7.0))
                .expectNext(new AutocompleteEntry("javascript", 3.0))
                .verifyComplete();

        verify(zSetOperations).reverseRangeWithScores(eq("autocomplete:ja"), ArgumentMatchers.<Range<Long>>any());
    }

    @Test
    void returnsEmptyFluxForNullPrefix() {
        StepVerifier.create(service.suggest(null, 5))
                .verifyComplete();

        verify(redisTemplate, never()).opsForZSet();
    }

    @Test
    void returnsEmptyFluxForBlankPrefix() {
        StepVerifier.create(service.suggest("   ", 5))
                .verifyComplete();

        verify(redisTemplate, never()).opsForZSet();
    }

    @Test
    void returnsEmptyFluxForNonPositiveLimit() {
        StepVerifier.create(service.suggest("ja", 0))
                .verifyComplete();

        verify(redisTemplate, never()).opsForZSet();
    }
}

