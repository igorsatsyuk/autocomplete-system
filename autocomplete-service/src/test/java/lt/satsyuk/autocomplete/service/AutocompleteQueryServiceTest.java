package lt.satsyuk.autocomplete.service;

import lt.satsyuk.autocomplete.model.AutocompleteEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
        when(zSetOperations.reverseRange(eq("autocomplete:ja"), ArgumentMatchers.<Range<Long>>any()))
                .thenReturn(Flux.just("java", "javascript"));

        StepVerifier.create(service.suggest("  Ja  ", 2))
                .expectNext(new AutocompleteEntry("java", 0.0))
                .expectNext(new AutocompleteEntry("javascript", 0.0))
                .verifyComplete();

        verify(zSetOperations).reverseRange(eq("autocomplete:ja"), ArgumentMatchers.<Range<Long>>any());
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

