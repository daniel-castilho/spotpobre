package com.spotpobre.backend.infrastructure.security.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisTokenBucketLimiterIT extends AbstractRedisIntegrationTest {

    private final RedisTokenBucketLimiter limiter = new RedisTokenBucketLimiter(redisTemplate);

    @Test
    void tryAcquire_allowsUpToCapacityThenBlocks() {
        String key = "rl:test:cap-" + java.util.UUID.randomUUID();

        assertTrue(limiter.tryAcquire(key, 3, Duration.ofHours(1), 1).allowed());
        assertTrue(limiter.tryAcquire(key, 3, Duration.ofHours(1), 1).allowed());
        assertTrue(limiter.tryAcquire(key, 3, Duration.ofHours(1), 1).allowed());

        TokenBucketResult blocked = limiter.tryAcquire(key, 3, Duration.ofHours(1), 1);
        assertFalse(blocked.allowed());
        assertEquals(0, blocked.remaining());
        assertTrue(blocked.resetSeconds() >= 1);
    }

    @Test
    void tryAcquire_refillsAfterTheWindowWithoutLongSleeps() throws Exception {
        String key = "rl:test:refill-" + java.util.UUID.randomUUID();

        assertTrue(limiter.tryAcquire(key, 1, Duration.ofSeconds(2), 1).allowed());
        assertFalse(limiter.tryAcquire(key, 1, Duration.ofSeconds(2), 1).allowed());

        // Bounded polling (no long sleeps, spec section 8.5): refill window is 2s.
        long deadline = System.currentTimeMillis() + 4000;
        boolean refilled = false;
        while (System.currentTimeMillis() < deadline) {
            if (limiter.tryAcquire(key, 1, Duration.ofSeconds(2), 1).allowed()) {
                refilled = true;
                break;
            }
            Thread.sleep(200);
        }
        assertTrue(refilled, "bucket must refill after its window");
    }

    @Test
    void tryAcquire_racingCallers_neverOverAdmitCapacity() throws Exception {
        String key = "rl:test:race-" + java.util.UUID.randomUUID();
        final int capacity = 4;
        final int callers = 16;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        Set<String> allowedKeys = ConcurrentHashMap.newKeySet();

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < callers; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                if (limiter.tryAcquire(key, capacity, Duration.ofHours(1), 1).allowed()) {
                    allowedKeys.add(Thread.currentThread().getName());
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(capacity, allowedKeys.size(),
                "exactly capacity racers may win regardless of thread contention");
    }

    @Test
    void tryAcquire_distinctSubjects_areIsolatedBuckets() {
        String keyA = "rl:test:iso-a-" + java.util.UUID.randomUUID();
        String keyB = "rl:test:iso-b-" + java.util.UUID.randomUUID();

        assertTrue(limiter.tryAcquire(keyA, 1, Duration.ofHours(1), 1).allowed());
        assertFalse(limiter.tryAcquire(keyA, 1, Duration.ofHours(1), 1).allowed());

        // Exhausting A must not consume B's budget.
        assertTrue(limiter.tryAcquire(keyB, 1, Duration.ofHours(1), 1).allowed());
        assertFalse(limiter.tryAcquire(keyB, 1, Duration.ofHours(1), 1).allowed());
    }
}
