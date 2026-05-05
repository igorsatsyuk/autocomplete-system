package lt.satsyuk.cdc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import org.springframework.data.redis.core.ScanOptions;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

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

        verify(zSetOperations, atLeastOnce())
                .add(ArgumentMatchers.anyString(), ArgumentMatchers.eq("java"), ArgumentMatchers.eq(5.0));
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

    @Test
    void clearsAllAutocompleteKeysByPrefix() {
        when(redis.scan(ArgumentMatchers.any(ScanOptions.class)))
                .thenReturn(Flux.just("autocomplete:ja", "autocomplete:jav"));
        when(redis.delete(ArgumentMatchers.any(org.reactivestreams.Publisher.class)))
                .thenReturn(Mono.just(2L));

        updater.clearIndex();

        verify(redis).scan(ArgumentMatchers.any(ScanOptions.class));
        verify(redis).delete(ArgumentMatchers.any(org.reactivestreams.Publisher.class));
    }

    @Test
    void clearIndexPropagatesRedisErrors() {
        when(redis.scan(ArgumentMatchers.any(ScanOptions.class)))
                .thenReturn(Flux.just("autocomplete:ja"));
        when(redis.delete(ArgumentMatchers.any(org.reactivestreams.Publisher.class)))
                .thenReturn(Mono.error(new IllegalStateException("delete failed")));

        assertThatThrownBy(() -> updater.clearIndex())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delete failed");
    }

    @Test
    void clearIndexWaitsForInFlightUpdateAndBlocksNewUpdates() {
        CountDownLatch firstUpdateStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstUpdate = new CountDownLatch(1);
        CountDownLatch clearFinished = new CountDownLatch(1);
        CountDownLatch secondUpdateReachedRedis = new CountDownLatch(1);
        AtomicBoolean clearCompletedBeforeSecondUpdate = new AtomicBoolean(false);

        when(redis.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.add(ArgumentMatchers.anyString(), ArgumentMatchers.eq("java"), ArgumentMatchers.eq(1.0)))
                .thenAnswer(invocation -> Mono.fromRunnable(() -> {
                    firstUpdateStarted.countDown();
                    await().atMost(Duration.ofSeconds(2)).until(() -> releaseFirstUpdate.getCount() == 0);
                }).thenReturn(Boolean.TRUE));
        when(zSetOperations.add(ArgumentMatchers.anyString(), ArgumentMatchers.eq("blocked"), ArgumentMatchers.eq(2.0)))
                .thenAnswer(invocation -> Mono.fromRunnable(() -> {
                    clearCompletedBeforeSecondUpdate.set(clearFinished.getCount() == 0);
                    secondUpdateReachedRedis.countDown();
                }).thenReturn(Boolean.TRUE));

        when(redis.scan(ArgumentMatchers.any(ScanOptions.class)))
                .thenReturn(Flux.just("autocomplete:ja"));
        when(redis.delete(ArgumentMatchers.any(org.reactivestreams.Publisher.class)))
                .thenReturn(Mono.just(1L));

        Thread firstUpdateThread = new Thread(() -> updater.updateQueryScore("java", 1L));
        firstUpdateThread.start();
        await().atMost(Duration.ofSeconds(2)).until(() -> firstUpdateStarted.getCount() == 0);

        Thread clearThread = new Thread(() -> {
            updater.clearIndex();
            clearFinished.countDown();
        });
        clearThread.start();

        Thread blockedUpdateThread = new Thread(() -> updater.updateQueryScore("blocked", 2L));
        blockedUpdateThread.start();


        releaseFirstUpdate.countDown();

        await().atMost(Duration.ofSeconds(2)).until(() -> clearFinished.getCount() == 0);
        await().atMost(Duration.ofSeconds(2)).until(() -> secondUpdateReachedRedis.getCount() == 0);

        assertThat(clearCompletedBeforeSecondUpdate.get()).isTrue();
        verify(redis).delete(ArgumentMatchers.any(org.reactivestreams.Publisher.class));
        verify(zSetOperations, atLeastOnce())
                .add(ArgumentMatchers.anyString(), ArgumentMatchers.eq("blocked"), ArgumentMatchers.eq(2.0));
    }

    @Test
    void updatesLowerScoreWhenNewCountIsSmaller() {
        when(redis.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.add(ArgumentMatchers.anyString(), ArgumentMatchers.eq("java"), ArgumentMatchers.eq(5.0)))
                .thenReturn(Mono.just(Boolean.TRUE));

        updater.updateQueryScore("java", 5L);

        verify(zSetOperations, atLeastOnce())
                .add(ArgumentMatchers.anyString(), ArgumentMatchers.eq("java"), ArgumentMatchers.eq(5.0));
    }

    @Test
    void serializesOverlappingClearsAndKeepsUpdatesBlockedUntilBothFinish() {
        CountDownLatch firstDeleteStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstDelete = new CountDownLatch(1);
        CountDownLatch secondDeleteStarted = new CountDownLatch(1);
        CountDownLatch releaseSecondDelete = new CountDownLatch(1);
        AtomicInteger deleteInvocations = new AtomicInteger(0);
        AtomicBoolean updateReachedRedisAfterSecondClear = new AtomicBoolean(false);
        CountDownLatch updateReachedRedis = new CountDownLatch(1);

        when(redis.scan(ArgumentMatchers.any(ScanOptions.class)))
                .thenReturn(Flux.just("autocomplete:ja"));
        when(redis.delete(ArgumentMatchers.any(org.reactivestreams.Publisher.class)))
                .thenAnswer(invocation -> Mono.fromRunnable(() -> {
                    int call = deleteInvocations.incrementAndGet();
                    if (call == 1) {
                        firstDeleteStarted.countDown();
                        await().atMost(Duration.ofSeconds(2)).until(() -> releaseFirstDelete.getCount() == 0);
                    } else {
                        secondDeleteStarted.countDown();
                        await().atMost(Duration.ofSeconds(2)).until(() -> releaseSecondDelete.getCount() == 0);
                    }
                }).thenReturn(1L));

        when(redis.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.add(ArgumentMatchers.anyString(), ArgumentMatchers.eq("late"), ArgumentMatchers.eq(1.0)))
                .thenAnswer(invocation -> Mono.fromRunnable(() -> {
                    updateReachedRedisAfterSecondClear.set(secondDeleteStarted.getCount() == 0 && releaseSecondDelete.getCount() == 0);
                    updateReachedRedis.countDown();
                }).thenReturn(Boolean.TRUE));

        Thread clearOne = new Thread(() -> updater.clearIndex());
        clearOne.start();
        await().atMost(Duration.ofSeconds(2)).until(() -> firstDeleteStarted.getCount() == 0);

        Thread clearTwo = new Thread(() -> updater.clearIndex());
        clearTwo.start();

        Thread blockedUpdate = new Thread(() -> updater.updateQueryScore("late", 1L));
        blockedUpdate.start();

        releaseFirstDelete.countDown();
        await().atMost(Duration.ofSeconds(2)).until(() -> secondDeleteStarted.getCount() == 0);
        releaseSecondDelete.countDown();

        await().atMost(Duration.ofSeconds(2)).until(() -> updateReachedRedis.getCount() == 0);

        assertThat(updateReachedRedisAfterSecondClear.get()).isTrue();
        verify(redis, times(2)).delete(ArgumentMatchers.any(org.reactivestreams.Publisher.class));
    }
}

