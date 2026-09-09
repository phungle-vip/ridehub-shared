package com.ridehub.common.redis.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Annotation for distributed locking using Redisson.
 * Supports Spring Expression Language (SpEL) for dynamic keys based on method arguments.
 *
 * Example:
 * {@code @DistributedLock(key = "#seatId", waitTime = 5, leaseTime = 30)}
 * public void lockSeat(Long seatId) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * Lock key or SpEL expression evaluating against method parameters (e.g. "#id" or "'user:' + #user.id")
     */
    String key();

    /**
     * Key prefix for namespacing in Redis. Default is "lock:".
     */
    String prefix() default "lock:";

    /**
     * Max wait time to acquire the lock before throwing LockAcquisitionException. Default is 5.
     */
    long waitTime() default 5;

    /**
     * Lease expiration time after which lock is automatically released if held. Default is 30.
     */
    long leaseTime() default 30;

    /**
     * Time unit for waitTime and leaseTime. Default is SECONDS.
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
