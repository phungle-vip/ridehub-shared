package com.ridehub.common.redis.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Helper to manage idempotency tokens and prevent duplicate execution of requests or events.
 */
public class RedisIdempotencyManager {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyManager.class);
    private static final String DEFAULT_PREFIX = "idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final String prefix;

    public RedisIdempotencyManager(StringRedisTemplate redisTemplate) {
        this(redisTemplate, DEFAULT_PREFIX);
    }

    public RedisIdempotencyManager(StringRedisTemplate redisTemplate, String prefix) {
        this.redisTemplate = redisTemplate;
        this.prefix = prefix != null ? prefix : DEFAULT_PREFIX;
    }

    /**
     * Attempts to acquire an idempotency lock for the given key.
     * Returns true if this is the first execution, false if it is a duplicate.
     *
     * @param key unique identifier (e.g. requestId, orderId, eventId)
     * @param ttl time to live for the idempotency record
     * @return true if acquired (first time), false if already exists
     */
    public boolean tryAcquire(String key, Duration ttl) {
        String fullKey = prefix + key;
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(fullKey, "PROCESSING", ttl);
            boolean acquired = Boolean.TRUE.equals(success);
            if (!acquired) {
                log.warn("Duplicate request or event detected for idempotency key: {}", fullKey);
            }
            return acquired;
        } catch (Exception e) {
            log.error("Failed to verify idempotency in Redis for key {}: {}", fullKey, e.getMessage());
            // Fall back to allowing execution in case of Redis degradation
            return true;
        }
    }

    /**
     * Marks the idempotency key as successfully COMPLETED with a retained TTL.
     */
    public void markCompleted(String key, Duration ttl) {
        String fullKey = prefix + key;
        try {
            redisTemplate.opsForValue().set(fullKey, "COMPLETED", ttl);
        } catch (Exception e) {
            log.error("Failed to mark idempotency key as completed {}: {}", fullKey, e.getMessage());
        }
    }

    /**
     * Releases or deletes an idempotency key (e.g. on business failure where retry is permitted).
     */
    public void release(String key) {
        String fullKey = prefix + key;
        try {
            redisTemplate.delete(fullKey);
            log.debug("Released idempotency key: {}", fullKey);
        } catch (Exception e) {
            log.error("Failed to release idempotency key {}: {}", fullKey, e.getMessage());
        }
    }

    /**
     * Check if a key has already been processed.
     */
    public boolean isProcessed(String key) {
        String fullKey = prefix + key;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(fullKey));
        } catch (Exception e) {
            log.error("Failed to check idempotency key {}: {}", fullKey, e.getMessage());
            return false;
        }
    }
}
