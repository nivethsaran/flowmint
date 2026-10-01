package com.flowmint.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AuthRateLimiter {
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK = Duration.ofMinutes(5);
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        Attempt attempt = attempts.get(key);
        return attempt != null && attempt.blockedUntil().isAfter(Instant.now());
    }

    public void recordFailure(String key) {
        attempts.compute(key, (ignored, current) -> {
            Instant now = Instant.now();
            if (current == null || current.startedAt().plus(WINDOW).isBefore(now)) return new Attempt(now, new AtomicInteger(1), Instant.MIN);
            int failures = current.failures().incrementAndGet();
            return failures >= MAX_FAILURES ? new Attempt(current.startedAt(), current.failures(), now.plus(BLOCK)) : current;
        });
    }

    public void clear(String key) {
        attempts.remove(key);
    }

    private record Attempt(Instant startedAt, AtomicInteger failures, Instant blockedUntil) {}
}
