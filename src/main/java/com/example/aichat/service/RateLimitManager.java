package com.example.aichat.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitManager {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public Bucket getBucket(String keyId, int ratePerMinute) {
        return buckets.computeIfAbsent(keyId, k -> createBucket(ratePerMinute));
    }

    private Bucket createBucket(int ratePerMinute) {
        Bandwidth limit = Bandwidth.builder()
            .capacity(ratePerMinute)
            .refillGreedy(ratePerMinute, Duration.ofMinutes(1))
            .build();
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }

    public boolean tryConsume(String keyId, int ratePerMinute) {
        return getBucket(keyId, ratePerMinute).tryConsume(1);
    }
}
